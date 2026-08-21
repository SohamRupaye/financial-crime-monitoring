# Financial Crime Transaction Monitoring System

A backend system for **Anti-Money Laundering (AML) transaction monitoring**, built with
Java 17 and Spring Boot 4.

The system models how a financial institution ingests transactions, evaluates them against
configurable AML rules, calculates an explainable risk score, raises alerts, and hands them
to an analyst for investigation.

> **Disclaimer:** This project uses entirely synthetic transaction and customer data for
> educational and demonstration purposes. It is not financial, regulatory, or compliance
> advice, and the elevated-risk country list is illustrative rather than an official
> classification.

---

## Status

This is a project in progress, built feature by feature. The table below is the honest
state of the code — everything marked *Planned* is designed but not yet written.

| Capability | Status | Notes |
|---|---|---|
| Customer management | ✅ Built | Create, fetch, list with paging and risk-level filter |
| Account management | ✅ Built | Open an account, fetch by number, list per customer |
| Transaction ingestion | 📋 Planned | Section 2 |
| AML rules engine | 📋 Planned | Sections 4–5 — the core of the project |
| Risk scoring | 📋 Planned | Section 6 |
| Explainable assessments | 📋 Planned | Section 7 |
| Alerts | 📋 Planned | Section 8 |
| Investigation cases | 📋 Planned | Section 9 |
| Synthetic data generator | 📋 Planned | Section 3 |
| REST API | 🚧 In progress | Customers and accounts so far |
| Schema and migrations | 🚧 In progress | Flyway V1–V2: `customers`, `accounts` |
| Testing | 🚧 In progress | Three-layer suite for customers and accounts |
| Docker | ✅ Built | Dev and production compose stacks |
| Authentication | 📋 Planned | Section 12 — **the API is currently open** |
| Redis caching | 📋 Planned | Section 14 |
| Event-driven processing | 📋 Planned | Section 15 |
| Kubernetes / CI | 📋 Planned | Section 24 |

---

## Overview

Financial institutions process large volumes of transactions every day. A transaction
monitoring system exists to surface the small fraction of that activity which warrants a
human look.

This project models that pipeline:

```text
                    ┌───────────────────────┐
                    │   Transaction Source  │
                    │  Synthetic / REST API │
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │ Transaction Ingestion │
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │    AML Rules Engine   │
                    │                       │
                    │ • Amount thresholds   │
                    │ • Velocity checks     │
                    │ • Structuring         │
                    │ • Customer risk       │
                    │ • Country risk        │
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │    Risk Assessment    │
                    │                       │
                    │  Score + Explanation  │
                    └───────────┬───────────┘
                                │
                  ┌─────────────┴─────────────┐
                  ▼                           ▼
             Low / Medium                High / Critical
                  │                           │
                  ▼                           ▼
              Persist                   Generate Alert
                                              │
                                              ▼
                                      Investigation Case
                                              │
                                              ▼
                                        Analyst Review
```

Today the left-hand half of that diagram exists as far as persistence. The rules engine and
everything downstream of it is the work in progress.

---

## 1. Customer and Account Management

> ✅ Built.

Customer profiles are the reference data the rules engine reads when scoring a transaction.

A customer carries:

* A `CUST-` business reference, used in URLs instead of the primary key
* Name, email, date of birth
* ISO 3166-1 alpha-2 country code
* A standing risk level
* Audit timestamps

```json
{
  "customerReference": "CUST-3F2A9C41",
  "firstName": "Asha",
  "lastName": "Menon",
  "countryCode": "IN",
  "riskLevel": "MEDIUM"
}
```

Risk level is one of:

```text
LOW
MEDIUM
HIGH
CRITICAL
```

It is **server-assigned** — every customer starts at `LOW` and is re-rated as their
transactions are assessed. A client cannot hand itself a rating, which is why
`CreateCustomerRequest` has no field for it.

Each customer holds one or more accounts, mapped many-to-one with lazy fetching:

```json
{
  "accountNumber": "ACC-9B41C7E20D5A",
  "customerReference": "CUST-3F2A9C41",
  "accountType": "SAVINGS",
  "currency": "INR",
  "balance": 0.0000,
  "status": "ACTIVE"
}
```

Accounts open at zero balance with `ACTIVE` status, both set by the server. Balances are
`NUMERIC(19,4)` mapped to `BigDecimal`; floating point cannot represent `0.1` exactly, which
would quietly corrupt every threshold comparison the rules engine makes.

---

## 2. Transaction Management

> 📋 Planned.

Transactions represent financial activity on a monitored account.

```json
{
  "transactionReference": "TXN-93842A1C",
  "accountNumber": "ACC-9B41C7E20D5A",
  "transactionType": "TRANSFER",
  "amount": 485000.0000,
  "currency": "INR",
  "counterpartyAccountNumber": "ACC-EXTERNAL-8841",
  "counterpartyCountry": "IN",
  "occurredAt": "2026-08-11T14:32:00Z"
}
```

Transaction types:

```text
TRANSFER
CASH_DEPOSIT
CASH_WITHDRAWAL
CARD_PAYMENT
INTERNATIONAL_TRANSFER
```

Note that `occurredAt` is separate from the inherited `createdAt`. One is when the money
moved, the other is when we learned about it, and they are not the same instant in any real
institution. Every rule time window is measured on `occurredAt`.

Transactions will be:

* Created through the REST API or the synthetic generator
* Retrieved by reference
* Filtered by account, type, amount range, date range and risk level
* Evaluated by the AML rules engine

---

## 3. Synthetic Transaction Generator

> 📋 Planned.

No real financial data is used anywhere in this project.

A generator produces datasets containing both ordinary activity and the patterns the rules
are meant to catch.

Normal activity:

```text
₹2,000
₹4,500
₹8,200
₹3,700
```

A structuring pattern, within a short window:

```text
₹490,000
₹490,000
₹495,000
₹480,000
```

Controllable inputs: customer count, transaction count, amount ranges, frequency, countries,
customer risk mix, and which suspicious patterns to inject. This is what lets the whole
monitoring workflow be demonstrated without an external data source.

Intended entry point:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--seed-data=true"
```

---

## 4. AML Rules Engine

> 📋 Planned. This is the core of the project.

The engine evaluates a transaction against a collection of independent rules and combines
their output into a single assessment.

Each rule reports:

```text
Triggered / Not Triggered
Risk points
Human-readable reason
```

```text
Transaction
     │
     ├── Large Amount Rule ──────── +25
     ├── Velocity Rule ──────────── +20
     ├── Structuring Rule ───────── +30
     └── Customer Risk Rule ─────── +15
                                  ----
                                   90
```

The design constraint that matters: **a rule never touches a repository.** Each rule receives
a context object holding the transaction, the customer, and a narrow history port. That keeps
every rule unit-testable as plain JUnit with no Spring context and no database, and it means
adding a sixth rule requires no change to the engine.

---

## 5. AML Rules

> 📋 Planned. All thresholds below are configuration, not constants in code.

### Large Transaction Rule

```text
IF amount > configured threshold
THEN +25 risk points
```

Default threshold ₹500,000.

### Transaction Velocity Rule

```text
IF the account has more than N transactions
WITHIN a configured window
THEN +20 risk points
```

Catches bursts of activity that are individually unremarkable.

### Structuring Detection

Structuring is splitting one large transfer into several smaller ones to stay under a
reporting threshold.

```text
₹490,000
₹490,000
₹480,000
₹495,000
```

instead of:

```text
₹1,955,000
```

The rule looks for several transactions on the same account, each individually *below* the
large-amount threshold but above a floor, inside one time window, together summing to more
than that threshold. Two transactions are not a pattern; the minimum count is configurable
and defaults to three.

```text
+30 risk points
```

### Customer Risk Rule

The customer's standing rating feeds into every transaction they make.

```text
LOW      → +0
MEDIUM   → +10
HIGH     → +20
CRITICAL → +30
```

### Country Risk Rule

```text
IF the counterparty country is in the configured elevated-risk list
THEN +20 risk points
```

The list is configuration and is illustrative only. It is **not** an official regulatory
classification, and nothing here should be read as a claim about any jurisdiction.

---

## 6. Risk Scoring

> 📋 Planned.

Points from every triggered rule are summed, then capped at 100 — the five rules can total
125, and a score that runs off the end of its own scale is not a score.

```text
Large transaction       +25
Velocity anomaly        +20
Customer risk           +20
Country risk            +20
                         ---
Total                    85
```

Bands, all configurable:

```text
 0 – 29   LOW
30 – 59   MEDIUM
60 – 79   HIGH
80 +      CRITICAL
```

---

## 7. Explainable Risk Assessment

> 📋 Planned.

A score on its own is useless to an analyst who has to justify a decision. Every assessment
carries the reasons that produced it.

```json
{
  "transactionReference": "TXN-93842A1C",
  "score": 85,
  "level": "CRITICAL",
  "reasons": [
    "Amount 485000.00 INR exceeded the 500000.00 threshold",
    "12 transactions in the preceding 10 minutes exceeded the limit of 10",
    "Customer risk rating is HIGH",
    "Counterparty country requires additional scrutiny"
  ]
}
```

Every rule result is persisted, including the ones that did not trigger. Knowing which rules
looked and stayed quiet is what makes false-positive tuning possible later.

---

## 8. Alert Management

> 📋 Planned.

An assessment at or above the configured alert threshold raises an alert.

```text
Risk score >= 60
        │
        ▼
     ALERT
        │
        ├── Transaction details
        ├── Risk score
        ├── Triggered rules
        └── Customer information
```

Alert statuses:

```text
OPEN
ACKNOWLEDGED
INVESTIGATING
RESOLVED
FALSE_POSITIVE
```

Transitions are validated rather than free-form: an alert cannot jump from `OPEN` straight to
`RESOLVED`, and nothing moves out of a terminal state. An illegal transition is a `409`, not a
silent write.

---

## 9. Investigation Case Management

> 📋 Planned.

An alert can be promoted to an investigation case, where an analyst can review the
transaction and customer, read previous activity and triggered rules, add notes, reassign,
change status, and either resolve it or mark it a false positive.

```text
Alert
  ↓
Open
  ↓
Acknowledged
  ↓
Investigating
  ↓
┌─────────────────┐
│                 │
▼                 ▼
Resolved      False Positive
```

---

## 10. Transaction History and Search

> 🚧 Paging and filtering exist for customers; transaction search is planned.

Collections are always paginated. An unbounded `findAll` over a transaction-scale table is
how an API takes down its own database.

```text
GET /api/v1/customers?page=0&size=20&sort=lastName,asc
GET /api/v1/customers?riskLevel=HIGH
```

Planned transaction filters: account, type, amount range, date range, risk level, country,
alert status.

---

## 11. REST API

> 🚧 Customer and account endpoints live. The rest arrive with their features.

```text
✅  GET    /api/v1/customers
✅  GET    /api/v1/customers/{customerReference}
✅  POST   /api/v1/customers

✅  POST   /api/v1/customers/{customerReference}/accounts
✅  GET    /api/v1/customers/{customerReference}/accounts
✅  GET    /api/v1/accounts/{accountNumber}

📋  POST   /api/v1/transactions
📋  GET    /api/v1/transactions
📋  GET    /api/v1/transactions/{transactionReference}
📋  POST   /api/v1/transactions/{transactionReference}/evaluate
📋  GET    /api/v1/transactions/{transactionReference}/assessment

📋  GET    /api/v1/alerts
📋  GET    /api/v1/alerts/{alertReference}
📋  PATCH  /api/v1/alerts/{alertReference}/status

📋  GET    /api/v1/cases
📋  POST   /api/v1/cases
📋  PATCH  /api/v1/cases/{caseReference}
```

Conventions in place:

* JSON request and response bodies, versioned under `/api/v1`
* Bean Validation on every request DTO
* Errors as RFC 9457 `application/problem+json`, from one `@RestControllerAdvice`
* `201 Created` with a `Location` header on creation
* Paging and sorting resolved straight from query parameters

---

## 12. Authentication and Authorization

> 📋 Planned. **The API is currently unauthenticated.**

`SecurityConfig` exists and deliberately permits `/api/**`, because there is no `User` entity
yet and requiring authentication with no way to authenticate would leave the service
unusable. Anything not explicitly listed already defaults to denied.

Planned roles:

```text
ADMIN
ANALYST
VIEWER
```

| Operation            | ADMIN | ANALYST | VIEWER |
|----------------------|------:|--------:|-------:|
| View transactions    |     ✓ |       ✓ |      ✓ |
| Evaluate transaction |     ✓ |       ✓ |      ✗ |
| Manage alerts        |     ✓ |       ✓ |   Read |
| Manage cases         |     ✓ |       ✓ |   Read |
| Configure rules      |     ✓ |       ✗ |      ✗ |
| Manage users         |     ✓ |       ✗ |      ✗ |

Intended implementation: Spring Security with JWT bearer tokens and method-level
authorization.

---

## 13. Database Design

> 🚧 `customers` and `accounts` exist. The rest follow their features.

PostgreSQL is the primary store. Flyway owns the schema; Hibernate runs with
`ddl-auto=validate`, so a mapping that disagrees with the migrations fails at startup rather
than drifting silently.

```text
Customer
   │
   ├───────────────┐
   │               │
   ▼               ▼
Account        Transaction
                    │
                    ▼
              RiskAssessment
                    │
                    ├── RiskRuleResult
                    │
                    ▼
                  Alert
                    │
                    ▼
                   Case
```

| Table | Migration |
|---|---|
| `customers` | ✅ V1 |
| `accounts` | ✅ V2 |
| `transactions` | 📋 V3 |
| `risk_assessments`, `risk_rule_results` | 📋 V4 |
| `alerts` | 📋 V5 |
| `investigation_cases`, `case_notes`, `users` | 📋 later |

Migrations are immutable once applied — Flyway checksums them, so a schema change means a new
`V<n>`, never an edit to an old file.

Indexes are added for the access patterns that actually exist, not speculatively. The
composite index on `(account_id, occurred_at)` matters most: it is what the velocity and
structuring lookbacks depend on.

---

## 14. Redis Caching

> 📋 Planned.

Candidates for caching once there is measurable load: customer risk profiles, rule
configuration, and dashboard aggregates.

```text
Request
   ↓
Redis
   │
   ├── Cache hit → Return
   │
   └── Cache miss
           ↓
       PostgreSQL
           ↓
       Update cache
```

Rule configuration is the interesting one, because a cached threshold that outlives a
configuration change silently scores transactions against the wrong number. Any cache here
needs an explicit expiry and invalidation story, not just a TTL.

---

## 15. Event-Driven Processing

> 📋 Planned, and genuinely optional.

Evaluation is synchronous today: ingesting a transaction scores it in the same request. That
is the right starting point — it is simpler, and it makes the whole pipeline observable in a
single `curl`.

At higher volume, ingestion and scoring want to scale separately:

```text
Transaction API
      │
      ▼
Message Broker
      │
      ▼
Transaction Processor
      │
      ▼
AML Rules Engine
      │
      ▼
Risk Assessment
      │
      ├── Normal → Persist
      │
      └── Suspicious → Alert Service
```

This will only appear in the repository if it is actually finished, not as a stub.

---

## 16. Testing

> 🚧 Three layers in place for customers and accounts. Every new feature ships with its own
> tests.

```text
Unit          plain JUnit + Mockito, no Spring context
Slice         @DataJpaTest / @WebMvcTest
Integration   @SpringBootTest against Testcontainers PostgreSQL
```

Repository and integration tests run against a real PostgreSQL 17 container, the same major
version as the compose stacks, rather than an embedded database that would accept SQL
Postgres rejects.

Priority test areas as features land:

* Individual AML rules, especially threshold and time-window boundaries
* Structuring and velocity detection
* Score aggregation and band boundaries
* Alert generation and status transitions
* Request validation and error responses

---

## 17. Docker

> ✅ Built.

Two stacks, for two different jobs.

**Development** — Postgres only. The application is deliberately *not* containerised, so it
runs from your IDE with hot reload and a debugger attached.

```bash
cd docker/development
docker compose up -d
```

**Production** — application plus Postgres, with the database unpublished and reachable only
over the internal network, a multi-stage build, a non-root container user, and a healthcheck
against `/actuator/health`.

```bash
cd docker/production
cp .env.example .env    # fill in POSTGRES_PASSWORD
docker compose up -d --build
```

---

## 18. Technology Stack

### Built

| | |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1 — Web MVC, Data JPA, Validation, Security, Actuator |
| Persistence | PostgreSQL 17, Hibernate, Flyway |
| Testing | JUnit 5, Mockito, AssertJ, Spring Boot Test, Testcontainers |
| Build | Maven |
| Runtime | Docker, Docker Compose |

### Planned

| | |
|---|---|
| Caching | Redis |
| Messaging | Kafka or RabbitMQ |
| Auth | Spring Security JWT |
| API docs | OpenAPI / Swagger UI |
| Deployment | Kubernetes |
| CI | GitHub Actions |

---

## 19. Project Architecture

```text
Controller      HTTP only — no business logic, no repositories
    │
    ▼
Service         business rules, transaction boundaries
    │
    ▼
Domain / Rules  pure logic, no framework
    │
    ▼
Repository      Spring Data interfaces
    │
    ▼
Database
```

Where the rules engine will sit:

```text
TransactionController
        │
        ▼
TransactionService
        │
        ├── TransactionRepository
        │
        └── RiskAssessmentService
                    │
                    ▼
              AmlRulesEngine
                    │
          ┌─────────┼─────────┐
          ▼         ▼         ▼
       Amount    Velocity   Structuring
        Rule       Rule        Rule
```

Rules are discovered by injecting `List<AmlRule>`, so the engine never names them
individually and a new rule is a new class plus its tests — nothing else.

---

## 20. Design Principles

* Entities are database rows; DTOs are the API contract. The two never mix, and the request
  DTO doubles as the write allowlist
* Constructor injection with `final` fields — no field `@Autowired`
* Business logic in services, not controllers; pure logic in the domain, not services
* Strategy pattern for AML rules, so the engine is closed to modification
* Centralised exception handling, so controllers contain no `try`/`catch`
* Business rules configurable, not hardcoded
* Every risk decision explainable
* `BigDecimal` for money, compared with `compareTo`
* No interface until there is a second implementation

---

## 21. Assumptions and Limitations

Worth stating plainly, because each of these is a real simplification:

* **Single currency for thresholds.** Rule thresholds are compared directly against the
  transaction amount, with no FX normalisation. A real system converts to a base currency
  first, using the rate at the transaction date.
* **Synchronous evaluation.** Ingestion scores the transaction in-request. Fine at
  demonstration volume, wrong at production volume — see section 15.
* **Point-based scoring, not statistical.** Fixed weights chosen by hand. Real monitoring
  tunes thresholds against historical alert outcomes, and increasingly supplements rules with
  behavioural baselining.
* **Customer risk is not yet dynamic.** The rating exists and is read by the rules, but
  nothing writes it back after an assessment.
* **No authentication.** See section 12.
* **Elevated-risk countries are configuration, not a regulatory list.**
* **Synthetic data only**, generated locally.

---

## 22. Getting Started

### Prerequisites

* Java 17 or later
* Docker and Docker Compose
* Maven (or just use the bundled `./mvnw`)

```bash
java -version
docker compose version
```

### Clone

```bash
git clone https://github.com/<username>/financial-crime-monitoring.git
cd financial-crime-monitoring
```

### Start the database

```bash
cd docker/development
docker compose up -d
cd ../..
```

Defaults are database `fcm`, user `fcm`, password `fcm` on port 5432, matching
`src/main/resources/application.properties`. To change any of them, copy
`docker/development/.env.example` to `.env` and edit it. `.env` files are gitignored — never
commit credentials.

### Run

```bash
./mvnw spring-boot:run
```

Flyway applies the migrations on startup. Health check:

```bash
curl -s http://localhost:8080/actuator/health
```

### Try it

```bash
curl -i -X POST http://localhost:8080/api/v1/customers \
  -H 'Content-Type: application/json' \
  -d '{
        "firstName": "Asha",
        "lastName": "Menon",
        "email": "asha.menon@example.com",
        "dateOfBirth": "1990-05-17",
        "countryCode": "IN"
      }'

curl -s 'http://localhost:8080/api/v1/customers?size=5'
```

### Tests

```bash
./mvnw test          # Docker must be running — Testcontainers needs it
./mvnw clean package
```

---

## 23. API Documentation

> 📋 Planned.

OpenAPI generation with a Swagger UI at `/swagger-ui.html`. Until then, section 11 is the
endpoint reference and the request DTOs are the schema.

---

## 24. Roadmap

Next, in order:

1. Transaction ingestion
2. The AML rules engine and the five rules
3. Risk scoring and explainable assessments
4. Alerts with a validated status workflow

Later, in rough priority order:

* Investigation cases with notes and assignment
* Synthetic data generator
* OpenAPI documentation
* JWT authentication and role-based authorization
* Redis caching for rule configuration and risk profiles
* Kafka-based asynchronous ingestion
* GitHub Actions CI
* Customer behavioural profiles and dynamic re-rating
* Graph analysis of counterparty networks
* Prometheus and Grafana metrics

---

## Project Goal

To build something that demonstrates how the pieces of a real backend fit together — layered
architecture, a relational schema under migration control, configurable business rules, an
explainable decision, and the tests that keep all of it honest — on a domain where getting it
wrong actually matters.
