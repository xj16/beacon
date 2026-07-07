-- Seed the same service catalogue and alert rules that docker/postgres-init.sql defines, so the
-- default H2 profile behaves identically to a real Postgres deployment. Idempotent: each INSERT
-- only fires when the row is absent, so restarts and Postgres (already seeded) are safe.

INSERT INTO service_catalog (name, team, environment)
SELECT 'orders-api-prod', 'checkout', 'prod'
WHERE NOT EXISTS (SELECT 1 FROM service_catalog WHERE name = 'orders-api-prod');

INSERT INTO service_catalog (name, team, environment)
SELECT 'checkout-api-prod', 'checkout', 'prod'
WHERE NOT EXISTS (SELECT 1 FROM service_catalog WHERE name = 'checkout-api-prod');

INSERT INTO service_catalog (name, team, environment)
SELECT 'search-api-staging', 'discovery', 'staging'
WHERE NOT EXISTS (SELECT 1 FROM service_catalog WHERE name = 'search-api-staging');

-- A strict per-service rule for the flagship prod service...
INSERT INTO alert_rule (service, min_anomaly_score, min_severity, channel, enabled)
SELECT 'orders-api-prod', 0.85, 4, 'slack', TRUE
WHERE NOT EXISTS (SELECT 1 FROM alert_rule WHERE service = 'orders-api-prod');

-- ...and a catch-all default rule (service NULL) so any high-severity anomaly on an unlisted
-- service still alerts. Keeps the engine useful out of the box.
INSERT INTO alert_rule (service, min_anomaly_score, min_severity, channel, enabled)
SELECT NULL, 0.9, 4, 'slack', TRUE
WHERE NOT EXISTS (SELECT 1 FROM alert_rule WHERE service IS NULL);
