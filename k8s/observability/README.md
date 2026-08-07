<!-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0) -->
# nexarank-api observability

Prometheus/Grafana wiring for nexarank-api. nexarank-api is deployed with
`kubectl` (not ArgoCD), so these are applied manually.

## What's here

| File | Purpose |
|---|---|
| `servicemonitor.yaml` | Makes Prometheus scrape `/actuator/prometheus`. Requires the Service to carry `app: nexarank-api`. |
| `prometheusrule-slo.yaml` | SLO alerts: 99.9% enrich availability + rules-only p99 < 50ms latency. |
| `dashboards/nexarank-slo.json` | Grafana "NexaRank SLO Dashboard" (RED + SLO). |
| `dashboards/cfmw9rjz38irkf.json` | Grafana "NexaRank Audit Log" (Loki, log-based). |

## Apply

```sh
kubectl label svc nexarank-api app=nexarank-api -n default --overwrite
kubectl apply -f servicemonitor.yaml
kubectl apply -f prometheusrule-slo.yaml
# dashboards: import the JSON via Grafana UI or API (they are stored in
# Grafana's own DB, not provisioned from a ConfigMap in this cluster).
```

The app side is already wired: `micrometer-registry-prometheus` on the
classpath, `management.endpoints.web.exposure.include` contains
`prometheus`, and `/actuator/prometheus` is `permitAll` in `SecurityConfig`.
The rules-only latency SLI comes from the custom `nexarank_enrich_rules`
timer in `QueryPipelineOrchestrator` (wraps the RULE_APPLICATION group
only — excludes LLM stages).

## Loki tuning note (audit-log dashboard)

The "NexaRank Audit Log" dashboard is Loki log-based; every audit event
reaches Loki because `AuditService` emits one structured
`AUDIT action=… entity=… id=… actor=…` line per event.

The shared single-binary Loki (`loki-stack` in `monitoring`) shipped with
defaults that reject concurrent multi-panel dashboards with
`too many outstanding requests`. Fixed in the `loki-stack` secret
(`loki.yaml`) — this benefits every Loki dashboard in the cluster, not
just this one:

```yaml
limits_config:
  split_queries_by_interval: 0   # don't fan one range query into N subqueries
frontend:
  max_outstanding_per_tenant: 4096
query_scheduler:
  max_outstanding_requests_per_tenant: 4096
```

Since `loki-stack` is Helm-managed, re-apply this if a `helm upgrade`
reverts it, then restart `loki-stack-0`.
