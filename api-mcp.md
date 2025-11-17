# MCP API Catalog — Model Context Protocol

> **Version:** 1.0  
> **Base URL:** `https://api.mcp.platform/v1`  
> **Authentication:** Bearer token required in `Authorization` header

---

## 1. Business Rules Inventory APIs

Manage business-level rules that define organizational policies, constraints, and decision logic.

### `GET /business-rules`
List all business rules with pagination.

**Query Parameters:**
- `page` (int) — Page number (default: 1)
- `limit` (int) — Results per page (default: 50, max: 200)
- `domain` (string) — Filter by business domain
- `status` (string) — Filter by status: `active`, `draft`, `archived`

**Sample Request:**
```http
GET /business-rules?domain=finance&status=active&limit=20
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "data": [
    {
      "id": "br-001",
      "name": "Credit Limit Validation",
      "domain": "finance",
      "description": "Customer credit limit must not exceed $50,000 for new accounts",
      "owner": "finance-team@company.com",
      "status": "active",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-09-20T14:22:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 145
  }
}
```

---

### `GET /business-rules/{id}`
Retrieve a specific business rule by ID.

**Sample Request:**
```http
GET /business-rules/br-001
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "id": "br-001",
  "name": "Credit Limit Validation",
  "domain": "finance",
  "description": "Customer credit limit must not exceed $50,000 for new accounts",
  "logic": "IF customer.accountAge < 90 THEN creditLimit <= 50000",
  "owner": "finance-team@company.com",
  "severity": "high",
  "status": "active",
  "tags": ["credit", "risk", "validation"],
  "metadata": {
    "approvedBy": "john.doe@company.com",
    "lastReviewed": "2024-09-15"
  },
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-09-20T14:22:00Z"
}
```

---

### `POST /business-rules`
Create a new business rule.

**Sample Request:**
```json
{
  "name": "Invoice Payment Terms",
  "domain": "finance",
  "description": "Standard payment terms are net-30 for B2B customers",
  "logic": "IF customer.type == 'B2B' THEN paymentTerms = 'NET_30'",
  "owner": "finance-team@company.com",
  "severity": "medium",
  "tags": ["payment", "terms"]
}
```

**Sample Response:**
```json
{
  "id": "br-146",
  "name": "Invoice Payment Terms",
  "status": "draft",
  "createdAt": "2024-11-17T08:15:00Z"
}
```

---

### `PUT /business-rules/{id}`
Update an existing business rule.

**Sample Request:**
```json
{
  "description": "Updated: Standard payment terms are net-45 for premium B2B customers",
  "logic": "IF customer.type == 'B2B' AND customer.tier == 'premium' THEN paymentTerms = 'NET_45'",
  "status": "active"
}
```

**Sample Response:**
```json
{
  "id": "br-146",
  "updatedAt": "2024-11-17T09:00:00Z",
  "status": "active"
}
```

---

### `DELETE /business-rules/{id}`
Archive a business rule (soft delete).

**Sample Response:**
```json
{
  "id": "br-146",
  "status": "archived",
  "archivedAt": "2024-11-17T09:30:00Z"
}
```

---

## 2. Logical Rules Inventory APIs

Manage logical rules that represent abstract, implementation-agnostic rule definitions.

### `GET /logical-rules`
List all logical rules.

**Query Parameters:**
- `page` (int) — Page number
- `limit` (int) — Results per page
- `entityType` (string) — Filter by entity type
- `ruleType` (string) — Filter by type: `validation`, `transformation`, `calculation`

**Sample Request:**
```http
GET /logical-rules?entityType=customer&ruleType=validation
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "data": [
    {
      "id": "lr-501",
      "name": "Email Format Validation",
      "entityType": "customer",
      "ruleType": "validation",
      "expression": "REGEX_MATCH(email, '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$')",
      "owner": "data-team@company.com",
      "status": "active",
      "createdAt": "2023-08-10T12:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 78
  }
}
```

---

### `GET /logical-rules/{id}`
Retrieve a specific logical rule.

**Sample Response:**
```json
{
  "id": "lr-501",
  "name": "Email Format Validation",
  "entityType": "customer",
  "ruleType": "validation",
  "expression": "REGEX_MATCH(email, '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$')",
  "description": "Ensures customer email follows standard format",
  "owner": "data-team@company.com",
  "status": "active",
  "dependencies": [],
  "mappedTechnicalRules": ["tr-201", "tr-202"],
  "createdAt": "2023-08-10T12:00:00Z",
  "updatedAt": "2024-02-18T15:45:00Z"
}
```

---

### `POST /logical-rules`
Create a new logical rule.

**Sample Request:**
```json
{
  "name": "Phone Number Standardization",
  "entityType": "customer",
  "ruleType": "transformation",
  "expression": "NORMALIZE_PHONE(phoneNumber, 'E164')",
  "description": "Convert phone numbers to E.164 international format",
  "owner": "data-team@company.com"
}
```

**Sample Response:**
```json
{
  "id": "lr-579",
  "status": "draft",
  "createdAt": "2024-11-17T10:00:00Z"
}
```

---

### `PUT /logical-rules/{id}`
Update a logical rule.

**Sample Request:**
```json
{
  "expression": "NORMALIZE_PHONE(phoneNumber, 'E164', countryCode)",
  "status": "active"
}
```

---

### `DELETE /logical-rules/{id}`
Archive a logical rule.

---

## 3. Technical Rules Inventory APIs

Manage technical implementation rules tied to specific systems, databases, or platforms.

### `GET /technical-rules`
List all technical rules.

**Query Parameters:**
- `page` (int)
- `limit` (int)
- `platform` (string) — Filter by platform: `sql`, `spark`, `airflow`, `dbt`, etc.
- `system` (string) — Filter by system name

**Sample Request:**
```http
GET /technical-rules?platform=sql&system=warehouse
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "data": [
    {
      "id": "tr-201",
      "name": "Customer Email Validation Check",
      "platform": "sql",
      "system": "warehouse",
      "implementation": "CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$')",
      "logicalRuleId": "lr-501",
      "owner": "platform-team@company.com",
      "status": "active",
      "createdAt": "2023-09-05T14:20:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 234
  }
}
```

---

### `GET /technical-rules/{id}`
Retrieve a specific technical rule.

**Sample Response:**
```json
{
  "id": "tr-201",
  "name": "Customer Email Validation Check",
  "platform": "sql",
  "system": "warehouse",
  "implementation": "CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$')",
  "logicalRuleId": "lr-501",
  "businessRuleId": "br-023",
  "deployedAt": "warehouse.public.customers",
  "owner": "platform-team@company.com",
  "status": "active",
  "metadata": {
    "lastDeployed": "2024-10-05T08:30:00Z",
    "executionCount": 1245678,
    "failureRate": 0.02
  },
  "createdAt": "2023-09-05T14:20:00Z",
  "updatedAt": "2024-10-05T08:30:00Z"
}
```

---

### `POST /technical-rules`
Create a new technical rule.

**Sample Request:**
```json
{
  "name": "Order Total Calculation",
  "platform": "spark",
  "system": "data-pipeline",
  "implementation": "df.withColumn('total', col('quantity') * col('unit_price'))",
  "logicalRuleId": "lr-403",
  "owner": "data-eng@company.com"
}
```

---

### `PUT /technical-rules/{id}`
Update a technical rule.

---

### `DELETE /technical-rules/{id}`
Archive a technical rule.

---

## 4. Rule Search & Filtering APIs

Comprehensive search capabilities across all rule types.

> **💡 Note:** Search operates across business, logical, and technical rules simultaneously unless filtered by type.

---

### `GET /rules/search`
Simple keyword search across all rules.

**Query Parameters:**
- `q` (string, required) — Search query
- `type` (string) — Filter by type: `business`, `logical`, `technical`
- `limit` (int) — Results limit (default: 50)

**Sample Request:**
```http
GET /rules/search?q=credit%20limit&type=business&limit=10
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "query": "credit limit",
  "results": [
    {
      "id": "br-001",
      "type": "business",
      "name": "Credit Limit Validation",
      "description": "Customer credit limit must not exceed $50,000 for new accounts",
      "score": 0.95,
      "highlights": ["<em>Credit</em> <em>Limit</em> Validation"]
    }
  ],
  "total": 1,
  "took": 12
}
```

---

### `GET /rules/search/filtered`
Fielded/filtered search with multiple criteria.

**Query Parameters:**
- `domain` (string) — Business domain
- `owner` (string) — Rule owner email
- `status` (string) — Rule status
- `severity` (string) — Severity level
- `platform` (string) — Technical platform
- `entityType` (string) — Entity type for logical rules
- `tags` (string) — Comma-separated tags
- `createdAfter` (date) — ISO 8601 date
- `createdBefore` (date) — ISO 8601 date

**Sample Request:**
```http
GET /rules/search/filtered?domain=finance&severity=high&status=active&tags=credit,risk
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "filters": {
    "domain": "finance",
    "severity": "high",
    "status": "active",
    "tags": ["credit", "risk"]
  },
  "results": [
    {
      "id": "br-001",
      "type": "business",
      "name": "Credit Limit Validation",
      "domain": "finance",
      "severity": "high",
      "status": "active"
    }
  ],
  "total": 1
}
```

---

### `POST /rules/search`
Boolean/DSL search with advanced query structure.

**Sample Request:**
```json
{
  "query": {
    "bool": {
      "must": [
        { "match": { "domain": "finance" } }
      ],
      "should": [
        { "match": { "tags": "credit" } },
        { "match": { "tags": "payment" } }
      ],
      "must_not": [
        { "match": { "status": "archived" } }
      ],
      "filter": [
        { "range": { "createdAt": { "gte": "2024-01-01" } } }
      ]
    }
  },
  "size": 20,
  "from": 0
}
```

**Sample Response:**
```json
{
  "hits": {
    "total": 12,
    "results": [
      {
        "id": "br-001",
        "type": "business",
        "name": "Credit Limit Validation",
        "domain": "finance",
        "tags": ["credit", "risk"],
        "status": "active",
        "score": 2.34
      }
    ]
  },
  "took": 18
}
```

---

### `GET /rules/search/facets`
Faceted search returning aggregated counts.

**Query Parameters:**
- `q` (string) — Optional search query
- `facets` (string) — Comma-separated facet fields

**Sample Request:**
```http
GET /rules/search/facets?q=validation&facets=domain,severity,owner,status
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "query": "validation",
  "results": [...],
  "facets": {
    "domain": {
      "finance": 23,
      "operations": 15,
      "marketing": 8
    },
    "severity": {
      "high": 12,
      "medium": 28,
      "low": 6
    },
    "owner": {
      "finance-team@company.com": 18,
      "data-team@company.com": 14
    },
    "status": {
      "active": 38,
      "draft": 5,
      "archived": 3
    }
  },
  "total": 46
}
```

---

### `GET /rules/search/autocomplete`
Autocomplete suggestions for search queries.

**Query Parameters:**
- `q` (string, required) — Partial query (min 2 chars)
- `field` (string) — Specific field: `name`, `domain`, `owner`, `tags`

**Sample Request:**
```http
GET /rules/search/autocomplete?q=cred&field=name
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "query": "cred",
  "suggestions": [
    { "text": "Credit Limit Validation", "score": 0.92 },
    { "text": "Credit Score Calculation", "score": 0.88 },
    { "text": "Credit Card Processing", "score": 0.81 }
  ]
}
```

---

### `GET /rules/search/fuzzy`
Fuzzy/typo-tolerant search.

**Query Parameters:**
- `q` (string, required) — Search query
- `fuzziness` (string) — Fuzziness level: `AUTO`, `0`, `1`, `2` (default: `AUTO`)
- `type` (string) — Rule type filter

**Sample Request:**
```http
GET /rules/search/fuzzy?q=credti%20limt&fuzziness=AUTO
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "query": "credti limt",
  "corrected": "credit limit",
  "results": [
    {
      "id": "br-001",
      "type": "business",
      "name": "Credit Limit Validation",
      "score": 0.89
    }
  ],
  "total": 1
}
```

---

### `GET /rules/search/phrase`
Phrase and proximity search.

**Query Parameters:**
- `q` (string, required) — Phrase query (use quotes)
- `slop` (int) — Max word distance for proximity (default: 0)

**Sample Request:**
```http
GET /rules/search/phrase?q="customer%20credit"&slop=2
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "query": "customer credit",
  "slop": 2,
  "results": [
    {
      "id": "br-001",
      "type": "business",
      "name": "Credit Limit Validation",
      "description": "Customer credit limit must not exceed $50,000",
      "score": 0.94
    }
  ],
  "total": 1
}
```

---

### `GET /rules/search/pattern`
Regex and wildcard pattern search.

**Query Parameters:**
- `pattern` (string, required) — Regex or wildcard pattern
- `field` (string, required) — Field to search: `name`, `description`, `logic`
- `mode` (string) — `regex` or `wildcard` (default: `wildcard`)

**Sample Request:**
```http
GET /rules/search/pattern?pattern=cr*dit%20l?mit&field=name&mode=wildcard
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "pattern": "cr*dit l?mit",
  "mode": "wildcard",
  "field": "name",
  "results": [
    {
      "id": "br-001",
      "type": "business",
      "name": "Credit Limit Validation"
    }
  ],
  "total": 1
}
```

---

### `POST /rules/search/semantic`
Semantic/vector search for RAG and similarity matching.

**Sample Request:**
```json
{
  "query": "What are the rules about customer payment deadlines?",
  "embedding": [0.023, 0.891, -0.234, ...],
  "topK": 5,
  "filters": {
    "domain": "finance"
  }
}
```

> **💡 Note:** `embedding` can be omitted; the system will auto-generate embeddings from `query`.

**Sample Response:**
```json
{
  "query": "What are the rules about customer payment deadlines?",
  "results": [
    {
      "id": "br-146",
      "type": "business",
      "name": "Invoice Payment Terms",
      "description": "Standard payment terms are net-30 for B2B customers",
      "similarity": 0.91
    },
    {
      "id": "br-089",
      "type": "business",
      "name": "Late Payment Policy",
      "description": "Invoices unpaid after 45 days incur 1.5% monthly interest",
      "similarity": 0.87
    }
  ],
  "total": 5
}
```

---

### `POST /rules/search/similarity`
Find similar or duplicate rules.

**Sample Request:**
```json
{
  "ruleId": "br-001",
  "threshold": 0.75,
  "limit": 10
}
```

**Sample Response:**
```json
{
  "sourceRule": {
    "id": "br-001",
    "name": "Credit Limit Validation"
  },
  "similarRules": [
    {
      "id": "br-034",
      "type": "business",
      "name": "Credit Limit Check for New Accounts",
      "similarity": 0.92,
      "reason": "High overlap in logic and domain"
    },
    {
      "id": "lr-501",
      "type": "logical",
      "name": "Credit Validation Logic",
      "similarity": 0.78,
      "reason": "Related logical implementation"
    }
  ],
  "total": 2
}
```

---

## 5. Metrics & Monitoring APIs

Track rule performance, usage, and operational health.

### `GET /metrics/rules/{id}`
Get metrics for a specific rule.

**Query Parameters:**
- `startDate` (date) — Start date (ISO 8601)
- `endDate` (date) — End date (ISO 8601)

**Sample Request:**
```http
GET /metrics/rules/tr-201?startDate=2024-11-01&endDate=2024-11-17
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "ruleId": "tr-201",
  "ruleName": "Customer Email Validation Check",
  "period": {
    "start": "2024-11-01T00:00:00Z",
    "end": "2024-11-17T23:59:59Z"
  },
  "metrics": {
    "executionCount": 45678,
    "successCount": 44890,
    "failureCount": 788,
    "failureRate": 0.017,
    "avgExecutionTime": 12.4,
    "p95ExecutionTime": 23.1,
    "p99ExecutionTime": 45.6
  }
}
```

---

### `GET /metrics/summary`
Get aggregate metrics across all rules.

**Query Parameters:**
- `groupBy` (string) — Group by: `domain`, `type`, `platform`, `owner`
- `startDate` (date)
- `endDate` (date)

**Sample Request:**
```http
GET /metrics/summary?groupBy=domain&startDate=2024-11-01
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "period": {
    "start": "2024-11-01T00:00:00Z",
    "end": "2024-11-17T23:59:59Z"
  },
  "summary": [
    {
      "domain": "finance",
      "totalRules": 145,
      "activeRules": 132,
      "totalExecutions": 2345678,
      "avgFailureRate": 0.023
    },
    {
      "domain": "operations",
      "totalRules": 89,
      "activeRules": 81,
      "totalExecutions": 1234567,
      "avgFailureRate": 0.015
    }
  ]
}
```

---

### `GET /metrics/alerts`
Retrieve active rule alerts and anomalies.

**Sample Response:**
```json
{
  "alerts": [
    {
      "id": "alert-501",
      "ruleId": "tr-305",
      "ruleName": "Order Processing Rule",
      "severity": "high",
      "type": "high_failure_rate",
      "message": "Failure rate exceeded threshold: 15% (threshold: 5%)",
      "triggeredAt": "2024-11-17T08:45:00Z",
      "status": "open"
    }
  ],
  "total": 1
}
```

---

## 6. Mappings (Logical → Physical) APIs

Manage relationships between logical rules and their technical implementations.

### `GET /mappings`
List all rule mappings.

**Query Parameters:**
- `logicalRuleId` (string) — Filter by logical rule
- `technicalRuleId` (string) — Filter by technical rule
- `page` (int)
- `limit` (int)

**Sample Request:**
```http
GET /mappings?logicalRuleId=lr-501
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "data": [
    {
      "id": "map-001",
      "logicalRuleId": "lr-501",
      "logicalRuleName": "Email Format Validation",
      "technicalRuleId": "tr-201",
      "technicalRuleName": "Customer Email Validation Check",
      "platform": "sql",
      "mappingType": "one-to-one",
      "createdAt": "2023-09-05T14:30:00Z"
    },
    {
      "id": "map-002",
      "logicalRuleId": "lr-501",
      "technicalRuleId": "tr-202",
      "technicalRuleName": "Email Validation Spark Job",
      "platform": "spark",
      "mappingType": "one-to-many",
      "createdAt": "2023-10-12T09:15:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 2
  }
}
```

---

### `POST /mappings`
Create a new logical-to-technical mapping.

**Sample Request:**
```json
{
  "logicalRuleId": "lr-403",
  "technicalRuleId": "tr-560",
  "mappingType": "one-to-one",
  "notes": "Direct SQL implementation for warehouse"
}
```

**Sample Response:**
```json
{
  "id": "map-458",
  "logicalRuleId": "lr-403",
  "technicalRuleId": "tr-560",
  "createdAt": "2024-11-17T11:00:00Z"
}
```

---

### `DELETE /mappings/{id}`
Remove a mapping relationship.

---

## 7. Lineage & Impact APIs

Track data lineage and assess rule change impact.

### `GET /lineage/rules/{id}`
Get full lineage graph for a rule.

**Query Parameters:**
- `direction` (string) — `upstream`, `downstream`, or `both` (default: `both`)
- `depth` (int) — Max traversal depth (default: 3)

**Sample Request:**
```http
GET /lineage/rules/lr-501?direction=both&depth=2
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "ruleId": "lr-501",
  "ruleName": "Email Format Validation",
  "lineage": {
    "upstream": [
      {
        "id": "br-023",
        "type": "business",
        "name": "Customer Data Quality Standards",
        "relationship": "derives_from"
      }
    ],
    "downstream": [
      {
        "id": "tr-201",
        "type": "technical",
        "name": "Customer Email Validation Check",
        "platform": "sql",
        "relationship": "implements"
      },
      {
        "id": "tr-202",
        "type": "technical",
        "name": "Email Validation Spark Job",
        "platform": "spark",
        "relationship": "implements"
      }
    ]
  }
}
```

---

### `POST /impact/analyze`
Analyze impact of changing or removing a rule.

**Sample Request:**
```json
{
  "ruleId": "lr-501",
  "changeType": "delete"
}
```

**Sample Response:**
```json
{
  "ruleId": "lr-501",
  "ruleName": "Email Format Validation",
  "changeType": "delete",
  "impact": {
    "affectedRules": 2,
    "affectedSystems": ["warehouse", "data-pipeline"],
    "affectedConsumers": 3,
    "risk": "high",
    "details": [
      {
        "id": "tr-201",
        "type": "technical",
        "name": "Customer Email Validation Check",
        "impact": "This rule will become orphaned"
      },
      {
        "id": "consumer-05",
        "type": "subscription",
        "name": "CRM System Integration",
        "impact": "Validation will no longer occur"
      }
    ]
  }
}
```

---

### `GET /lineage/dependencies/{id}`
Get direct dependencies for a rule.

**Sample Response:**
```json
{
  "ruleId": "br-001",
  "dependencies": {
    "dependsOn": ["br-020", "lr-403"],
    "dependedBy": ["lr-501", "tr-201", "tr-305"]
  }
}
```

---

## 8. Consumer Subscription APIs

Manage external system subscriptions to rule events and changes.

### `GET /subscriptions`
List all consumer subscriptions.

**Query Parameters:**
- `consumer` (string) — Filter by consumer name
- `ruleId` (string) — Filter by rule
- `status` (string) — Filter by status: `active`, `paused`, `failed`

**Sample Request:**
```http
GET /subscriptions?status=active
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "data": [
    {
      "id": "sub-001",
      "consumer": "CRM System",
      "ruleId": "lr-501",
      "ruleName": "Email Format Validation",
      "eventTypes": ["rule.updated", "rule.executed"],
      "endpoint": "https://crm.company.com/webhooks/rules",
      "status": "active",
      "createdAt": "2024-05-10T10:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 1
  }
}
```

---

### `POST /subscriptions`
Create a new subscription.

**Sample Request:**
```json
{
  "consumer": "Analytics Platform",
  "ruleId": "br-001",
  "eventTypes": ["rule.updated", "rule.deleted"],
  "endpoint": "https://analytics.company.com/webhooks/rules",
  "authToken": "Bearer abc123xyz"
}
```

**Sample Response:**
```json
{
  "id": "sub-034",
  "consumer": "Analytics Platform",
  "ruleId": "br-001",
  "status": "active",
  "createdAt": "2024-11-17T12:00:00Z"
}
```

---

### `PUT /subscriptions/{id}`
Update a subscription.

**Sample Request:**
```json
{
  "status": "paused",
  "eventTypes": ["rule.updated"]
}
```

---

### `DELETE /subscriptions/{id}`
Delete a subscription.

---

### `GET /subscriptions/{id}/events`
Retrieve event delivery history for a subscription.

**Sample Response:**
```json
{
  "subscriptionId": "sub-001",
  "events": [
    {
      "id": "evt-5001",
      "eventType": "rule.updated",
      "ruleId": "lr-501",
      "deliveredAt": "2024-11-17T10:30:00Z",
      "status": "success",
      "responseCode": 200
    },
    {
      "id": "evt-5002",
      "eventType": "rule.executed",
      "ruleId": "lr-501",
      "deliveredAt": "2024-11-17T11:15:00Z",
      "status": "failed",
      "responseCode": 500,
      "retryCount": 2
    }
  ],
  "total": 2
}
```

---

## 9. Bulk Import / Export APIs

Import and export rules in bulk for migration, backup, or batch updates.

### `POST /import/rules`
Import rules from file (JSON, CSV, or YAML).

**Content-Type:** `multipart/form-data`

**Form Parameters:**
- `file` (file, required) — Rules file
- `format` (string) — File format: `json`, `csv`, `yaml`
- `mode` (string) — Import mode: `create`, `update`, `upsert` (default: `create`)
- `validateOnly` (boolean) — Dry-run validation without import

**Sample Request:**
```http
POST /import/rules
Authorization: Bearer <token>
Content-Type: multipart/form-data

file=@rules_export.json
format=json
mode=upsert
```

**Sample Response:**
```json
{
  "jobId": "import-job-789",
  "status": "processing",
  "submitted": 150,
  "stats": {
    "created": 0,
    "updated": 0,
    "failed": 0
  },
  "startedAt": "2024-11-17T13:00:00Z"
}
```

---

### `GET /import/jobs/{jobId}`
Check status of an import job.

**Sample Response:**
```json
{
  "jobId": "import-job-789",
  "status": "completed",
  "submitted": 150,
  "stats": {
    "created": 45,
    "updated": 98,
    "failed": 7
  },
  "errors": [
    {
      "row": 12,
      "error": "Missing required field: domain"
    },
    {
      "row": 34,
      "error": "Invalid rule type: 'custom'"
    }
  ],
  "startedAt": "2024-11-17T13:00:00Z",
  "completedAt": "2024-11-17T13:05:23Z"
}
```

---

### `POST /export/rules`
Export rules matching specified criteria.

**Sample Request:**
```json
{
  "filters": {
    "domain": "finance",
    "status": "active",
    "type": ["business", "logical"]
  },
  "format": "json",
  "includeMetadata": true,
  "includeMetrics": false
}
```

**Sample Response:**
```json
{
  "jobId": "export-job-456",
  "status": "processing",
  "estimatedCount": 145,
  "startedAt": "2024-11-17T13:10:00Z"
}
```

---

### `GET /export/jobs/{jobId}`
Check export job status and download link.

**Sample Response:**
```json
{
  "jobId": "export-job-456",
  "status": "completed",
  "format": "json",
  "recordCount": 145,
  "downloadUrl": "https://api.mcp.platform/v1/downloads/export-job-456.json",
  "expiresAt": "2024-11-18T13:15:00Z",
  "startedAt": "2024-11-17T13:10:00Z",
  "completedAt": "2024-11-17T13:15:00Z"
}
```

---

### `GET /downloads/{filename}`
Download exported file.

**Sample Request:**
```http
GET /downloads/export-job-456.json
Authorization: Bearer <token>
```

**Response:** File download (application/json, text/csv, or application/yaml)

---

### `POST /import/validate`
Validate import file without importing.

**Sample Request:**
```http
POST /import/validate
Authorization: Bearer <token>
Content-Type: multipart/form-data

file=@rules_import.csv
format=csv
```

**Sample Response:**
```json
{
  "valid": false,
  "totalRecords": 50,
  "validRecords": 43,
  "invalidRecords": 7,
  "errors": [
    {
      "row": 5,
      "field": "domain",
      "error": "Required field missing"
    },
    {
      "row": 12,
      "field": "severity",
      "error": "Invalid value 'critical'. Must be: low, medium, high"
    }
  ]
}
```

---

## 10. Governance & Workflow APIs

Manage approval workflows, compliance, and rule governance.

### `GET /governance/workflows`
List all governance workflows.

**Query Parameters:**
- `type` (string) — Workflow type: `approval`, `review`, `deprecation`
- `status` (string) — Workflow status: `pending`, `approved`, `rejected`

**Sample Request:**
```http
GET /governance/workflows?type=approval&status=pending
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "data": [
    {
      "id": "wf-001",
      "type": "approval",
      "ruleId": "br-146",
      "ruleName": "Invoice Payment Terms",
      "requestedBy": "jane.smith@company.com",
      "status": "pending",
      "currentApprover": "finance-lead@company.com",
      "createdAt": "2024-11-15T09:00:00Z",
      "dueDate": "2024-11-22T09:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 1
  }
}
```

---

### `POST /governance/workflows`
Create a new governance workflow.

**Sample Request:**
```json
{
  "type": "approval",
  "ruleId": "br-150",
  "requestedBy": "john.doe@company.com",
  "approvers": ["finance-lead@company.com", "compliance@company.com"],
  "reason": "New rule requires compliance review",
  "priority": "high"
}
```

**Sample Response:**
```json
{
  "id": "wf-045",
  "type": "approval",
  "ruleId": "br-150",
  "status": "pending",
  "createdAt": "2024-11-17T14:00:00Z"
}
```

---

### `PUT /governance/workflows/{id}/approve`
Approve a workflow.

**Sample Request:**
```json
{
  "approver": "finance-lead@company.com",
  "comments": "Approved pending minor documentation updates",
  "conditions": ["Update rule description", "Add compliance tags"]
}
```

**Sample Response:**
```json
{
  "id": "wf-001",
  "status": "approved",
  "approvedBy": "finance-lead@company.com",
  "approvedAt": "2024-11-17T14:30:00Z",
  "nextStep": "implementation"
}
```

---

### `PUT /governance/workflows/{id}/reject`
Reject a workflow.

**Sample Request:**
```json
{
  "approver": "compliance@company.com",
  "reason": "Conflicts with existing regulatory requirements",
  "comments": "See compliance policy CP-2024-03"
}
```

**Sample Response:**
```json
{
  "id": "wf-001",
  "status": "rejected",
  "rejectedBy": "compliance@company.com",
  "rejectedAt": "2024-11-17T14:45:00Z"
}
```

---

### `GET /governance/audit-log`
Retrieve governance audit trail.

**Query Parameters:**
- `ruleId` (string) — Filter by rule
- `action` (string) — Action type: `created`, `updated`, `deleted`, `approved`, `rejected`
- `userId` (string) — Filter by user
- `startDate` (date)
- `endDate` (date)

**Sample Request:**
```http
GET /governance/audit-log?ruleId=br-001&startDate=2024-11-01
Authorization: Bearer <token>
```

**Sample Response:**
```json
{
  "logs": [
    {
      "id": "audit-5001",
      "ruleId": "br-001",
      "ruleName": "Credit Limit Validation",
      "action": "updated",
      "userId": "jane.smith@company.com",
      "timestamp": "2024-11-10T10:30:00Z",
      "changes": {
        "field": "description",
        "oldValue": "Customer credit limit must not exceed $50,000",
        "newValue": "Customer credit limit must not exceed $50,000 for new accounts"
      },
      "ipAddress": "10.20.30.40"
    },
    {
      "id": "audit-5002",
      "ruleId": "br-001",
      "action": "approved",
      "userId": "finance-lead@company.com",
      "workflowId": "wf-023",
      "timestamp": "2024-11-11T14:15:00Z",
      "comments": "Approved for production deployment"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 2
  }
}
```

---

### `GET /governance/compliance/status`
Get compliance status summary.

**Sample Response:**
```json
{
  "summary": {
    "totalRules": 456,
    "compliant": 420,
    "nonCompliant": 28,
    "pendingReview": 8,
    "complianceRate": 0.92
  },
  "byDomain": [
    {
      "domain": "finance",
      "totalRules": 145,
      "compliant": 142,
      "nonCompliant": 3,
      "complianceRate": 0.98
    },
    {
      "domain": "operations",
      "totalRules": 89,
      "compliant": 82,
      "nonCompliant": 7,
      "complianceRate": 0.92
    }
  ],
  "issues": [
    {
      "ruleId": "br-089",
      "ruleName": "Late Payment Policy",
      "issue": "Missing required compliance tags",
      "severity": "medium"
    }
  ]
}
```

---

### `POST /governance/deprecation`
Mark a rule for deprecation.

**Sample Request:**
```json
{
  "ruleId": "br-034",
  "reason": "Superseded by br-150",
  "deprecationDate": "2024-12-31",
  "replacementRuleId": "br-150",
  "notifyConsumers": true
}
```

**Sample Response:**
```json
{
  "ruleId": "br-034",
  "status": "deprecated",
  "deprecationDate": "2024-12-31",
  "replacementRuleId": "br-150",
  "notificationsSent": 3,
  "deprecatedAt": "2024-11-17T15:00:00Z"
}
```

---

## Appendix: Common Response Codes

| Code | Status | Description |
|------|--------|-------------|
| 200 | OK | Request successful |
| 201 | Created | Resource created successfully |
| 400 | Bad Request | Invalid request parameters |
| 401 | Unauthorized | Missing or invalid authentication token |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource does not exist |
| 409 | Conflict | Resource conflict (e.g., duplicate) |
| 422 | Unprocessable Entity | Validation error |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |
| 503 | Service Unavailable | Service temporarily unavailable |

---

## Appendix: Common Headers

**Request Headers:**
```
Authorization: Bearer <token>
Content-Type: application/json
Accept: application/json
X-Request-ID: <unique-request-id>
```

**Response Headers:**
```
X-Request-ID: <unique-request-id>
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 998
X-RateLimit-Reset: 1700236800
```

---

*Last updated: November 17, 2024*
