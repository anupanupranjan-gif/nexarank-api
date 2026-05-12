# NexaRank API

NexaRank is an open-source merchandising rules engine for Elasticsearch-based eCommerce search. It lets merchandising teams create and manage PIN, BOOST, BURY, and SYNONYM rules without touching search infrastructure or filing engineering tickets.

## Why NexaRank?

Most merchandising tools are locked to a specific search vendor (Algolia, Constructor, Bloomreach). NexaRank is platform-agnostic — it runs on your own Elasticsearch cluster and integrates with any search API.

## Features

- Four rule types: PIN, BOOST, BURY, SYNONYM
- REST API for full rule lifecycle management
- Designed to integrate with any Elasticsearch-based search pipeline
- Lightweight Spring Boot service, easy to deploy anywhere
- Apache 2.0 licensed — free to use and modify

## Rule Types

| Type | What it does |
|------|-------------|
| PIN | Forces specific product IDs to the top of results for a query |
| BOOST | Increases relevance score for products matching a field/value |
| BURY | Demotes products matching a field/value |
| SYNONYM | Expands a query term to include equivalent terms |

## Tech Stack

- Java 25, Spring Boot 3.4.5
- Spring Data Elasticsearch 5.x
- Elasticsearch 8.x (ECK)

## Quick Start

```bash
# Prerequisites: Java 25, Maven, Elasticsearch 8.x running

git clone https://github.com/anupanupranjan-gif/nexarank-api.git
cd nexarank-api

# Set environment variables
export ES_URI=https://localhost:9200
export ES_USERNAME=elastic
export ES_PASSWORD=yourpassword

mvn clean package -DskipTests
java -jar target/nexarank-api-*.jar
```

## API Reference
GET    /api/v1/rules                      List all rules
POST   /api/v1/rules                      Create a rule
GET    /api/v1/rules/query/{query}        Get rules matching a specific query keyword
PUT    /api/v1/rules/{id}                 Update a rule
DELETE /api/v1/rules/{id}                 Delete a rule
PATCH  /api/v1/rules/{id}/toggle          Toggle a rule enabled/disabled
### Example: Create a BOOST rule

```bash
curl -X POST http://localhost:8080/api/v1/rules \
  -H "Content-Type: application/json" \
  -d '{
    "type": "BOOST",
    "query": "battery",
    "boostField": "category",
    "boostValue": "Automotive",
    "boostFactor": 1.5,
    "enabled": true
  }'
```

### Example: Create a PIN rule

```bash
curl -X POST http://localhost:8080/api/v1/rules \
  -H "Content-Type: application/json" \
  -d '{
    "type": "PIN",
    "query": "oil filter",
    "pinnedIds": ["SKU-001", "SKU-002"],
    "enabled": true
  }'
```

### Example: Toggle a rule

```bash
curl -X PATCH http://localhost:8080/api/v1/rules/{id}/toggle
```

### Example: Get rules for a query

```bash
curl http://localhost:8080/api/v1/rules/query/battery
```

## Roadmap

- [ ] Authentication and role-based access (Merchandiser, Approver, Admin)
- [ ] Rule approval workflow (DRAFT → PENDING_REVIEW → APPROVED)
- [ ] Rule scheduling (activate/expire by date)
- [ ] Rule priority ordering and conflict detection
- [ ] Rule preview (simulate results before activating)
- [ ] Clickstream feedback loop (rule performance metrics)
- [ ] AI-suggested rules from zero-result query analysis
- [ ] Semantic rule matching via vector search
- [ ] Multi-tenant namespace support

## License

Copyright (c) 2026 Anup Ranjan. Licensed under the Apache License 2.0.
See [LICENSE](LICENSE) for details.
