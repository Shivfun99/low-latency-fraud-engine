const test = require('node:test');
const assert = require('node:assert/strict');
const WebSocket = require('ws');
const { createGateway, TOPICS } = require('../src/server');

function inbox(ws) {
  const messages = [];
  ws.on('message', (raw) => messages.push(JSON.parse(raw.toString())));
  return async (type) => {
    const deadline = Date.now() + 3000;
    while (Date.now() < deadline) {
      const index = messages.findIndex((message) => message.type === type);
      if (index !== -1) return messages.splice(index, 1)[0];
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    throw new Error(`Timed out waiting for ${type}`);
  };
}

test('HTTP and WebSocket retain decisions, analyst updates, and explicitly synthetic test alerts', async (t) => {
  const gateway = createGateway({ kafkaEnabled: false });
  const { port } = await gateway.start(0, '127.0.0.1');
  t.after(() => gateway.stop());
  const url = `http://127.0.0.1:${port}`;
  const ws = new WebSocket(`ws://127.0.0.1:${port}/ws`);
  const next = inbox(ws);
  assert.equal((await next('CONNECTED')).stats.connectedClients, 1);
  assert.equal((await fetch(`${url}/health`)).status, 200);

  const decision = {
    transaction_id: 'txn-1',
    decision: 'BLOCK',
    user_id: 'user-1',
    risk_score: 0.95,
  };
  gateway.processEvent(TOPICS.DECISIONS, decision);
  assert.deepEqual((await next('FRAUD_DECISION')).data, decision);
  gateway.processEvent(TOPICS.DECISIONS, decision);
  assert.equal(
    gateway.getPublicStats().totalDecisions,
    1,
    'duplicate delivery must not double count'
  );

  gateway.processEvent(TOPICS.ALERTS, {
    alert_id: 'alert-1',
    transaction_id: 'txn-1',
    actionable: true,
    status: 'OPEN',
  });
  assert.equal((await next('FRAUD_ALERT')).data.actionable, true);
  gateway.processEvent(TOPICS.ALERTS, {
    alert_id: 'alert-1',
    transaction_id: 'txn-1',
    actionable: false,
    status: 'RESOLVED',
  });
  assert.equal(
    (await next('FRAUD_ALERT')).data.actionable,
    false,
    'analyst resolution must remain non-actionable'
  );
  assert.equal(
    gateway.getPublicStats().totalAlerts,
    1,
    'case update must not create another alert'
  );

  const response = await fetch(`${url}/api/test-alert`, { method: 'POST' });
  const { alert } = await response.json();
  assert.equal(alert.source, 'simulated');
  assert.equal(alert.actionable, false);
  assert.equal((await next('FRAUD_ALERT')).data.alert_id, alert.alert_id);
  assert.equal(
    gateway.getPublicStats().totalDecisions,
    1,
    'synthetic UI test must not fabricate scored decisions'
  );

  const recent = await (await fetch(`${url}/api/recent-alerts?limit=2`)).json();
  assert.equal(recent[0].alert_id, alert.alert_id);
  assert.equal(recent[1].status, 'RESOLVED');
  assert.equal((await (await fetch(`${url}/api/recent-alerts?limit=-10`)).json()).length, 1);

  ws.send('{broken json');
  assert.equal((await next('ERROR')).message, 'Invalid message JSON');
  ws.send(JSON.stringify({ type: 'SUBSCRIBE', topics: 'invalid subscription type' }));
  ws.send(JSON.stringify({ type: 'PING' }));
  assert.ok((await next('PONG')).timestamp);

  const reconnect = new WebSocket(`ws://127.0.0.1:${port}/ws`);
  const snapshot = await inbox(reconnect)('CONNECTED');
  assert.equal(
    snapshot.recentAlerts[0].alert_id,
    alert.alert_id,
    'reconnect snapshot must be newest first'
  );
  assert.equal(snapshot.recentDecisions[0].transaction_id, 'txn-1');
});
