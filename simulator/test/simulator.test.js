const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { parseOptions, sendTransaction } = require('../simulator');

test('CLI defaults and both option formats produce bounded real-pipeline runs', () => {
  assert.deepEqual(parseOptions([]), { mode: 'normal', count: null, interval: null });
  assert.deepEqual(parseOptions(['--mode', 'burst', '--count=3', '--interval', '0']), {
    mode: 'burst',
    count: 3,
    interval: 0,
  });
  assert.throws(() => parseOptions(['--count=0']), /positive integer/);
  assert.throws(() => parseOptions(['--mode']), /Missing value/);
  assert.throws(() => parseOptions(['--mode=unknown']), /Mode must/);
});

test('transaction sender posts supplied data and distinguishes accepted ingestion from scoring or failure', async (t) => {
  const requests = [];
  const server = http.createServer(async (req, res) => {
    let body = '';
    for await (const part of req) body += part;
    requests.push(JSON.parse(body));
    res.setHeader('Content-Type', 'application/json');
    if (req.url === '/unavailable') {
      res.writeHead(503);
      res.end('{"message":"Kafka unavailable"}');
    } else {
      res.writeHead(202);
      res.end('{"transaction_id":"txn-accepted","decision":"PENDING"}');
    }
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise((resolve) => server.close(resolve)));
  const url = `http://127.0.0.1:${server.address().port}`;
  const transaction = { user_id: 'real-pipeline-test', amount: 2500, location: 'Chennai' };
  assert.equal(
    (await sendTransaction(transaction, `${url}/api/v1/sandbox/ingest`)).decision,
    'PENDING'
  );
  assert.deepEqual(requests[0], transaction);
  await assert.rejects(
    sendTransaction(transaction, `${url}/unavailable`),
    /HTTP 503:.*Kafka unavailable/
  );
});
