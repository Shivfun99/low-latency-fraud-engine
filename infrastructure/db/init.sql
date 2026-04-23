-- ═══════════════════════════════════════════════════════════
-- Fraud Detection System — PostgreSQL Schema
-- ═══════════════════════════════════════════════════════════

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id              UUID PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    amount          DECIMAL(15,2) NOT NULL,
    currency        VARCHAR(3) DEFAULT 'INR',
    merchant_id     VARCHAR(64),
    merchant_name   VARCHAR(255),
    category        VARCHAR(64),
    card_type       VARCHAR(32),
    location        VARCHAR(128),
    device_id       VARCHAR(128),
    ip_address      VARCHAR(45),
    channel         VARCHAR(32) DEFAULT 'ONLINE',
    status          VARCHAR(16) DEFAULT 'PENDING',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    processed_at    TIMESTAMP WITH TIME ZONE
);

-- Fraud decisions table
CREATE TABLE IF NOT EXISTS fraud_decisions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      UUID NOT NULL REFERENCES transactions(id),
    risk_score          DECIMAL(5,4) NOT NULL,
    decision            VARCHAR(16) NOT NULL CHECK (decision IN ('ALLOW', 'REVIEW', 'BLOCK')),
    rule_triggered      VARCHAR(255),
    ml_score            DECIMAL(5,4),
    velocity_score      DECIMAL(5,4),
    geo_anomaly_score   DECIMAL(5,4),
    amount_anomaly_score DECIMAL(5,4),
    processing_time_ms  INTEGER,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Fraud alerts table
CREATE TABLE IF NOT EXISTS fraud_alerts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      UUID NOT NULL REFERENCES transactions(id),
    decision_id         UUID REFERENCES fraud_decisions(id),
    alert_type          VARCHAR(32) NOT NULL,
    severity            VARCHAR(16) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    description         TEXT,
    analyst_id          VARCHAR(64),
    status              VARCHAR(16) DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'DISMISSED')),
    resolved_at         TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Analyst actions audit log
CREATE TABLE IF NOT EXISTS analyst_actions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id        UUID NOT NULL REFERENCES fraud_alerts(id),
    analyst_id      VARCHAR(64) NOT NULL,
    action          VARCHAR(32) NOT NULL,
    notes           TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_fraud_decisions_transaction ON fraud_decisions(transaction_id);
CREATE INDEX idx_fraud_decisions_decision ON fraud_decisions(decision);
CREATE INDEX idx_fraud_alerts_status ON fraud_alerts(status);
CREATE INDEX idx_fraud_alerts_severity ON fraud_alerts(severity);
CREATE INDEX idx_fraud_alerts_created_at ON fraud_alerts(created_at);

GRANT USAGE ON SCHEMA public TO fraud_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    transactions,
    fraud_decisions,
    fraud_alerts,
    analyst_actions
TO fraud_user;
