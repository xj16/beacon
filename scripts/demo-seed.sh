#!/usr/bin/env sh
#
# Demo seeder: waits for the Beacon services, publishes a realistic batch of observability events
# across several services / severities / environments, then triggers an anomaly-scoring pass so the
# dashboard and /api/anomaly/alerts are alive immediately. Pure POSIX sh + curl so it runs in the
# tiny curlimages/curl container used by the `demo` compose profile.
#
# Also usable from a host shell:  INGEST_URL=http://localhost:8081 ./scripts/demo-seed.sh
set -eu

INGEST_URL="${INGEST_URL:-http://localhost:8081}"
QUERY_URL="${QUERY_URL:-http://localhost:8082}"
ANOMALY_URL="${ANOMALY_URL:-http://localhost:8083}"

wait_for() {
  name="$1"; url="$2"; tries=60
  printf 'waiting for %s ' "$name"
  while [ "$tries" -gt 0 ]; do
    if curl -sf "$url" >/dev/null 2>&1; then echo "ok"; return 0; fi
    printf '.'; tries=$((tries-1)); sleep 2
  done
  echo "timed out"; return 1
}

post() {
  curl -s -o /dev/null -X POST "$INGEST_URL/api/events" \
    -H 'Content-Type: application/json' -d "$1"
}

wait_for "ingest-service" "$INGEST_URL/actuator/health"

echo "seeding events -> $INGEST_URL"

# A spread of normal traffic, warnings, and genuine incidents across prod/staging/dev so the charts,
# aggregations, and anomaly feed all have something interesting to show.
post '{"id":"d-1","service":"orders-api-prod","level":"INFO","message":"order 8891 placed successfully","host":"pod-prod-1","attributes":{"status":200,"latency_ms":42}}'
post '{"id":"d-2","service":"orders-api-prod","level":"ERROR","message":"database connection timeout after 5s","host":"pod-prod-1","attributes":{"status":500,"latency_ms":5200}}'
post '{"id":"d-3","service":"checkout-api-prod","level":"FATAL","message":"OutOfMemoryError: Java heap space","host":"pod-prod-2","attributes":{"status":500,"latency_ms":120}}'
post '{"id":"d-4","service":"checkout-api-prod","level":"INFO","message":"payment captured for order 8891","host":"pod-prod-2","attributes":{"status":200,"latency_ms":88}}'
post '{"id":"d-5","service":"payments-api-prod","level":"ERROR","message":"upstream gateway returned 503","host":"pod-prod-3","attributes":{"status":503,"latency_ms":2100}}'
post '{"id":"d-6","service":"payments-api-prod","level":"WARN","message":"retrying charge, attempt 2","host":"pod-prod-3","attributes":{"status":200,"latency_ms":1400}}'
post '{"id":"d-7","service":"search-api-staging","level":"WARN","message":"query latency degraded, retrying","host":"pod-staging-1","attributes":{"status":200,"latency_ms":1800}}'
post '{"id":"d-8","service":"search-api-staging","level":"INFO","message":"reindex completed","host":"pod-staging-1","attributes":{"status":200,"latency_ms":300}}'
post '{"id":"d-9","service":"inventory-api-prod","level":"ERROR","message":"deadlock detected, transaction aborted","host":"pod-prod-4","attributes":{"status":500,"latency_ms":900}}'
post '{"id":"d-10","service":"inventory-api-prod","level":"INFO","message":"stock level synced","host":"pod-prod-4","attributes":{"status":200,"latency_ms":55}}'
post '{"id":"d-11","service":"auth-api-dev","level":"DEBUG","message":"token refreshed","host":"laptop-dev","attributes":{"status":200,"latency_ms":12}}'
post '{"id":"d-12","service":"auth-api-dev","level":"WARN","message":"rate limit approaching for client","host":"laptop-dev","attributes":{"status":429,"latency_ms":30}}'
post '{"id":"d-13","service":"orders-api-prod","level":"FATAL","message":"panic: nil pointer dereference in checkout","host":"pod-prod-1","attributes":{"status":500,"latency_ms":70}}'
post '{"id":"d-14","service":"orders-api-prod","level":"INFO","message":"health check ok","host":"pod-prod-1","attributes":{"status":200,"latency_ms":8}}'
post '{"id":"d-15","service":"payments-api-prod","level":"ERROR","message":"connection refused to fraud-service","host":"pod-prod-3","attributes":{"status":500,"latency_ms":3200}}'

echo "published 15 events"

# Let the ingest consumer index them, then trigger scoring + alert evaluation.
sleep 4
if curl -sf "$ANOMALY_URL/actuator/health" >/dev/null 2>&1; then
  echo "triggering anomaly scan..."
  curl -s -X POST "$ANOMALY_URL/api/anomaly/scan" || true
  echo
  echo "alerts:"
  curl -s "$ANOMALY_URL/api/anomaly/alerts?limit=10" || true
  echo
fi

echo
echo "Done. Open the dashboard:  $QUERY_URL/"
echo "Try:   curl '$QUERY_URL/api/search?q=timeout'"
echo "       curl '$QUERY_URL/api/aggregations'"
echo "       curl '$ANOMALY_URL/api/anomaly/alerts'"
