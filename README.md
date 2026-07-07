<div align="center">

# Beacon

**A Kafka → Elasticsearch observability pipeline with a Kotlin query API, a local-LLM anomaly
detector, a real alerting engine, and a dependency-free live dashboard.**

[![CI](https://github.com/xj16/beacon/actions/workflows/ci.yml/badge.svg)](https://github.com/xj16/beacon/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/badge/coverage-66%25%20lines-brightgreen)](#testing)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Java 17](https://img.shields.io/badge/Java-17-orange)
![Kotlin 1.9](https://img.shields.io/badge/Kotlin-1.9-7F52FF)
![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F)

</div>

Beacon ingests a firehose of structured log/observability events from Kafka, enriches them, and
indexes them into Elasticsearch. A Kotlin service exposes ergonomic full-text search and
aggregations over that data; a background worker scores every event for anomalousness — using a
**local** LLM (Ollama) when one is available and a fast, deterministic statistical scorer when it is
not — and an **alerting engine** turns high-scoring events into durable alerts routed to Slack (or a
log). A **dependency-free dashboard** puts it all on screen. Everything runs on free, self-hostable
infrastructure with **no paid API keys**.

> **Try it in one command:** `docker compose --profile demo up --build` → open
> **http://localhost:8082** for a dashboard that is already alive with seeded data.
> No Docker? Open [`dashboard/index.html`](dashboard/index.html) directly — it ships an offline demo.

---

## Why

Most "observability" is just `grep` over log files until it isn't. Beacon is a compact but real
reference for the pattern teams actually reach for at scale:

- **decouple producers from storage** with Kafka, so a spike in log volume never takes down the
  services emitting the logs — and a poison message is retried and dead-lettered, never wedged;
- **enrich on the way in** (normalized severity, environment tagging, idempotent ids) so the data
  is queryable, not just stored;
- **make it searchable** with full-text queries and aggregations instead of raw index dumps;
- **surface the needles**, not the haystack, by scoring events for anomalousness — with an LLM if
  you have one, a solid heuristic scorer if you don't;
- **close the loop** from detect → alert: evaluate scores against per-service rules and fire.

It is intentionally small enough to read in one sitting, but every piece does something real.

---

## Features

- **Kafka ingestion** with JSON-deserialized `LogEvent`s and automatic topic creation.
- **Reliability:** `RECORD`-ack consumer + exponential-backoff retry + a
  **dead-letter topic** (`beacon.events.DLT`) for poison/failed records, so a document is only
  committed once it is durably indexed.
- **Enrichment:** textual level → numeric `severity`, coarse `environment` bucket, ingest
  timestamp, and idempotent document ids (safe on re-delivery).
- **Elasticsearch indexing** with an explicit mapping (full-text `message` + `keyword` sub-field,
  keyword dimensions, date fields), created automatically on startup.
- **Full-text search** (`GET /api/search`) with filters: free-text, `service`, `environment`,
  `minSeverity`, `from`/`to` range — newest-first, paginated.
- **Aggregations** (`GET /api/aggregations`): counts bucketed by service, severity, and environment.
- **Anomaly detection** preferring a **local Ollama model**, degrading gracefully to a deterministic
  scorer (severity + keyword + latency + 5xx signals). Score write-back is **bulk** (`_bulk`).
- **Alerting engine:** loads `service_catalog` + `alert_rule` from Postgres (or embedded H2),
  evaluates each scored event (`min_anomaly_score` **and** `min_severity`), persists breaches to an
  `alert` table, and delivers via a pluggable notifier (**Slack webhook** or logging).
  `GET /api/anomaly/alerts`.
- **Live dashboard:** a single, dependency-free page (served at `/` by query-api) with inline-SVG
  events-over-time, severity, service and environment charts, and an anomaly feed with red→green
  heat colouring and score/severity filters. Offline `?demo=1` mode.
- **Self-instrumentation:** `/actuator/prometheus` on every service with custom meters
  (`beacon_events_indexed_total`, `beacon_index_latency`, `beacon_scoring_latency`,
  `beacon_anomaly_backend_llm`, `beacon_alerts_fired_total`).
- **Hardening:** request validation + clean `400`s, body-size bounds, a per-IP rate limiter, and an
  opt-in CORS allowlist.
- **Testcontainers integration tests** (real Kafka + Elasticsearch) plus fast container-free unit
  tests, with JaCoCo coverage.
- **Docker & docker-compose:** one image per service, a full-stack `demo` profile, and Kibana.

---

## Architecture

```
              ┌──────────────┐        ┌─────────────────────┐        ┌────────────────────┐
producers ───▶│    Kafka     │───────▶│  ingest-service     │───────▶│   Elasticsearch    │
 (apps,       │ beacon.events│  JSON  │  (Spring Boot/Java) │  index │   beacon-events    │
  agents)     └──────────────┘        │  enrich + index     │        └─────────┬──────────┘
                    │  retry/DLT       │  RECORD ack + retry │                  │
                    ▼                  └─────────────────────┘        search &  │  read/update
             beacon.events.DLT                                        aggregate │  scores
                                       ┌─────────────────────┐                  │
                              GET  ──▶ │   query-api         │◀─────────────────┤
                           /search     │  (Spring Boot/      │──── serves ────▶ live dashboard (/)
                       /aggregations    │   Kotlin)          │                  │
                                       └─────────────────────┘                  │
                                                                                │
       ┌───────────────┐  rules + catalog  ┌─────────────────────┐  score+bulk  │
       │   Postgres    │◀─────────────────▶│  anomaly-worker     │◀─────────────┘
       │ catalog/rules │   write alerts    │  (Spring Boot/Java) │
       │    alerts     │                   │  Ollama ─or─ stats  │──▶ Slack / log  (alerts)
       └───────────────┘                   └─────────────────────┘
```

### Modules

| Module | Language | What it does |
| --- | --- | --- |
| `common` | Java | Shared domain records (`LogEvent`, `EnrichedEvent`) and `Severity` normalization. |
| `ingest-service` | Java / Spring Boot | Kafka consumer → enrichment → Elasticsearch indexer, with retry + DLT. Also a validated REST producer + stats. |
| `query-api` | Kotlin / Spring Boot | Full-text search, aggregations, and the static dashboard. |
| `anomaly-worker` | Java / Spring Boot | Scores unscored events (local LLM w/ statistical fallback), bulk-writes scores, and runs the Postgres-backed **alerting engine**. |

---

## Getting started

### Prerequisites

- **Docker + docker-compose** (easiest path), or JDK 17+ to run the services directly.
- *(optional)* [Ollama](https://ollama.com) running locally with a small model (e.g.
  `ollama pull llama3.2`) — the anomaly worker uses it automatically if reachable, otherwise it
  falls back to the statistical scorer.

### Option A — one command (recommended)

```bash
docker compose --profile demo up --build
```

This builds all three service images, starts Kafka + Elasticsearch + Postgres + Kibana, seeds a
realistic batch of events, and triggers an anomaly scan. Then open:

- **Dashboard:** http://localhost:8082
- Search: `curl 'http://localhost:8082/api/search?q=timeout'`
- Aggregations: `curl 'http://localhost:8082/api/aggregations'`
- Alerts: `curl 'http://localhost:8083/api/anomaly/alerts'`
- Kibana: http://localhost:5601

Bring up the stack **without** demo data by dropping `--profile demo`.

### Option B — infra in Docker, services from Gradle

```bash
docker compose up -d kafka elasticsearch postgres kibana
./gradlew :ingest-service:bootRun     # http://localhost:8081
./gradlew :query-api:bootRun          # http://localhost:8082  (dashboard at /)
./gradlew :anomaly-worker:bootRun     # http://localhost:8083

./scripts/demo-seed.sh                # seed events + trigger a scan
```

### Send a single event

```bash
curl -X POST http://localhost:8081/api/events \
  -H 'Content-Type: application/json' \
  -d '{"id":"demo-1","service":"orders-api-prod","level":"ERROR",
       "message":"database connection timeout","timestamp":"2026-01-01T10:00:00Z",
       "attributes":{"status":500,"latency_ms":5200}}'
```

Malformed input (blank `service`, unknown `level`, oversized body, …) is rejected with a clean
`400`; excess requests per client IP get a `429`.

---

## API reference

### `GET /api/search` · `GET /api/aggregations` (query-api, port 8082)

| Param | Type | Description |
| --- | --- | --- |
| `q` | string | Full-text query against the log `message` (search only). |
| `service` | string | Exact service name filter. |
| `environment` | string | `prod` / `staging` / `dev` / `unknown`. |
| `minSeverity` | int | Minimum numeric severity (DEBUG=1 … FATAL=5). |
| `from`, `to` | ISO-8601 | Inclusive event-time range. |
| `page`, `size` | int | Pagination (size clamped to 1–200; search only). |

Search returns `{ total, hits: [...] }`; aggregations return totals bucketed `byService`,
`bySeverity`, `byEnvironment`.

### `POST /api/anomaly/scan` · `GET /api/anomaly/status` · `GET /api/anomaly/alerts` (port 8083)

Runs / reports the scoring pass and active backend (`ollama` or `statistical`); lists recently fired
alerts (`?limit=N`).

### `POST /api/events` · `GET /api/stats` (ingest-service, port 8081)

Publishes a validated event onto Kafka and reports how many events have been indexed.

Every service exposes Spring Boot Actuator at `/actuator/health` and Prometheus metrics at
`/actuator/prometheus`.

---

## Alerting

The anomaly worker owns a small metadata store — embedded **H2** by default (zero config), or the
compose **Postgres** when `SPRING_DATASOURCE_URL` is set. It seeds a `service_catalog` and
`alert_rule` table. After each scoring pass, every scored event is evaluated against the enabled
rules; a breach (`anomaly_score >= min_anomaly_score` **and** `severity >= min_severity`) is written
to the `alert` table and delivered through a notifier:

- **Slack** when `SLACK_WEBHOOK_URL` is set (incoming webhook), else
- **logging** — always available, so alerting works with no external service.

Alert firing is idempotent per event id. Inspect fired alerts at `GET /api/anomaly/alerts`.

---

## Anomaly scoring

1. **Ollama (local LLM)** — if `http://localhost:11434` is reachable, each event is described to the
   model, which returns a 0–1 anomalousness rating. Runs entirely on your machine.
2. **Statistical fallback** — otherwise a deterministic scorer combines severity weight, high-signal
   keywords (`outofmemory`, `timeout`, `panic`, …), elevated `latency_ms`, and 5xx status codes.
   Fast, explainable, dependency-free.

Fully functional with **zero** external services and **no** paid keys; richer if you run Ollama.

---

## Dashboard

`query-api` serves a self-contained, dependency-free dashboard at **`/`**:

- events-over-time (with an error overlay), a severity donut, top-service and by-environment bars;
- an **anomaly feed** sorted by score with red→green heat colouring and score/severity filters;
- live auto-refresh against `/api/search` + `/api/aggregations`, and a **`?demo=1`** offline mode
  that replays an embedded dataset with no backend.

A standalone copy lives in [`dashboard/index.html`](dashboard/index.html) (opens straight from disk,
defaults to demo data; append `?live=1&api=http://localhost:8082` to point it at a running stack).

---

## Testing

```bash
./gradlew test              # fast, container-free unit tests
./gradlew integrationTest   # Testcontainers: real Kafka + Elasticsearch (needs Docker)
./gradlew build             # compile + unit tests + assemble
./gradlew printCoverage     # aggregate JaCoCo line coverage across all modules
```

Unit tests cover severity normalization, enrichment, JSON round-trips, the statistical scorer, the
Ollama parser/fallback, the **alerting engine** (full Spring context on H2: seed → evaluate →
persist → notify → read back), request validation and rate limiting, and query-API parameter
clamping. Integration tests spin up a real Elasticsearch (plus an in-JVM Kafka broker) and verify the
full ingest→index→search→aggregate→score→**alert** loop and the **dead-letter** path.

---

## Configuration

| Variable | Default | Used by |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | ingest-service |
| `ELASTICSEARCH_URI` | `http://localhost:9200` | all services |
| `BEACON_TOPIC` | `beacon.events` | ingest-service |
| `BEACON_RATELIMIT_PER_MINUTE` | `600` | ingest-service (0 disables) |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | anomaly-worker |
| `OLLAMA_MODEL` | `llama3.2` | anomaly-worker |
| `SPRING_DATASOURCE_URL` | embedded H2 | anomaly-worker (set to point at Postgres) |
| `SLACK_WEBHOOK_URL` | *(unset → log channel)* | anomaly-worker |
| `BEACON_CORS_ALLOWED_ORIGINS` | *(same-origin only)* | query-api |

> **Elasticsearch security.** The compose stack runs ES with `xpack.security.enabled=false` for a
> frictionless local run. To use a secured cluster, set `ELASTICSEARCH_URI` to an `https://` URL with
> credentials (`https://user:pass@host:9200`) — the client honours it.

---

## License

MIT © 2026 xj16 — see [LICENSE](LICENSE). See [CHANGELOG.md](CHANGELOG.md) for release history.
