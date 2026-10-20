// Run after starting the stack: node scripts/smoke-test.mjs
// Creates seven INR test transactions and resolves one resulting test case.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';

const require = createRequire(new URL('../websocket-gateway/package.json', import.meta.url));
const WebSocket = require('ws');
const dashboard = process.env.DASHBOARD_URL || 'http://localhost:5173';
const gateway = process.env.TRANSACTION_GATEWAY_URL || 'http://localhost:8080';
const orchestrator = process.env.ORCHESTRATOR_URL || 'http://localhost:8081';
const cases = process.env.CASE_MANAGEMENT_URL || 'http://localhost:8082';
const notifications = process.env.NOTIFICATION_SERVICE_URL || 'http://localhost:8083';
const websocket = process.env.WS_GATEWAY_URL || 'http://localhost:3001';
const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const messages = [];

async function request(url, options = {}, expected = 200) {
  const response = await fetch(url, { ...options, signal: AbortSignal.timeout(10000) });
  const body = await response.text();
  assert.equal(response.status, expected, `${url}: ${response.status} ${body}`);
  return JSON.parse(body);
}
const post = (url, data, expected = 200, headers = {}) =>
  request(
    url,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...headers },
      body: JSON.stringify(data),
    },
    expected
  );

async function eventually(check, description) {
  const deadline = Date.now() + 60000;
  while (Date.now() < deadline) {
    const result = await check();
    if (result) return result;
    await pause(500);
  }
  throw new Error(`Timed out waiting for ${description}`);
}

let socket;
try {
  for (const [name, url] of [
    ['transaction gateway', `${gateway}/api/v1/health`],
    ['orchestrator', `${orchestrator}/actuator/health`],
    ['case management', `${cases}/actuator/health`],
    ['notifications', `${notifications}/actuator/health`],
    ['WebSocket gateway', `${websocket}/health`],
  ]) {
    const health = await request(url);
    assert.equal(health.status, 'UP', `${name} health`);
    console.log(`PASS ${name} health`);
  }
  const page = await fetch(dashboard, { signal: AbortSignal.timeout(10000) });
  assert.equal(page.status, 200);
  assert.match(await page.text(), /id="root"/);
  await request(`${dashboard}/api/stats`);
  await request(`${dashboard}/api/v1/cases`);
  console.log('PASS dashboard and API proxies');

  const unauthorized = await fetch(`${gateway}/api/v1/transactions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{}',
    signal: AbortSignal.timeout(10000),
  });
  assert.ok([401, 403].includes(unauthorized.status), 'transactions require authentication');
  const { token } = await post(`${gateway}/api/v1/auth/login`, {
    username: process.env.DEMO_USERNAME || 'admin',
    password: process.env.DEMO_PASSWORD || 'password',
  });
  assert.ok(token);
  await post(`${gateway}/api/v1/transactions`, { user_id: 'invalid', amount: -1 }, 400, {
    Authorization: `Bearer ${token}`,
  });
  console.log('PASS authentication and invalid amount rejection');

  // Connect through Vite too: this verifies the browser's WebSocket proxy.
  const wsUrl = new URL('/ws', dashboard);
  wsUrl.protocol = wsUrl.protocol === 'https:' ? 'wss:' : 'ws:';
  socket = new WebSocket(wsUrl);
  socket.on('message', (raw) => messages.push(JSON.parse(raw.toString())));
  socket.on('error', (error) => console.error('WebSocket:', error.message));
  await eventually(
    () => messages.some((message) => message.type === 'CONNECTED'),
    'WebSocket connection'
  );

  const userId = `smoke_${randomUUID().slice(0, 8)}`;
  const transactionIds = [];
  const startedAt = Date.now();
  for (let index = 0; index < 7; index++) {
    const result = await post(
      `${gateway}/api/v1/transactions`,
      {
        user_id: userId,
        amount: 100,
        currency: 'INR',
        merchant_id: 'smoke_merchant',
        merchant_name: 'Smoke test store',
        category: 'retail',
        card_type: 'VISA',
        location: 'Chennai',
        device_id: 'smoke_device',
        channel: 'ONLINE',
      },
      202,
      { Authorization: `Bearer ${token}` }
    );
    assert.ok(result.transaction_id);
    transactionIds.push(result.transaction_id);
  }
  const decisions = await eventually(() => {
    const found = messages
      .filter(
        (message) =>
          message.type === 'FRAUD_DECISION' && transactionIds.includes(message.data?.transaction_id)
      )
      .map((message) => message.data);
    return new Set(found.map((value) => value.transaction_id)).size === 7 && found;
  }, 'all seven decisions from the real Kafka/Redis/C++ pipeline');
  assert.ok(
    decisions.every((decision) => !decision.triggered_rules?.includes('SCORING_FALLBACK')),
    'every transaction must be scored by the C++ gRPC service'
  );
  assert.ok(
    decisions.some((decision) => decision.decision === 'ALLOW'),
    'normal transaction allowed'
  );
  const blocked = decisions.find((decision) => decision.decision === 'BLOCK');
  assert.ok(blocked, 'velocity burst blocked');
  await eventually(
    () =>
      messages.some(
        (message) =>
          message.type === 'FRAUD_ALERT' && message.data?.transaction_id === blocked.transaction_id
      ),
    'live fraud alert'
  );
  console.log(
    `PASS seven live C++ decisions and alerts (${Date.now() - startedAt} ms total test time)`
  );

  const saved = await eventually(async () => {
    const recent = await request(`${orchestrator}/api/v1/cases`);
    return recent.find((value) => value.transaction_id === blocked.transaction_id);
  }, 'persisted blocked case');
  assert.equal(saved.decision, 'BLOCK');
  const separateCase = await request(`${cases}/api/v1/cases/${blocked.transaction_id}`);
  assert.equal(separateCase.transaction_id, blocked.transaction_id);
  const resolved = await post(`${orchestrator}/api/v1/cases/${blocked.transaction_id}/action`, {
    action: 'RESOLVE',
    analyst_id: 'smoke-test',
    notes: 'Automated end-to-end verification',
  });
  assert.equal(resolved.status, 'RESOLVED');
  await eventually(
    () =>
      messages.some(
        (message) =>
          message.type === 'FRAUD_ALERT' &&
          message.data?.transaction_id === blocked.transaction_id &&
          message.data?.status === 'RESOLVED'
      ),
    'analyst action WebSocket update'
  );
  await eventually(async () => {
    const inbox = await request(`${notifications}/api/v1/notifications/recent`);
    return inbox.some((value) => value.transaction_id === blocked.transaction_id);
  }, 'notification inbox');
  console.log(
    'PASS database-backed case retrieval, analyst resolution, live update, and notification inbox'
  );
  console.log(`PASS end-to-end smoke test; test user: ${userId}`);
} catch (error) {
  console.error(`FAIL ${error.message}`);
  process.exitCode = 1;
} finally {
  socket?.terminate();
}
