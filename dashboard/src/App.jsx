import { useState, useEffect, useMemo } from 'react';
import { motion, AnimatePresence, LayoutGroup } from 'framer-motion';
import { 
  Shield, 
  AlertTriangle, 
  CheckCircle, 
  Ban, 
  Activity, 
  Users, 
  Zap, 
  Flame, 
  Search, 
  Filter, 
  TrendingUp, 
  MapPin, 
  Clock, 
  User, 
  MoreVertical,
  ChevronRight,
  RefreshCw,
  Globe
} from 'lucide-react';
import { 
  AreaChart, 
  Area, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer 
} from 'recharts';
import { useWebSocket } from './hooks/useWebSocket';
const formatAmount = (amount) => {
  if (amount == null) return '₹0';
  return '₹' + Number(amount).toLocaleString('en-IN');
};
const formatScore = (score) => {
  if (score == null) return '0.0%';
  return (score * 100).toFixed(1) + '%';
};
const formatTime = (ts) => {
  if (!ts) return '';
  const d = new Date(ts);
  return d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
};
const getRiskColor = (score) => {
  if (score >= 0.7) return 'var(--danger)';
  if (score >= 0.4) return 'var(--warning)';
  return 'var(--success)';
};
const StatCard = ({ icon: Icon, value, label, variant, trend }) => (
  <motion.div 
    whileHover={{ y: -4 }}
    className={`glass-panel stat-card ${variant}`}
  >
    <div className="stat-card-icon">
      <Icon size={24} />
    </div>
    <div className="stat-value">{value}</div>
    <div className="stat-label">{label}</div>
    {trend && (
      <div className="stat-trend" style={{ color: trend > 0 ? 'var(--success-bright)' : 'var(--danger-bright)' }}>
        <TrendingUp size={12} style={{ transform: trend < 0 ? 'rotate(180deg)' : 'none' }} />
        {Math.abs(trend)}% from last hr
      </div>
    )}
  </motion.div>
);
const RiskFactor = ({ label, value, icon: Icon, color }) => (
  <div className="risk-factor-item">
    <div className="factor-header">
      <div className="factor-info">
        <Icon size={14} style={{ color }} />
        <span className="factor-label">{label}</span>
      </div>
      <span className="factor-value" style={{ color }}>{formatScore(value)}</span>
    </div>
    <div className="factor-bar-bg">
      <motion.div 
        initial={{ width: 0 }}
        animate={{ width: `${(value || 0) * 100}%` }}
        style={{ backgroundColor: color }}
        className="factor-bar-fill"
      />
    </div>
  </div>
);
const AlertCard = ({ alert, isActive, onClick }) => {
  const score = alert.risk_score || 0;
  const severity = alert.severity || 'MEDIUM';
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      whileHover={{ scale: 1.01, backgroundColor: 'var(--bg-card-hover)' }}
      whileTap={{ scale: 0.98 }}
      onClick={onClick}
      className={`alert-card severity-${severity} ${isActive ? 'active' : ''}`}
    >
      <div className="alert-card-header">
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
          <span className={`badge ${alert.decision === 'BLOCK' ? 'badge-danger' : 'badge-warning'}`}>
            {alert.decision}
          </span>
          <span className={`badge ${severity === 'CRITICAL' ? 'badge-danger' : 'badge-info'}`}>
            {severity}
          </span>
          {alert.is_sandbox && (
            <span className="badge sandbox-badge">SANDBOX</span>
          )}
        </div>
        <div className="alert-time">
          <Clock size={12} /> {formatTime(alert.timestamp)}
        </div>
      </div>
      <div className="alert-body">
        <div className="alert-main-info">
          <div className="user-info">
            <User size={14} className="text-muted" />
            <span className="mono">{alert.user_id}</span>
          </div>
          <div className="amount-info">{formatAmount(alert.amount)}</div>
        </div>
      <AnimatePresence>
        {notification && (
          <motion.div 
            initial={{ opacity: 0, y: -50, x: '-50%' }}
            animate={{ opacity: 1, y: 20 }}
            exit={{ opacity: 0, scale: 0.5 }}
            style={{ 
              position: 'fixed', left: '50%', zIndex: 5000, 
              padding: '12px 24px', borderRadius: '8px',
              backgroundColor: notification.type === 'error' ? 'var(--danger)' : 'var(--success-bright)',
              color: 'white', fontWeight: 700, boxShadow: '0 10px 40px rgba(0,0,0,0.5)',
              display: 'flex', alignItems: 'center', gap: '8px'
            }}
          >
            {notification.type === 'error' ? <AlertTriangle size={18} /> : <CheckCircle size={18} />}
            {notification.message}
          </motion.div>
        )}
      </AnimatePresence>
            <div className="stats-grid">
              <StatCard icon={AlertTriangle} value={stats.totalAlerts} label="Security Alerts" variant="danger" trend={12} />
              <StatCard icon={Ban} value={stats.blockedCount} label="Blocked Items" variant="danger" />
              <StatCard icon={Activity} value={stats.totalDecisions || 0} label="Processed Req" variant="info" trend={5} />
              <StatCard icon={Users} value={stats.connectedClients || 1} label="Active Analysts" variant="info" />
            </div>
                <div className="glass-panel" style={{ padding: '20px 28px', display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
                  <div className="live-indicator"><div className="pulse-dot" /> KAFKA: READY</div>
                  <div className="live-indicator" style={{ color: 'var(--accent-purple)', borderColor: 'var(--accent-purple)' }}>
                    <div className="pulse-dot" style={{ background: 'var(--accent-purple)' }} /> REDIS: SYNCED
                  </div>
                  <div className="live-indicator" style={{ color: 'var(--accent-cyan)', borderColor: 'var(--accent-cyan)' }}>
                    <div className="pulse-dot" style={{ background: 'var(--accent-cyan)' }} /> ML-ENGINE: V1.2
                  </div>
                </div>
                <div className="glass-panel">
                  <div className="panel-header">
                    <div className="panel-title">
                      <Flame size={18} color="var(--danger)" /> Intelligence Feed
                    </div>
                    <div style={{ display: 'flex', gap: '12px' }}>
                      <div className="live-indicator" style={{ background: 'transparent', border: 'none' }}>
                        <div className="pulse-dot" style={{ backgroundColor: 'var(--danger)' }} />
                      </div>
                    </div>
                  </div>
                  <div className="panel-body alert-feed">
                    <AnimatePresence>
                      {alerts.length === 0 ? (
                        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="empty-state">
                          <Shield size={48} style={{ opacity: 0.2, marginBottom: '16px' }} />
                          <p>Monitoring network for threats...</p>
                        </motion.div>
                      ) : (
                        alerts.map((alert) => (
                          <AlertCard 
                            key={alert.alert_id} 
                            alert={alert} 
                            isActive={selectedAlert?.alert_id === alert.alert_id}
                            onClick={() => setSelectedAlert(alert)}
                          />
                        ))
                      )}
                    </AnimatePresence>
                  </div>
                </div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
                <div className="glass-panel" style={{ flex: 1 }}>
                  <div className="panel-header">
                    <div className="panel-title"><Activity size={18} color="var(--accent-cyan)" /> Activity Journal</div>
                  </div>
                  <div className="panel-body" style={{ padding: 0 }}>
                    <table className="txn-table">
                      <tbody>
                        <AnimatePresence initial={false}>
                          {decisions.slice(0, 12).map((txn) => (
                            <motion.tr 
                              key={txn.transaction_id}
                              layout
                              initial={{ opacity: 0 }}
                              animate={{ opacity: 1 }}
                              className="txn-row"
                            >
                              <td className={`txn-indicator ${txn.decision}`}></td>
                              <td className="txn-main">
                                <span className="txn-user">{txn.user_id}</span>
                                <span className="txn-id">{txn.transaction_id.slice(0, 8)}...</span>
                              </td>
                              <td className="txn-amount">{formatAmount(txn.amount)}</td>
                              <td>
                                <div className="txn-score-badge" style={{ 
                                  background: getRiskColor(txn.risk_score) + '20',
                                  color: getRiskColor(txn.risk_score)
                                }}>
                                  {(txn.risk_score * 100).toFixed(0)}%
                                </div>
                              </td>
                            </motion.tr>
                          ))}
                        </AnimatePresence>
                      </tbody>
                    </table>
                  </div>
                </div>
                        <div className="rationale-section">
                          <h4 className="section-title">Triggered Intelligence</h4>
                          <div className="rules-list">
                            {(selectedAlert.triggered_rules || []).map(rule => (
                               <div key={rule} className="rule-item">
                                 <AlertTriangle size={14} color="var(--warning)" />
                                 <span>{rule}</span>
                               </div>
                            ))}
                            {(selectedAlert.triggered_rules || []).length === 0 && (
                              <p className="text-dim" style={{ fontSize: '11px' }}>No specific rules triggered. Pure ML inference.</p>
                            )}
                          </div>
                        </div>
                        <div className="divider" />
                        <div className="rationale-section">
                          <h4 className="section-title">Enriched Metadata</h4>
                          <div className="metadata-compact">
                            <div className="meta-row"><span>Merchant</span> <span className="highlight-text">{selectedAlert.merchant_name || 'Unknown'}</span></div>
                            <div className="meta-row"><span>Category</span> <span>{selectedAlert.category}</span></div>
                            <div className="meta-row"><span>Device</span> <span className="mono">{selectedAlert.device_id}</span></div>
                            <div className="meta-row"><span>IP Addr</span> <span className="mono">{selectedAlert.ip_address}</span></div>
                          </div>
                        </div>
                        <div className="action-buttons-container">
                          <button className="btn btn-danger" style={{ flex: 1 }} onClick={() => handleAction('Block', selectedAlert)} disabled={processing}>
                            {processing ? <RefreshCw className="animate-spin" size={16} /> : <Ban size={16} />} 
                            Block Entity
                          </button>
                          <button className="btn btn-secondary" style={{ flex: 1 }} onClick={() => handleAction('Approve', selectedAlert)} disabled={processing}>
                            {processing ? <RefreshCw className="animate-spin" size={16} /> : <CheckCircle size={16} />} 
                            Safe / Dismiss
                          </button>
                        </div>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            </div>
          </>
        ) : (
          <div className="sandbox-workspace">
            <div className="glass-panel main-sandbox">
              <div className="panel-header">
                <div className="panel-title"><Zap size={18} color="var(--accent-yellow)" /> Smart Ingestion Console</div>
                <div className="badge badge-info">TESTING ENVIRONMENT</div>
              </div>
              <div className="panel-body">
                <div className="sandbox-grid">
                  <div className="editor-side">
                    <div className="template-selector">
                      <span className="filter-label">Quick Templates</span>
                      <div className="template-btns">
                        {Object.keys(SANDBOX_TEMPLATES).map(key => (
                          <button key={key} className="btn-micro" onClick={() => loadTemplate(key)}>
                            {key.replace('_', ' ')}
                          </button>
                        ))}
                      </div>
                    </div>
                    <div className="editor-container">
                      <div className="editor-header">
                        <span className="mono">payload.json</span>
                        <div className="editor-actions">
                          <button className="btn-icon" onClick={() => setSandboxPayload(JSON.stringify(JSON.parse(sandboxPayload), null, 2))}><RefreshCw size={12} /></button>
                        </div>
                      </div>
                      <textarea 
                        className="sandbox-editor mono" 
                        value={sandboxPayload}
                        onChange={(e) => setSandboxPayload(e.target.value)}
                        placeholder="{ ... }"
                      />
                    </div>
                    <button className="btn btn-primary btn-full" onClick={sendSandboxTxn} disabled={processing}>
                      {processing ? <RefreshCw className="animate-spin" size={16} /> : <Zap size={16} />}
                      Deploy to Kafka Pipeline
                    </button>
                  </div>
                  <div className="info-side">
                    <div className="config-card">
                      <div className="filter-label">X-Sandbox-Key</div>
                      <div className="key-display">
                        <code className="mono">sbx_live_55214488</code>
                        <div className="status-indicator">ACTIVE</div>
                      </div>
                    </div>
                    <div className="log-card">
                      <div className="card-header">
                        <span className="filter-label">Pipeline Logs</span>
                        <div className="badge-dot">Live</div>
                      </div>
                      <div className="log-feed">
                        {sandboxLogs.map(log => (
                          <motion.div 
                            initial={{ opacity: 0, x: 10 }}
                            animate={{ opacity: 1, x: 0 }}
                            key={log.id} 
                            className="log-item"
                          >
                            <div className="log-meta">
                              <span className="log-status">SUCCESS</span>
                              <span className="log-time">{log.time}</span>
                            </div>
                            <div className="log-details mono">
                              TXN: {log.id.slice(0, 12)}...
                            </div>
                          </motion.div>
                        ))}
                        {sandboxLogs.length === 0 && (
                          <div className="empty-logs">
                            <Clock size={24} className="text-dim" />
                            <p>Waiting for ingestion...</p>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
