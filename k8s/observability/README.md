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
| `dashboards/configmap.yaml` | Provisions both dashboards into Grafana via the kube-prometheus-stack sidecar (`grafana_dashboard: "1"` label) — see below. |

**⚠ 2026-08-19 fix — hardcoded datasource UIDs (recurring fragility):** both
dashboard JSON files used to hardcode `"uid": "loki"` / `"uid": "prometheus"`.
That's exactly the failure mode documented in `SearchX Platform — Build
Journal v18` ("stale datasource entries... 10 custom dashboards pointing at
dead uids") — a fresh Loki/Prometheus install assigns a different auto-
generated UID and the panels silently break. Both dashboards now use proper
Grafana template datasource variables (`${DS_LOKI}` / `${DS_PROMETHEUS}`,
declared in each dashboard's own `templating.list`) instead, which
Grafana resolves to whichever datasource of that type exists — resilient
across rebuilds regardless of what UID gets assigned.

## Apply

```sh
kubectl label svc nexarank-api app=nexarank-api -n default --overwrite
kubectl apply -f servicemonitor.yaml
kubectl apply -f prometheusrule-slo.yaml
kubectl apply -f dashboards/configmap.yaml
```

Dashboards are now provisioned declaratively (ConfigMap + sidecar) rather
than manually imported via the Grafana UI/API — the old manual-import step
was itself a form of the "lost on rebuild" fragility this directory exists
to avoid.

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
