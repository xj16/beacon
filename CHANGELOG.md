# Changelog

All notable changes to Beacon are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] — 2026-07-07

A large enhancement pass that closes the gap between what the README pitched and what the code
actually did, and turns the invisible JSON API into something you can look at.

### Added

- **Alerting engine (the Postgres box is now real).** `anomaly-worker` wires a JPA datasource
  (in-memory H2 by default, the compose Postgres when `SPRING_DATASOURCE_URL` is set), loads
  `service_catalog` + `alert_rule`, and after scoring evaluates each event against its service's
  rule (`min_anomaly_score` **and** `min_severity`). Breaches are persisted to a new `alert` table
  and delivered through a pluggable `Notifier` — a real Slack webhook when `SLACK_WEBHOOK_URL` is
  set, otherwise a logging channel. Alert firing is idempotent per event id. New endpoint:
  `GET /api/anomaly/alerts`.
- **Live observability dashboard.** A dependency-free single-page dashboard (`query-api` serves it
  at `/`) with hand-drawn inline-SVG charts: events-over-time, a severity donut, top-services and
  by-environment bars, and an anomaly feed sorted by score with red→green heat colouring and
  severity/score filters. A `?demo=1` mode replays an embedded dataset so it renders with no
  backend; a standalone copy lives in `dashboard/` for portfolio embedding.
- **Real dead-letter + retry path.** The ingest listener now runs with `RECORD` ack and a
  `DefaultErrorHandler` (exponential backoff) plus a `DeadLetterPublishingRecoverer` that routes
  poison/failed records to `beacon.events.DLT`. A `DeadLetterIT` publishes a poison record and
  asserts it lands on the DLT. This makes the README's "retry / route to a DLT" claim true.
- **Self-instrumentation.** All three services expose `/actuator/prometheus` via
  `micrometer-registry-prometheus`, with custom meters: `beacon_events_indexed_total`,
  `beacon_index_latency`, `beacon_scoring_latency`, `beacon_anomaly_backend_llm` (gauge), and
  `beacon_alerts_fired_total`.
- **Input validation & hardening on the ingest edge.** `POST /api/events` now binds a validated
  `EventRequest` (`@NotBlank`/`@Size`/`@Pattern`, bounded attributes), a `@RestControllerAdvice`
  returns clean structured `400`s, request bodies are size-bounded, and a dependency-free
  per-IP fixed-window rate limiter guards `/api/**` (`429` + `Retry-After`).
- **CORS policy** on `query-api`: same-origin only by default, opt-in allowlist via
  `BEACON_CORS_ALLOWED_ORIGINS` for hosting the dashboard elsewhere.
- **Docker.** A multi-stage `Dockerfile` (one image per service via a `SERVICE` build arg) and
  `docker-compose` services for all three apps. A `demo` compose profile
  (`docker compose --profile demo up`) brings the stack up already seeded with realistic events and
  triggers a scoring pass, so the dashboard is alive on first load. Seeder: `scripts/demo-seed.sh`.
- **Coverage.** JaCoCo per-module reports plus an aggregate `jacocoRootReport` / `printCoverage`
  task; CI runs it and uploads the report. Coverage badge added to the README.
- **Flagship tests.** `AlertServiceTest` (full Spring context on H2, exercising
  seed→evaluate→persist→notify→read-back), `AlertRuleTest`, `IngestControllerTest` (validation /
  400s / id backfill), `SearchControllerTest` (param clamping / shapes), `RateLimitFilterTest`, and
  `DeadLetterIT`. The existing `AnomalyWorkerIT` now also asserts alerts fire end-to-end.

### Changed

- **Bulk score write-back.** `AnomalyWorker.scoreOnce` now flushes score updates in a single
  Elasticsearch `_bulk` request instead of one HTTP round-trip per event.
- Postgres schema (`docker/postgres-init.sql`) gains the `alert` table and a catch-all default
  alert rule; `alert_rule.service` is no longer a hard FK so catch-all (`NULL` service) rules work.
- `README` overhauled: badge row, feature list, architecture, one-command quickstart, dashboard and
  alerting docs.

### Notes

- Everything still runs with **zero paid keys** and no mandatory external services: the alerting
  store defaults to embedded H2, anomaly scoring falls back to the statistical scorer without
  Ollama, and the dashboard has an offline demo mode.

## [0.1.0]

- Initial release: Kafka → Elasticsearch ingest pipeline (Java/Spring Boot), Kotlin query API
  (full-text search + aggregations), anomaly worker (local Ollama LLM with a deterministic
  statistical fallback), Testcontainers integration tests, and a `docker-compose` infra stack.

[Unreleased]: https://github.com/xj16/beacon/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/xj16/beacon/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/xj16/beacon/releases/tag/v0.1.0
