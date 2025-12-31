# Observability Local (IntelliJ on host + Docker)

This setup lets you run microservices in IntelliJ (host) while Prometheus/Loki/Tempo/Grafana run in Docker.

## Traces (Tempo)
Use the OpenTelemetry Java Agent in IntelliJ run configurations:

- VM options:
  - `-javaagent:/path/opentelemetry-javaagent.jar`
- Env vars:
  - `OTEL_SERVICE_NAME=ms-order` (adjust per service)
  - `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317`
  - `OTEL_EXPORTER_OTLP_PROTOCOL=grpc`
  - `OTEL_TRACES_EXPORTER=otlp`
  - `OTEL_METRICS_EXPORTER=none`
  - `OTEL_LOGS_EXPORTER=none`
  - `OTEL_RESOURCE_ATTRIBUTES=deployment.environment=local`

## Logs (Loki)
Services write JSON logs to files and Promtail (Docker) ships them to Loki.

- Default directory: `../logs` (relative to each service module).
- Override: `RETAIL_LOGS_DIR=/absolute/path/to/logs`.
- Promtail reads `/var/log/retail-store/*.json`.

Loki labels: `job`, `service`, `env`, `level` (no `order_id` label).

## Metrics (Prometheus)
Prometheus runs with `network_mode: host` and scrapes `localhost:809x`.

Exporters:
- `localhost:9187` (postgres_exporter)
- `localhost:9308` (kafka_exporter)

Grafana reaches Prometheus via `http://host.docker.internal:9090`.

## Validation
Run:

```bash
./scripts/validate-observability.sh
```

## Loki Queries (order timeline)
Examples:

- All logs for an order:
  - `{job="retail-store"} | json | order_id="YOUR_ORDER_ID"`
- Only errors for an order:
  - `{job="retail-store"} | json | order_id="YOUR_ORDER_ID" | level="ERROR"`
- Saga step filter:
  - `{job="retail-store"} | json | service="ms-checkout-orchestrator" | saga_step="WAIT_PAYMENT_CAPTURE"`
