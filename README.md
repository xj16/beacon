# Beacon

**A Kafka → Elasticsearch observability pipeline with a Kotlin query API and local-LLM anomaly detection.**

Beacon ingests a firehose of structured log/observability events from Kafka, enriches them, and
indexes them into Elasticsearch. A Kotlin service exposes ergonomic full-text search and
aggregations over that data, and a background worker scores every event for anomalousness — using
a **local** LLM (Ollama) when one is available and falling back to a fast, deterministic
statistical scorer when it is not. Everything runs on free, self-hostable infrastructure with no
paid API keys.

---

## Why

Most "observability" is just `grep` over log files until it isn't. Beacon is a compact but real
reference for the pattern teams actually reach for at scale:

- **decouple producers from storage** with Kafka, so a spike in log volume never takes down the
  services emitting the logs;
- **enrich on the way in** (normalized severity, environment tagging, idempotent ids) so the data
  is queryable, not just stored;
- **make it searchable** with full-text queries and aggregations instead of raw index dumps;
- **surface the needles**, not the haystack, by scoring events for anomalousness — with an LLM if
  you have one, and a solid heuristic scorer if you don't.

It is intentionally small enough to read in one sitting, but every piece does something real.

---

## Architecture

```
              ┌──────────────┐        ┌─────────────────────┐        ┌────────────────────┐
producers ───▶│    Kafka     │───────▶│  ingest-service     │───────▶│   Elasticsearch    │
 (apps,       │ beacon.events│  JSON  │  (Spring Boot/Java) │  index │   beacon-events    │
  agents)     └──────────────┘        │  enrich + index     │        └─────────┬──────────┘
                                      └─────────────────────┘                  │
                                                                     search &  │  read/update
                                                                     aggregate │  scores
                                      ┌─────────────────────┐                  │
                              GET  ──▶│   query-api         │◀─────────────────┤
                           /search    │  (Spring Boot/      │                  │
                       /aggregations  │   Kotlin)           │                  │
                                      └─────────────────────┘                  │
                                                                               │
                                      ┌─────────────────────┐   score & write  │
                                      │  anomaly-worker     │◀─────────────────┘
                                      │  (Spring Boot/Java) │
                                      │  Ollama ─or─ stats  │
                                      └─────────────────────┘

  Postgres stores service catalog + alert rules (operational metadata, not event data).
```

### Modules

| Module | Language | What it does |
| --- | --- | --- |
| `common` | Java | Shared domain records (`LogEvent`, `EnrichedEvent`) and `Severity` normalization. |
| `ingest-service` | Java / Spring Boot | Kafka consumer → enrichment → Elasticsearch indexer. Also a REST producer + stats endpoint. |
| `query-api` | Kotlin / Spring Boot | Full-text search and terms aggregations over the index. |
| `anomaly-worker` | Java / Spring Boot | Scheduled worker that scores unscored events (local LLM with statistical fallback) and writes the score back. |

---

## Features

- **Kafka ingestion** with a JSON-deserialized `LogEvent` and automatic topic creation.
- **Enrichment**: textual level → numeric `severity`, coarse `environment` bucket derived from
  naming conventions, ingest timestamp, and idempotent document ids (safe on re-delivery).
- **Elasticsearch indexing** with an explicit mapping (full-text `message` plus a `keyword`
  sub-field, keyword-typed dimensions, date fields) created automatically on startup.
- **Full-text search** (`GET /api/search`) with optional filters: free-text query on the message,
  `service`, `environment`, `minSeverity`, and a `from`/`to` time range, newest-first, paginated.
- **Aggregations** (`GET /api/aggregations`): event counts bucketed by service, severity, and
  environment, honoring the same filters.
- **Anomaly detection** that prefers a **local Ollama model** and degrades gracefully to a
  deterministic heuristic scorer (severity + keyword + latency + 5xx signals) — no paid keys, ever.
- **Testcontainers integration tests** spinning up real Kafka and Elasticsearch, plus fast
  container-free unit tests.
- **docker-compose** stack: Kafka (KRaft, no ZooKeeper), Elasticsearch, Postgres, and Kibana.
- **CI** that compiles, unit-tests, assembles boot jars, and runs the Testcontainers suite.

---

## Tech stack

- **Java 17** and **Kotlin 1.9** on the JVM
- **Spring Boot 3.3** (web, actuator, validation, scheduling)
- **Spring Kafka** for the consumer/producer
- **Apache Kafka** (KRaft mode) as the event bus
- **Elasticsearch 8.14** with the official Elasticsearch Java client
- **PostgreSQL 16** for service/alert metadata
- **Ollama** (optional) as the local LLM backend for anomaly scoring
- **Gradle 8.10** (Kotlin DSL, multi-module) with the Gradle wrapper
- **Testcontainers** for integration tests
- **Docker / docker-compose** for local infrastructure
- **GitHub Actions** for CI

---

## Getting started

### Prerequisites

- JDK 17+
- Docker + docker-compose (for the local infrastructure and integration tests)
- *(optional)* [Ollama](https://ollama.com) running locally with a small model pulled, e.g.
  `ollama pull llama3.2` — the anomaly worker uses it automatically if reachable.

### 1. Start the infrastructure

```bash
docker compose up -d
# Kafka          -> localhost:9092
# Elasticsearch  -> localhost:9200
# Postgres       -> localhost:5432  (db/user/pass: beacon/beacon/beacon)
# Kibana         -> localhost:5601
```

### 2. Run the services

Each service is an independent Spring Boot app. In three terminals:

```bash
./gradlew :ingest-service:bootRun     # http://localhost:8081
./gradlew :query-api:bootRun          # http://localhost:8082
./gradlew :anomaly-worker:bootRun     # http://localhost:8083
```

### 3. Send some events and query them

```bash
# Publish a few sample events through the ingest service (which puts them on Kafka).
./scripts/seed-events.sh

# Full-text search for events mentioning "timeout":
curl 'http://localhost:8082/api/search?q=timeout'

# Only production errors (severity >= 4) from a specific service:
curl 'http://localhost:8082/api/search?service=orders-api-prod&minSeverity=4'

# Aggregations across the whole index:
curl 'http://localhost:8082/api/aggregations'

# Trigger an immediate anomaly-scoring pass and see which backend was used:
curl -X POST http://localhost:8083/api/anomaly/scan
curl http://localhost:8083/api/anomaly/status
```

You can also publish a single event directly:

```bash
curl -X POST http://localhost:8081/api/events \
  -H 'Content-Type: application/json' \
  -d '{"id":"demo-1","service":"orders-api-prod","level":"ERROR",
       "message":"database connection timeout","timestamp":"2026-01-01T10:00:00Z",
       "attributes":{"status":500,"latency_ms":5200}}'
```

---

## API reference

### `GET /api/search` (query-api, port 8082)

| Param | Type | Description |
| --- | --- | --- |
| `q` | string | Full-text query against the log `message`. |
| `service` | string | Exact service name filter. |
| `environment` | string | `prod` / `staging` / `dev` / `unknown`. |
| `minSeverity` | int | Minimum numeric severity (DEBUG=1 … FATAL=5). |
| `from`, `to` | ISO-8601 | Inclusive event-time range. |
| `page`, `size` | int | Pagination (size clamped to 1–200). |

Returns `{ total, hits: [...] }`.

### `GET /api/aggregations` (query-api, port 8082)

Same filters as search (minus `q`). Returns totals bucketed `byService`, `bySeverity`, and
`byEnvironment`.

### `POST /api/anomaly/scan` · `GET /api/anomaly/status` (anomaly-worker, port 8083)

Runs / reports the scoring pass and the active backend (`ollama` or `statistical`).

### `POST /api/events` · `GET /api/stats` (ingest-service, port 8081)

Publishes an event onto Kafka and reports how many events have been indexed.

Each service also exposes Spring Boot Actuator at `/actuator/health`.

---

## Anomaly scoring

The worker periodically finds events with no `anomaly_score`, scores them, and writes the score
back. The scoring backend is chosen automatically:

1. **Ollama (local LLM)** — if `http://localhost:11434` is reachable, each event is described to
   the model, which returns a 0–1 anomalousness rating. Runs entirely on your machine.
2. **Statistical fallback** — otherwise a deterministic scorer combines severity weight,
   high-signal keywords (`outofmemory`, `timeout`, `panic`, …), elevated `latency_ms`, and 5xx
   status codes. Fast, explainable, and dependency-free.

This is the project's "local model with graceful fallback" contract: it is fully functional with
**zero** external services and **no** paid keys, and gets richer if you happen to run Ollama.

---

## Testing

```bash
./gradlew test              # fast, container-free unit tests
./gradlew integrationTest   # Testcontainers: real Kafka + Elasticsearch (needs Docker)
./gradlew build             # compile + unit tests + assemble
```

Unit tests cover severity normalization, enrichment, the statistical scorer, and the Ollama
reply parser/fallback. Integration tests spin up real Kafka and Elasticsearch and verify the full
ingest→index→search→aggregate→score loop.

---

## Configuration

All services read configuration from environment variables (with sensible localhost defaults):

| Variable | Default | Used by |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | ingest-service |
| `ELASTICSEARCH_URI` | `http://localhost:9200` | all services |
| `BEACON_TOPIC` | `beacon.events` | ingest-service |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | anomaly-worker |
| `OLLAMA_MODEL` | `llama3.2` | anomaly-worker |

---

## License

MIT © 2026 xj16 — see [LICENSE](LICENSE).
