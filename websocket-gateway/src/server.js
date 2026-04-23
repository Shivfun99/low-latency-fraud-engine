const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const { Kafka } = require('kafkajs');
const { v4: uuidv4 } = require('uuid');
const PORT = process.env.PORT || 3001;
const KAFKA_BROKER = process.env.KAFKA_BROKER || 'localhost:9092';
const TOPICS = {
  ALERTS: 'fraud.alerts',
  DECISIONS: 'fraud.decisions',
};
const app = express();
app.use(cors());
app.use(express.json());
const server = http.createServer(app);
const wss = new WebSocket.Server({ server, path: '/ws' });
const clients = new Map(); 
let stats = {
  totalAlerts: 0,
  totalDecisions: 0,
  blockedCount: 0,
  reviewCount: 0,
  allowedCount: 0,
  connectedClients: 0,
  startTime: Date.now(),
  recentAlerts: [],    
  recentDecisions: [], 
};
wss.on('connection', (ws, req) => {
  const clientId = uuidv4();
  clients.set(clientId, {
    ws,
    subscriptions: ['alerts', 'decisions', 'stats'],
    connectedAt: Date.now(),
  });
  stats.connectedClients = clients.size;
  console.log(`[WS] Client connected: ${clientId} (total: ${clients.size})`);
  ws.send(JSON.stringify({
    type: 'CONNECTED',
    clientId,
    stats: getPublicStats(),
    recentAlerts: stats.recentAlerts.slice(-20),
    recentDecisions: stats.recentDecisions.slice(-20),
  }));
  ws.on('message', (data) => {
    try {
      const msg = JSON.parse(data);
      handleClientMessage(clientId, msg);
    } catch (e) {
      console.error(`[WS] Invalid message from ${clientId}:`, e.message);
    }
  });
  ws.on('close', () => {
    clients.delete(clientId);
    stats.connectedClients = clients.size;
    console.log(`[WS] Client disconnected: ${clientId} (total: ${clients.size})`);
  });
  ws.on('error', (err) => {
    console.error(`[WS] Error for ${clientId}:`, err.message);
  });
});
function handleClientMessage(clientId, msg) {
  const client = clients.get(clientId);
  if (!client) return;
  switch (msg.type) {
    case 'SUBSCRIBE':
      if (msg.topics) client.subscriptions = msg.topics;
      break;
    case 'PING':
      client.ws.send(JSON.stringify({ type: 'PONG', timestamp: Date.now() }));
      break;
    case 'GET_STATS':
      client.ws.send(JSON.stringify({ type: 'STATS', stats: getPublicStats() }));
      break;
  }
}
function broadcast(type, data) {
  const message = JSON.stringify({ type, data, timestamp: Date.now() });
  for (const [id, client] of clients) {
    if (client.ws.readyState === WebSocket.OPEN) {
      const topicType = type === 'FRAUD_ALERT' ? 'alerts' : 'decisions';
      if (client.subscriptions.includes(topicType) || client.subscriptions.includes('all')) {
        client.ws.send(message);
      }
    }
  }
}
function broadcastStats() {
  const message = JSON.stringify({ type: 'STATS_UPDATE', stats: getPublicStats() });
  for (const [id, client] of clients) {
    if (client.ws.readyState === WebSocket.OPEN && client.subscriptions.includes('stats')) {
      client.ws.send(message);
    }
  }
}
function getPublicStats() {
  return {
    totalAlerts: stats.totalAlerts,
    totalDecisions: stats.totalDecisions,
    blockedCount: stats.blockedCount,
    reviewCount: stats.reviewCount,
    allowedCount: stats.allowedCount,
    connectedClients: stats.connectedClients,
    uptimeSeconds: Math.floor((Date.now() - stats.startTime) / 1000),
  };
}
const kafka = new Kafka({
  clientId: 'fraud-ws-gateway',
  brokers: [KAFKA_BROKER],
  retry: {
    initialRetryTime: 1000,
    retries: 10,
  },
});
const consumer = kafka.consumer({ groupId: 'ws-gateway-group' });
async function startKafkaConsumer() {
  try {
    await consumer.connect();
    console.log('[Kafka] Consumer connected');
    await consumer.subscribe({ topics: [TOPICS.ALERTS, TOPICS.DECISIONS], fromBeginning: false });
    console.log(`[Kafka] Subscribed to: ${Object.values(TOPICS).join(', ')}`);
    await consumer.run({
      eachMessage: async ({ topic, partition, message }) => {
        try {
          const value = JSON.parse(message.value.toString());
          if (topic === TOPICS.ALERTS) {
            value.actionable = true;
            value.source = value.source || 'orchestrator';
            stats.totalAlerts++;
            stats.recentAlerts.push(value);
            if (stats.recentAlerts.length > 50) stats.recentAlerts.shift();
            console.log(`[Alert] txn=${value.transaction_id} decision=${value.decision} score=${value.risk_score}`);
            broadcast('FRAUD_ALERT', value);
          }
          if (topic === TOPICS.DECISIONS) {
            stats.totalDecisions++;
            const decision = value.decision;
            if (decision === 'BLOCK') stats.blockedCount++;
            else if (decision === 'REVIEW') stats.reviewCount++;
            else stats.allowedCount++;
            stats.recentDecisions.push(value);
            if (stats.recentDecisions.length > 100) stats.recentDecisions.shift();
            broadcast('FRAUD_DECISION', value);
          }
          if ((stats.totalDecisions + stats.totalAlerts) % 5 === 0) {
            broadcastStats();
          }
        } catch (e) {
          console.error(`[Kafka] Error processing message:`, e.message);
        }
      },
    });
  } catch (error) {
    console.error('[Kafka] Consumer error:', error.message);
    console.log('[Kafka] Retrying in 5 seconds...');
    setTimeout(startKafkaConsumer, 5000);
  }
}
app.get('/health', (req, res) => {
  res.json({
    status: 'UP',
    service: 'websocket-gateway',
    connectedClients: clients.size,
    timestamp: new Date().toISOString(),
  });
});
app.get('/api/stats', (req, res) => {
  res.json(getPublicStats());
});
app.get('/api/recent-alerts', (req, res) => {
  const limit = parseInt(req.query.limit) || 20;
  res.json(stats.recentAlerts.slice(-limit));
});
app.get('/api/recent-decisions', (req, res) => {
  const limit = parseInt(req.query.limit) || 20;
  res.json(stats.recentDecisions.slice(-limit));
});
app.post('/api/test-alert', (req, res) => {
  const testAlert = {
    alert_id: uuidv4(),
    transaction_id: uuidv4(),
    user_id: `user_${Math.floor(Math.random() * 1000)}`,
    amount: Math.floor(Math.random() * 100000) + 1000,
    risk_score: Math.random() * 0.5 + 0.5,
    decision: Math.random() > 0.5 ? 'BLOCK' : 'REVIEW',
    velocity_score: Math.random(),
    geo_anomaly_score: Math.random(),
    amount_anomaly_score: Math.random(),
    location: ['Mumbai', 'Delhi', 'Chennai', 'Bangalore', 'Kolkata'][Math.floor(Math.random() * 5)],
    severity: Math.random() > 0.7 ? 'CRITICAL' : Math.random() > 0.4 ? 'HIGH' : 'MEDIUM',
    status: 'OPEN',
    actionable: false,
    source: 'simulated',
    processing_time_ms: Math.floor(Math.random() * 15) + 12,
    timestamp: new Date().toISOString(),
    is_sandbox: true
  };
  stats.totalAlerts++;
  stats.recentAlerts.push(testAlert);
  if (stats.recentAlerts.length > 50) stats.recentAlerts.shift();
  broadcast('FRAUD_ALERT', testAlert);
  broadcastStats();
  res.json({ message: 'Test alert sent', alert: testAlert });
});
server.listen(PORT, () => {
  console.log('════════════════════════════════════════════');
  console.log('  Fraud Detection — WebSocket Gateway');
  console.log(`  HTTP:      http:
  console.log(`  WebSocket: ws:
  console.log('════════════════════════════════════════════');
  startKafkaConsumer();
});
setInterval(broadcastStats, 5000);
process.on('SIGTERM', async () => {
  console.log('[Server] Shutting down...');
  await consumer.disconnect();
  wss.close();
  server.close();
  process.exit(0);
});
