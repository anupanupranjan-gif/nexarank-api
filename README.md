# NexaRank

**NexaRank is a context-aware headless CMS and merchandising control plane.** Unlike generic headless CMS platforms that deliver static content, NexaRank evaluates real-time page context, search signals, and business rules to return the right content for the right moment. The open source alternative to Endeca Experience Manager.

## What Makes NexaRank Different from Generic Headless CMS

Standard headless CMS platforms (Strapi, Contentful, Directus) are content-first. Editors fill in content, the API delivers it. Simple but context-blind.

NexaRank is rules-first and context-aware. It evaluates *which* content to deliver based on the real-time context of the request:

- A category page at 3pm on a Tuesday gets a different banner than the homepage on Black Friday
- A customer searching for "running shoes" gets a different promotional spotlight than someone browsing Home & Kitchen
- Time-window rules, category context, query match, and customer segment all influence what content is returned

This contextual intelligence is what Endeca Experience Manager had and what generic headless CMS platforms fundamentally lack.

The same rules-first engine also drives search merchandising — PIN/BOOST/BURY/SYNONYM/REDIRECT rules with priority-based conflict resolution — so content decisions and search relevance decisions run through one control plane, one approval workflow, and one audit trail instead of two disconnected systems.

## Two Control Planes, One Platform

### Search Merchandising (MerchRule)

| Rule Type | What it does |
|-----------|---------------|
| BOOST | Increases relevance score for products matching a field/value |
| BURY | Demotes products matching a field/value |
| PIN | Forces specific product IDs to the top of results for a query |
| SYNONYM | Expands a query term to include equivalent terms (one-way or two-way) |
| REDIRECT | Sends a matching query straight to a landing page instead of results |

- **Priority-based conflict resolution** — when multiple LIVE rules would produce a conflicting instruction on the same target (e.g. two BOOST rules on the same field/value, or a BOOST and a BURY on it), the higher-priority rule wins; non-conflicting instructions from other rules still apply. Ties break on trigger specificity, then creation time, then rule id.
- **Duplicate trigger warning** — a soft, non-blocking warning when a new rule's trigger conditions and type exactly match an existing LIVE rule, so accidental duplicates get caught without blocking intentional overrides (e.g. a temporary seasonal rule outranking a standing one).
- **Trigger conditions** — facet-based AND/OR conditions (category, brand, price range, boolean flags) scope a rule beyond just its query text.
- **A/B testing** — route a percentage of sessions to a variant rule, track impressions.
- **Rule performance analytics** — fired count, last-fired time, click-through rate and revenue impact (directional, query-level attribution) per rule.
- **Zero-result query action loop** — the Analytics dashboard surfaces zero-result queries, one click creates a rule pre-populated with an LLM-suggested type and synonyms, and the dashboard shows whether it actually resolved the gap.
- **Query pipeline** — spell correction, stopword handling, LLM-based query rewrite, hybrid/vector search support — all engine-agnostic (Elasticsearch and Solr adapters ship today).

### Experience Manager (ContentRule)

| Zone | Typical use |
|------|-------------|
| HERO_BANNER | Homepage hero image/headline/CTA |
| ANNOUNCEMENT_BAR | Site-wide top-of-page bar |
| CATEGORY_BANNER | Banner above a specific category's results |
| PROMO_GRID | Multi-item promotional grid (up to 4 items) |
| CATEGORY_SPOTLIGHT | Featured category callout |
| FEATURED_PRODUCTS | Curated product spotlight |

- Content rules match on **page context** (page type, category, query, customer segment, device type) rather than search facets — a different targeting model from merchandising rules, sharing the same AND/OR condition logic.
- Same maker-checker approval workflow as merchandising rules (DRAFT → PENDING_REVIEW → ACTIVE, reject bounces to DRAFT with a comment), plus soft-delete and version history.
- Time-window scheduling (`scheduleStart`/`scheduleEnd`), explicit numeric priority (higher wins when multiple rules match the same zone).
- Single `POST /api/v1/content/enrich` endpoint resolves the highest-priority matching rule per requested zone — one call from the storefront, typed clients available (see SDKs below).

Both control planes share the same approval workflow shape, the same tenant/project scoping, and the same audit trail — a merchandiser and a content editor use the same mental model either way.

## Roles

| Role | Dashboard | Create Rules | Approve Rules | User Mgmt |
|------|-----------|--------------|----------------|-----------|
| STAKEHOLDER | No (email digests only) | No | No | No |
| VIEWER | Read-only | No | No | No |
| MERCHANDISER | Yes | Yes | No | No |
| APPROVER | Yes | Yes | Yes | No |
| ADMIN / TENANT_ADMIN | Yes | Yes | Yes | Yes |

Multi-tenant by design — every rule, facet config, and content rule is scoped by tenant and project.

## SDKs

- **nexarank-java-sdk** — Spring Boot auto-configured client for the search query pipeline (`enrich()`) and Experience Manager (`ContentEnrichClient`).
- **nexarank-content-sdk** — TypeScript client for Experience Manager, with a `useNexarankContent` React hook, TTL caching, and fail-open error handling so a NexaRank outage never breaks a storefront render.

Both fail open (empty result, no exception) on network error or timeout — content and merchandising are enhancements to a page, never a hard dependency for it to render.

## Tech Stack

- Java 25, Spring Boot 3.4.5
- PostgreSQL (rules, content rules, users, audit trail) with Flyway migrations
- Redis (cache-invalidation signaling only — search-api's result cache keys off a lightweight version counter, not a query cache in front of Postgres)
- Elasticsearch 8.x and Apache Solr adapters (engine-agnostic by design — the pipeline never touches search results directly, it only produces instructions)
- Ollama (dev) / Claude API (production) behind an `LlmPort` abstraction for query rewrite and AI rule suggestions

## Quick Start

```bash
# Prerequisites: Java 25, Maven, PostgreSQL, Redis, and (for LLM-backed
# features — query rewrite, AI rule suggestions) an Ollama or Claude API endpoint

git clone https://github.com/anupanupranjan-gif/nexarank-api.git
cd nexarank-api

export DB_URL=jdbc:postgresql://localhost:5432/nexarank
export DB_USERNAME=nexarank
export DB_PASSWORD=yourpassword
export REDIS_HOST=localhost
export JWT_SECRET=change-me-minimum-32-characters
export OLLAMA_BASE_URL=http://localhost:11434

mvn clean package -DskipTests
java -jar target/nexarank-api-*.jar
```

## API Reference

```
POST   /api/v1/auth/login                        Authenticate, get a JWT

# Search merchandising
GET    /api/v1/rules                             List all rules
POST   /api/v1/rules                             Create a rule
GET    /api/v1/rules/query/{query}               Rules matching a query
PUT    /api/v1/rules/{id}                        Update a rule
PATCH  /api/v1/rules/{id}/submit                  DRAFT -> PENDING_REVIEW
PATCH  /api/v1/rules/{id}/approve                 PENDING_REVIEW -> APPROVED (or LIVE)
PATCH  /api/v1/rules/{id}/promote                 APPROVED -> LIVE
PATCH  /api/v1/rules/{id}/demote                  LIVE -> APPROVED/DRAFT
PATCH  /api/v1/rules/{id}/reject                  Reject with a comment
DELETE /api/v1/rules/{id}                        Delete a rule
GET    /api/v1/rules/enrich?query=...            Resolve instructions for a query
POST   /api/v1/rules/preview                     Preview conflicts/duplicates before saving

# Experience Manager
GET    /api/v1/content-rules                     List content rules (zone/status filter)
POST   /api/v1/content-rules                     Create a content rule
PATCH  /api/v1/content-rules/{id}/submit          DRAFT -> PENDING_REVIEW
PATCH  /api/v1/content-rules/{id}/approve         PENDING_REVIEW -> ACTIVE
POST   /api/v1/content/enrich                     Resolve content for requested zones (public)
```

### Example: Create a BOOST rule

```bash
curl -X POST http://localhost:8080/api/v1/rules \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "type": "BOOST",
    "query": "battery",
    "priority": 60,
    "boostField": "category",
    "boostValue": "Automotive",
    "boostFactor": 1.5
  }'
```

### Example: Resolve content for a homepage request

```bash
curl -X POST http://localhost:8080/api/v1/content/enrich \
  -H "Content-Type: application/json" \
  -d '{
    "zones": ["HERO_BANNER", "ANNOUNCEMENT_BAR"],
    "context": { "pageType": "homepage" }
  }'
```

## License

Copyright (c) 2026 Anup Ranjan. Licensed under the Apache License 2.0.
See [LICENSE](LICENSE) for details.
