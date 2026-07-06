#!/usr/bin/env bash
#
# Publishes a handful of sample events to the ingest service so you can immediately
# try the query API and anomaly worker against real data.
#
# Usage: ./scripts/seed-events.sh [INGEST_URL]
#   INGEST_URL defaults to http://localhost:8081
set -euo pipefail

INGEST_URL="${1:-http://localhost:8081}"

post() {
  curl -s -X POST "$INGEST_URL/api/events" \
    -H 'Content-Type: application/json' \
    -d "$1" > /dev/null
  echo "  published: $(echo "$1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])' 2>/dev/null || echo ok)"
}

echo "Seeding events -> $INGEST_URL"

post '{"id":"evt-1","service":"orders-api-prod","level":"ERROR","message":"database connection timeout after 5s","timestamp":"2026-01-01T10:00:00Z","host":"pod-prod-1","attributes":{"status":500,"latency_ms":5200}}'
post '{"id":"evt-2","service":"orders-api-prod","level":"INFO","message":"order 8891 placed successfully","timestamp":"2026-01-01T10:00:05Z","host":"pod-prod-1","attributes":{"status":200,"latency_ms":42}}'
post '{"id":"evt-3","service":"checkout-api-prod","level":"FATAL","message":"OutOfMemoryError: Java heap space","timestamp":"2026-01-01T10:00:10Z","host":"pod-prod-2","attributes":{"status":500,"latency_ms":100}}'
post '{"id":"evt-4","service":"search-api-staging","level":"WARN","message":"query latency degraded, retrying","timestamp":"2026-01-01T10:00:15Z","host":"pod-staging-1","attributes":{"status":200,"latency_ms":1800}}'
post '{"id":"evt-5","service":"checkout-api-prod","level":"INFO","message":"payment captured for order 8891","timestamp":"2026-01-01T10:00:20Z","host":"pod-prod-2","attributes":{"status":200,"latency_ms":88}}'

echo "Done. Try:"
echo "  curl 'http://localhost:8082/api/search?q=timeout'"
echo "  curl 'http://localhost:8082/api/aggregations'"
echo "  curl -X POST http://localhost:8083/api/anomaly/scan"
