-- Beacon metadata schema.
--
-- Postgres stores operational configuration that is not itself observability data:
-- the catalogue of known services and the alerting rules that downstream tooling can
-- evaluate against anomaly scores coming out of Elasticsearch.

CREATE TABLE IF NOT EXISTS service_catalog (
    name         TEXT PRIMARY KEY,
    team         TEXT,
    environment  TEXT NOT NULL DEFAULT 'unknown',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS alert_rule (
    id                 BIGSERIAL PRIMARY KEY,
    service            TEXT,
    min_anomaly_score  DOUBLE PRECISION NOT NULL DEFAULT 0.8,
    min_severity       INT NOT NULL DEFAULT 4,
    channel            TEXT NOT NULL DEFAULT 'slack',
    enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Durable record of every alert the anomaly-worker fires. Read back via
-- GET /api/anomaly/alerts and shown on the dashboard.
CREATE TABLE IF NOT EXISTS alert (
    id             BIGSERIAL PRIMARY KEY,
    event_id       TEXT NOT NULL,
    service        TEXT,
    team           TEXT,
    severity       INT NOT NULL,
    anomaly_score  DOUBLE PRECISION NOT NULL,
    channel        TEXT NOT NULL,
    message        TEXT,
    notified       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_created_at ON alert (created_at);
CREATE INDEX IF NOT EXISTS idx_alert_event_id ON alert (event_id);

-- Seed a couple of example services and a default high-severity alert rule.
INSERT INTO service_catalog (name, team, environment) VALUES
    ('orders-api-prod',    'checkout',  'prod'),
    ('checkout-api-prod',  'checkout',  'prod'),
    ('search-api-staging', 'discovery', 'staging')
ON CONFLICT (name) DO NOTHING;

-- A strict per-service rule for the flagship prod service, plus a catch-all default (service NULL)
-- so any high-severity anomaly on an unlisted service still alerts.
INSERT INTO alert_rule (service, min_anomaly_score, min_severity, channel)
SELECT 'orders-api-prod', 0.85, 4, 'slack'
WHERE NOT EXISTS (SELECT 1 FROM alert_rule WHERE service = 'orders-api-prod');

INSERT INTO alert_rule (service, min_anomaly_score, min_severity, channel)
SELECT NULL, 0.9, 4, 'slack'
WHERE NOT EXISTS (SELECT 1 FROM alert_rule WHERE service IS NULL);
