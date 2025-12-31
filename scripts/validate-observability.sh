#!/usr/bin/env bash
set -euo pipefail

echo "== Retail Store Observability Validation =="

SERVICES=(
  "ms-order:8091"
  "ms-invoice:8092"
  "ms-notification:8093"
  "ms-payment:8094"
  "ms-shipping:8095"
  "ms-customer:8096"
  "ms-checkout-orchestrator:8097"
  "ms-inventory:8098"
)

echo
echo "1) Checking Docker containers (Grafana/Loki/Tempo/Promtail/Prometheus)..."
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | egrep 'grafana|loki|tempo|promtail|prometheus' || {
  echo "ERROR: Observability containers not found/running."
  exit 1
}

echo
echo "2) Checking Grafana/Loki/Tempo/Prometheus health endpoints..."
echo -n "Grafana : "
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3000/login
echo -n "Loki    : "
curl -s http://localhost:3100/ready || true
echo
echo -n "Tempo   : "
curl -s http://localhost:3200/ready || true
echo
echo -n "Prometheus: "
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9090/-/ready

echo
echo "3) Checking Spring Actuator on HOST (only for services running in IntelliJ)..."
for s in "${SERVICES[@]}"; do
  name="${s%%:*}"
  port="${s##*:}"
  code="$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${port}/actuator/health" || true)"
  if [[ "$code" == "200" ]]; then
    echo "OK ${name} health on :${port}"
  else
    echo "SKIP ${name} health on :${port} (HTTP $code) - probably not running"
  fi
done

echo
echo "4) Checking /actuator/prometheus on HOST (must be 200 when service is running)..."
for s in "${SERVICES[@]}"; do
  name="${s%%:*}"
  port="${s##*:}"
  code="$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${port}/actuator/prometheus" || true)"
  if [[ "$code" == "200" ]]; then
    echo "OK ${name} prometheus endpoint on :${port}"
  else
    echo "SKIP ${name} prometheus endpoint on :${port} (HTTP $code)"
  fi
done

echo
echo "5) Checking Prometheus targets page is reachable (manual check recommended too)..."
curl -s -o /dev/null -w "Prometheus targets HTTP %{http_code}\n" http://localhost:9090/targets

echo
echo "6) Checking that log files exist on HOST (repo-root/logs/*.json)..."
if [[ -d "./logs" ]]; then
  ls -lah ./logs | sed -n '1,40p'
  count="$(ls -1 ./logs/*.json 2>/dev/null | wc -l | tr -d ' ' || true)"
  if [[ "$count" -ge 1 ]]; then
    echo "OK Found ${count} JSON log file(s) in ./logs"
  else
    echo "WARN No *.json logs found in ./logs yet (start a service + ensure logback writes to file)."
  fi
else
  echo "ERROR ./logs directory not found."
  exit 1
fi

echo
echo "7) Checking Promtail can see the mounted logs directory..."
PROMTAIL_CONTAINER="$(docker ps --format '{{.Names}}' | grep -E 'promtail' | head -n 1 || true)"
if [[ -z "${PROMTAIL_CONTAINER}" ]]; then
  echo "ERROR promtail container not found."
  exit 1
fi
echo "Promtail container: ${PROMTAIL_CONTAINER}"
docker exec "${PROMTAIL_CONTAINER}" sh -lc 'ls -lah /var/log/retail-store | sed -n "1,40p"' || {
  echo "ERROR promtail cannot access /var/log/retail-store (mount missing?)"
  exit 1
}

echo
echo "8) Basic Loki query check (via Loki HTTP API)..."
LOKI_QUERY_URL="http://localhost:3100/loki/api/v1/query?query=%7Bjob%3D%22retail-store%22%7D"
code="$(curl -s -o /dev/null -w "%{http_code}" "${LOKI_QUERY_URL}" || true)"
echo "Loki query HTTP $code (200 means Loki is responding)"
if [[ "$code" != "200" ]]; then
  echo "WARN Loki query not OK. Check Loki container logs and promtail config."
fi

echo
echo "9) Trace pipeline sanity check:"
echo "Start/trigger any request in a running service (HTTP or Kafka), then:"
echo "- open Grafana -> Explore -> Loki -> query: {job=\"retail-store\"} | json"
echo "- find a log line with trace_id and click TraceID to open Tempo trace."
echo
echo "DONE"
