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
    service            TEXT REFERENCES service_catalog(name),
    min_anomaly_score  DOUBLE PRECISION NOT NULL DEFAULT 0.8,
    min_severity       INT NOT NULL DEFAULT 4,
    channel            TEXT NOT NULL DEFAULT 'slack',
    enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed a couple of example services and a default high-severity alert rule.
INSERT INTO service_catalog (name, team, environment) VALUES
    ('orders-api-prod',    'checkout',  'prod'),
    ('checkout-api-prod',  'checkout',  'prod'),
    ('search-api-staging', 'discovery', 'staging')
ON CONFLICT (name) DO NOTHING;

INSERT INTO alert_rule (service, min_anomaly_score, min_severity, channel)
VALUES ('orders-api-prod', 0.85, 4, 'slack')
ON CONFLICT DO NOTHING;
