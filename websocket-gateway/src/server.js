const express = require('express');
const http = require('node:http');
const { randomUUID } = require('node:crypto');
const WebSocket = require('ws');
const cors = require('cors');
const { Kafka } = require('kafkajs');

const TOPICS = { ALERTS: 'fraud.alerts', DECISIONS: 'fraud.decisions' };

function createGateway({
  kafkaEnabled = true,
  broker = process.env.KAFKA_BROKER || 'localhost:9092',
} = {}) {
  const app = express();
  app.use(cors());
  app.use(express.json());
  const server = http.createServer(app);
  const wss = new WebSocket.Server({ server, path: '/ws' });
  const clients = new Map();
  const stats = {
    totalAlerts: 0,
    totalDecisions: 0,
    blockedCount: 0,
    reviewCount: 0,
    allowedCount: 0,
    startTime: Date.now(),
    recentAlerts: [],
    recentDecisions: [],
  };
  let kafkaConnected = false;
  let stopping = false;
  let retryTimer;
  let statsTimer;
  const consumer = kafkaEnabled
    ? new Kafka({
        clientId: 'fraud-ws-gateway',
        brokers: broker.split(','),
        retry: { initialRetryTime: 1000, retries: 5 },
      }).consumer({ groupId: process.env.KAFKA_GROUP_ID || 'ws-gateway-group' })
    : null;

  function getPublicStats() {
    return {
      totalAlerts: stats.totalAlerts,
      totalDecisions: stats.totalDecisions,
      blockedCount: stats.blockedCount,
      reviewCount: stats.reviewCount,
      allowedCount: stats.allowedCount,
      connectedClients: clients.size,
      uptimeSeconds: Math.floor((Date.now() - stats.startTime) / 1000),
      kafkaConnected,
      kafkaEnabled,
    };
  }

  function broadcast(message, topic) {
    const encoded = JSON.stringify(message);
    for (const [ws, subscriptions] of clients) {
      if (
        ws.readyState === WebSocket.OPEN &&
        (subscriptions.includes(topic) || subscriptions.includes('all'))
      ) {
        ws.send(encoded);
      }
    }
  }
  function broadcastStats() {
    broadcast({ type: 'STATS_UPDATE', stats: getPublicStats() }, 'stats');
  }

  function processEvent(topic, value) {
    if (!value || typeof value !== 'object') throw new Error('Expected an event object');
    if (topic === TOPICS.ALERTS) {
      if (!value.alert_id) throw new Error('Alert missing alert_id');
      // Analyst updates use the original alert ID and must preserve actionable=false.
      const alert = {
        ...value,
        actionable: value.actionable ?? true,
        source: value.source || 'orchestrator',
      };
      const existing = stats.recentAlerts.findIndex((item) => item.alert_id === alert.alert_id);
      if (existing === -1) stats.totalAlerts++;
      else stats.recentAlerts.splice(existing, 1);
      stats.recentAlerts.push(alert);
      stats.recentAlerts = stats.recentAlerts.slice(-50);
      broadcast({ type: 'FRAUD_ALERT', data: alert, timestamp: Date.now() }, 'alerts');
    } else if (topic === TOPICS.DECISIONS) {
      if (!value.transaction_id || !['ALLOW', 'REVIEW', 'BLOCK'].includes(value.decision)) {
        throw new Error('Decision missing transaction ID or valid decision');
      }
      if (stats.recentDecisions.some((item) => item.transaction_id === value.transaction_id))
        return;
      stats.totalDecisions++;
      stats[
        { BLOCK: 'blockedCount', REVIEW: 'reviewCount', ALLOW: 'allowedCount' }[value.decision]
      ]++;
      stats.recentDecisions.push(value);
      stats.recentDecisions = stats.recentDecisions.slice(-100);
      broadcast({ type: 'FRAUD_DECISION', data: value, timestamp: Date.now() }, 'decisions');
    }
    broadcastStats();
  }

  wss.on('connection', (ws) => {
    clients.set(ws, ['alerts', 'decisions', 'stats']);
    ws.send(
      JSON.stringify({
        type: 'CONNECTED',
        clientId: randomUUID(),
        stats: getPublicStats(),
        recentAlerts: stats.recentAlerts.slice(-20).reverse(),
        recentDecisions: stats.recentDecisions.slice(-20).reverse(),
      })
    );
    ws.on('message', (raw) => {
      try {
        const msg = JSON.parse(raw.toString());
        if (msg.type === 'SUBSCRIBE' && Array.isArray(msg.topics)) {
          clients.set(
            ws,
            msg.topics.filter((topic) => ['alerts', 'decisions', 'stats', 'all'].includes(topic))
          );
        } else if (msg.type === 'PING') {
          ws.send(JSON.stringify({ type: 'PONG', timestamp: Date.now() }));
        } else if (msg.type === 'GET_STATS') {
          ws.send(JSON.stringify({ type: 'STATS_UPDATE', stats: getPublicStats() }));
        }
      } catch {
        ws.send(JSON.stringify({ type: 'ERROR', message: 'Invalid message JSON' }));
      }
    });
    ws.on('close', () => clients.delete(ws));
    ws.on('error', (error) => console.error('[WS]', error.message));
  });

  async function startKafkaConsumer() {
    if (!consumer || stopping) return;
    try {
      await consumer.connect();
      await consumer.subscribe({ topics: Object.values(TOPICS), fromBeginning: false });
      await consumer.run({
        eachMessage: async ({ topic, message }) => {
          try {
            processEvent(topic, JSON.parse(message.value.toString()));
          } catch (error) {
            console.error('[Kafka] Invalid event:', error.message);
          }
        },
      });
    } catch (error) {
      kafkaConnected = false;
      console.error('[Kafka] Consumer error:', error.message);
      if (!stopping) retryTimer = setTimeout(startKafkaConsumer, 5000);
    }
  }
  if (consumer) {
    consumer.on(consumer.events.GROUP_JOIN, () => {
      kafkaConnected = true;
      broadcastStats();
    });
    consumer.on(consumer.events.DISCONNECT, () => {
      kafkaConnected = false;
    });
    consumer.on(consumer.events.CRASH, () => {
      kafkaConnected = false;
    });
  }

  app.get('/health', (_req, res) =>
    res.status(!kafkaEnabled || kafkaConnected ? 200 : 503).json({
      status: !kafkaEnabled || kafkaConnected ? 'UP' : 'DEGRADED',
      service: 'websocket-gateway',
      kafkaConnected,
      kafkaEnabled,
      connectedClients: clients.size,
      timestamp: new Date().toISOString(),
    })
  );
  app.get('/api/stats', (_req, res) => res.json(getPublicStats()));
  const limitFor = (req) => Math.max(1, Math.min(100, Number.parseInt(req.query.limit, 10) || 20));
  app.get('/api/recent-alerts', (req, res) =>
    res.json(stats.recentAlerts.slice(-limitFor(req)).reverse())
  );
  app.get('/api/recent-decisions', (req, res) =>
    res.json(stats.recentDecisions.slice(-limitFor(req)).reverse())
  );
  app.post('/api/test-alert', (_req, res) => {
    const alert = {
      alert_id: randomUUID(),
      transaction_id: randomUUID(),
      user_id: 'websocket_test_user',
      amount: 150000,
      risk_score: 0.9,
      decision: 'BLOCK',
      location: 'Delhi',
      velocity_score: 0.8,
      geo_anomaly_score: 0.9,
      amount_anomaly_score: 0.8,
      severity: 'CRITICAL',
      status: 'OPEN',
      actionable: false,
      source: 'simulated',
      triggered_rules: ['WEBSOCKET_TEST_ONLY'],
      is_sandbox: true,
      timestamp: new Date().toISOString(),
    };
    processEvent(TOPICS.ALERTS, alert);
    res.json({ message: 'Synthetic WebSocket test only; no transaction was scored', alert });
  });

  async function start(port = process.env.PORT || 3001, host = process.env.HOST || '0.0.0.0') {
    await new Promise((resolve, reject) => {
      server.once('error', reject);
      server.listen(port, host, resolve);
    });
    statsTimer = setInterval(broadcastStats, 5000);
    void startKafkaConsumer();
    return server.address();
  }
  async function stop() {
    stopping = true;
    clearTimeout(retryTimer);
    clearInterval(statsTimer);
    for (const ws of clients.keys()) ws.terminate();
    if (consumer) await consumer.disconnect();
    wss.close();
    await new Promise((resolve) => server.close(resolve));
  }
  return { app, server, start, stop, processEvent, getPublicStats };
}

if (require.main === module) {
  const gateway = createGateway();
  gateway
    .start()
    .then((address) => {
      console.log(
        `Fraud WebSocket gateway: http://localhost:${address.port} | ws://localhost:${address.port}/ws`
      );
    })
    .catch((error) => {
      console.error(error);
      process.exitCode = 1;
    });
  for (const signal of ['SIGINT', 'SIGTERM'])
    process.once(signal, () => gateway.stop().catch(console.error));
}
module.exports = { createGateway, TOPICS };
