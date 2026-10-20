import { useMemo, useState } from 'react';
import {
  Shield,
  AlertTriangle,
  CheckCircle,
  Ban,
  Activity,
  Users,
  Zap,
  MapPin,
  Clock,
  RefreshCw,
} from 'lucide-react';
import { useWebSocket } from './hooks/useWebSocket';

const amountText = (amount) => `₹${Number(amount || 0).toLocaleString('en-IN')}`;
const scoreText = (score) => `${(Number(score || 0) * 100).toFixed(1)}%`;
const timeText = (value) => (value ? new Date(value).toLocaleTimeString() : '—');
const baseTransaction = {
  user_id: 'dashboard_demo_user',
  amount: 2500,
  currency: 'INR',
  merchant_id: 'merchant_demo',
  merchant_name: 'Demo Store',
  category: 'ECOMMERCE',
  location: 'Chennai',
  device_id: 'dashboard_primary_device',
  channel: 'ONLINE',
};
const templates = {
  'Normal purchase': baseTransaction,
  'High amount': { ...baseTransaction, amount: 150000 },
  'New device / city': {
    ...baseTransaction,
    amount: 40000,
    location: 'Delhi',
    device_id: 'unknown_device',
  },
};

async function postJson(path, body) {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const text = await response.text();
  let data;
  try {
    data = JSON.parse(text);
  } catch {
    data = { message: text };
  }
  if (!response.ok)
    throw new Error(data.message || data.error || `Request failed (${response.status})`);
  return data;
}

function StatCard({ icon: Icon, value, label }) {
  return (
    <div className="glass-panel stat-card">
      <Icon size={23} />
      <strong>{value || 0}</strong>
      <span>{label}</span>
    </div>
  );
}
function Badge({ decision }) {
  return <span className={`badge ${decision || ''}`}>{decision || 'PENDING'}</span>;
}
function RiskFactor({ label, value }) {
  return (
    <div className="risk-factor">
      <span>
        {label}
        <strong>{scoreText(value)}</strong>
      </span>
      <progress max="1" value={Math.min(1, Math.max(0, Number(value || 0)))} />
    </div>
  );
}

export default function App() {
  const { connected, stats, alerts, decisions } = useWebSocket();
  const [tab, setTab] = useState('monitor');
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [selectedId, setSelectedId] = useState(null);
  const [processing, setProcessing] = useState(false);
  const [notification, setNotification] = useState(null);
  const [payload, setPayload] = useState(JSON.stringify(baseTransaction, null, 2));
  const [logs, setLogs] = useState([]);
  const [caseUpdates, setCaseUpdates] = useState({});
  const selected = alerts.find((alert) => alert.alert_id === selectedId);
  const selectedAlert = selected ? { ...selected, ...caseUpdates[selected.alert_id] } : null;
  const filtered = useMemo(
    () =>
      alerts.filter(
        (alert) =>
          (filter === 'ALL' || alert.decision === filter) &&
          `${alert.user_id} ${alert.transaction_id} ${alert.location}`
            .toLowerCase()
            .includes(query.toLowerCase())
      ),
    [alerts, query, filter]
  );

  async function submitTransaction() {
    setNotification(null);
    setProcessing(true);
    try {
      const transaction = JSON.parse(payload);
      const result = await postJson('/api/v1/sandbox/ingest', transaction);
      setLogs((previous) =>
        [{ ...result, submittedAt: new Date().toISOString() }, ...previous].slice(0, 20)
      );
      setNotification({
        message: 'Transaction accepted. Its decision will arrive in the live feed.',
      });
    } catch (error) {
      setNotification({ error: true, message: error.message });
    } finally {
      setProcessing(false);
    }
  }
  async function handleAction(action) {
    if (!selectedAlert?.actionable) return;
    setProcessing(true);
    setNotification(null);
    try {
      const result = await postJson(`/api/v1/cases/${selectedAlert.transaction_id}/action`, {
        action,
        analyst_id: 'dashboard-user',
        notes: `Recorded from dashboard: ${action}`,
      });
      setCaseUpdates((previous) => ({
        ...previous,
        [selectedAlert.alert_id]: {
          status: result.status || result.alert_status || 'RESOLVED',
          actionable: false,
        },
      }));
      setNotification({ message: `${action} recorded for this case.` });
    } catch (error) {
      setNotification({ error: true, message: error.message });
    } finally {
      setProcessing(false);
    }
  }

  return (
    <div className="app">
      <header className="header">
        <div className="brand">
          <Shield size={30} />
          <div>
            <h1>Fraud Shield</h1>
            <span>Real-time transaction monitoring</span>
          </div>
        </div>
        <div className={`connection ${connected ? 'online' : 'offline'}`}>
          <span className="status-dot" />
          {connected ? 'Live connection' : 'Connecting to gateway…'}
        </div>
      </header>
      <main className="main-content">
        <nav className="tabs" aria-label="Workspace">
          <button className={tab === 'monitor' ? 'active' : ''} onClick={() => setTab('monitor')}>
            <Activity size={17} /> Live monitor
          </button>
          <button className={tab === 'test' ? 'active' : ''} onClick={() => setTab('test')}>
            <Zap size={17} /> Test transactions
          </button>
        </nav>
        {notification && (
          <div
            role={notification.error ? 'alert' : 'status'}
            className={`notice ${notification.error ? 'error' : ''}`}
          >
            {notification.error ? <AlertTriangle size={18} /> : <CheckCircle size={18} />}
            <span>{notification.message}</span>
            <button aria-label="Dismiss message" onClick={() => setNotification(null)}>
              ×
            </button>
          </div>
        )}
        {tab === 'monitor' ? (
          <>
            <div className="stats-grid">
              <StatCard icon={Activity} value={stats.totalDecisions} label="Scored transactions" />
              <StatCard icon={AlertTriangle} value={stats.totalAlerts} label="Alerts received" />
              <StatCard icon={Ban} value={stats.blockedCount} label="Block decisions" />
              <StatCard icon={Users} value={stats.connectedClients} label="Connected dashboards" />
            </div>
            <div className="pipeline-status">
              <span className={`status-dot ${stats.kafkaConnected ? 'online' : 'offline'}`} />
              Kafka consumer: {connected && stats.kafkaConnected ? 'connected' : 'not connected'}
              <span className="muted">
                Counters cover this gateway session. Test traffic is marked SANDBOX.
              </span>
            </div>
            <div className="dashboard-grid">
              <div className="stack">
                <section className="glass-panel">
                  <div className="panel-header">
                    <h2>
                      <AlertTriangle size={19} /> Alert feed
                    </h2>
                    <span className="muted">{filtered.length} recent</span>
                  </div>
                  <div className="filters">
                    <input
                      aria-label="Search alerts"
                      placeholder="Search user, transaction or city"
                      value={query}
                      onChange={(event) => setQuery(event.target.value)}
                    />
                    <select
                      aria-label="Filter decision"
                      value={filter}
                      onChange={(event) => setFilter(event.target.value)}
                    >
                      <option value="ALL">All decisions</option>
                      <option>BLOCK</option>
                      <option>REVIEW</option>
                    </select>
                  </div>
                  <div className="alert-feed">
                    {filtered.length === 0 ? (
                      <div className="empty-state">
                        <Shield size={36} />
                        <p>No matching alerts yet.</p>
                        <span>Submit a test transaction to exercise the pipeline.</span>
                      </div>
                    ) : (
                      filtered.map((alert) => (
                        <button
                          className={`alert-card ${selectedId === alert.alert_id ? 'selected' : ''}`}
                          key={alert.alert_id}
                          onClick={() => setSelectedId(alert.alert_id)}
                        >
                          <div className="alert-card-header">
                            <div className="badges">
                              <Badge decision={alert.decision} />
                              {alert.is_sandbox && <span className="badge sandbox">SANDBOX</span>}
                              {alert.source === 'simulated' && (
                                <span className="badge sandbox">SYNTHETIC</span>
                              )}
                            </div>
                            <span className="muted">{timeText(alert.timestamp)}</span>
                          </div>
                          <div className="alert-summary">
                            <strong>{alert.user_id}</strong>
                            <strong>{amountText(alert.amount)}</strong>
                          </div>
                          <div className="alert-card-footer">
                            <span>
                              <MapPin size={13} />
                              {alert.location || 'Unknown location'}
                            </span>
                            <span>Risk {scoreText(alert.risk_score)}</span>
                          </div>
                        </button>
                      ))
                    )}
                  </div>
                </section>
                <section className="glass-panel">
                  <div className="panel-header">
                    <h2>
                      <Activity size={19} /> Recent decisions
                    </h2>
                  </div>
                  {decisions.length === 0 ? (
                    <p className="empty-state">Waiting for scored transactions…</p>
                  ) : (
                    <div className="table-scroll">
                      <table>
                        <thead>
                          <tr>
                            <th>User / transaction</th>
                            <th>Amount</th>
                            <th>Decision</th>
                            <th>Risk</th>
                          </tr>
                        </thead>
                        <tbody>
                          {decisions.slice(0, 15).map((transaction) => (
                            <tr key={transaction.transaction_id}>
                              <td>
                                {transaction.user_id}
                                <small className="mono">
                                  {transaction.transaction_id?.slice(0, 12)}
                                </small>
                              </td>
                              <td>{amountText(transaction.amount)}</td>
                              <td>
                                <Badge decision={transaction.decision} />
                              </td>
                              <td>{scoreText(transaction.risk_score)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </section>
              </div>
              <aside className="glass-panel detail-panel">
                <div className="panel-header">
                  <h2>
                    <Shield size={19} /> Alert details
                  </h2>
                </div>
                {!selectedAlert ? (
                  <div className="empty-state">
                    <Shield size={36} />
                    <p>Select an alert to inspect its risk factors.</p>
                  </div>
                ) : (
                  <div className="panel-body stack">
                    <div className="detail-score">
                      <strong>{scoreText(selectedAlert.risk_score)}</strong>
                      <Badge decision={selectedAlert.decision} />
                    </div>
                    <p>
                      {selectedAlert.description ||
                        'Risk assessment from the fraud scoring pipeline.'}
                    </p>
                    <div className="metadata">
                      <span>
                        User<strong>{selectedAlert.user_id}</strong>
                      </span>
                      <span>
                        Amount<strong>{amountText(selectedAlert.amount)}</strong>
                      </span>
                      <span>
                        Location<strong>{selectedAlert.location || '—'}</strong>
                      </span>
                      <span>
                        Status<strong>{selectedAlert.status}</strong>
                      </span>
                      <span>
                        Processing time
                        <strong>
                          {selectedAlert.processing_time_ms == null
                            ? '—'
                            : `${selectedAlert.processing_time_ms} ms`}
                        </strong>
                      </span>
                    </div>
                    <div>
                      <h3>Risk factors</h3>
                      <RiskFactor label="Velocity" value={selectedAlert.velocity_score} />
                      <RiskFactor
                        label="Location anomaly"
                        value={selectedAlert.geo_anomaly_score}
                      />
                      <RiskFactor
                        label="Amount anomaly"
                        value={selectedAlert.amount_anomaly_score}
                      />
                    </div>
                    <div>
                      <h3>Triggered rules</h3>
                      {selectedAlert.triggered_rules?.length ? (
                        <ul className="rules-list">
                          {selectedAlert.triggered_rules.map((rule) => (
                            <li key={rule}>{rule}</li>
                          ))}
                        </ul>
                      ) : (
                        <p className="muted">No explicit rules were reported.</p>
                      )}
                    </div>
                    <div>
                      <h3>Analyst action</h3>
                      <p className="muted">Records the case outcome in the audit log.</p>
                      <div className="action-buttons">
                        <button
                          className="btn danger"
                          disabled={processing || !selectedAlert.actionable}
                          onClick={() => handleAction('BLOCK')}
                        >
                          <Ban size={15} /> Confirm block
                        </button>
                        <button
                          className="btn secondary"
                          disabled={processing || !selectedAlert.actionable}
                          onClick={() => handleAction('APPROVE')}
                        >
                          <CheckCircle size={15} /> Approve
                        </button>
                      </div>
                      {!selectedAlert.actionable && (
                        <p className="muted">
                          This alert is resolved or is a synthetic display test.
                        </p>
                      )}
                    </div>
                    <small className="mono muted">
                      Transaction: {selectedAlert.transaction_id}
                    </small>
                  </div>
                )}
              </aside>
            </div>
          </>
        ) : (
          <section className="glass-panel">
            <div className="panel-header">
              <h2>
                <Zap size={19} /> Test the full pipeline
              </h2>
              <span className="badge sandbox">SANDBOX</span>
            </div>
            <div className="panel-body sandbox-grid">
              <div className="stack">
                <p>
                  Send a synthetic transaction through Kafka, Redis and the scoring service. The
                  resulting decision appears in the live monitor.
                </p>
                <div className="template-buttons">
                  {Object.entries(templates).map(([name, transaction]) => (
                    <button
                      className="btn secondary"
                      key={name}
                      onClick={() => setPayload(JSON.stringify(transaction, null, 2))}
                    >
                      {name}
                    </button>
                  ))}
                </div>
                <label htmlFor="transaction-payload">Transaction JSON</label>
                <textarea
                  id="transaction-payload"
                  spellCheck="false"
                  className="mono"
                  value={payload}
                  onChange={(event) => setPayload(event.target.value)}
                />
                <button className="btn primary" disabled={processing} onClick={submitTransaction}>
                  {processing ? <RefreshCw size={16} className="spin" /> : <Zap size={16} />} Submit
                  transaction
                </button>
              </div>
              <div>
                <h3>Submitted transactions</h3>
                <p className="muted">
                  Acceptance confirms ingestion; decisions arrive asynchronously.
                </p>
                {logs.length === 0 ? (
                  <div className="empty-state">
                    <Clock size={30} />
                    <p>No transactions submitted yet.</p>
                  </div>
                ) : (
                  logs.map((log) => {
                    const decision = decisions.find(
                      (item) => item.transaction_id === log.transaction_id
                    );
                    return (
                      <div className="submission" key={log.transaction_id}>
                        <div>
                          <Badge decision={decision?.decision || 'PENDING'} />
                          <span className="muted">{timeText(log.submittedAt)}</span>
                        </div>
                        <code>{log.transaction_id}</code>
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          </section>
        )}
        <footer>
          Fraud Shield · Demonstration engine · Scores are advisory; no payment processor is
          connected.
        </footer>
      </main>
    </div>
  );
}
