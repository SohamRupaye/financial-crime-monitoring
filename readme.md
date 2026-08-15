# Financial Crime Transaction Monitoring System

A production-oriented backend system for **Anti-Money Laundering (AML) transaction monitoring**, built with Java 17 and Spring Boot.

The system simulates how a financial institution can ingest transactions, evaluate them against configurable AML rules, calculate explainable risk scores, generate alerts, and manage investigations.

> **Disclaimer:** This project uses entirely synthetic transaction and customer data for educational and demonstration purposes. It is not intended to provide real-world financial, regulatory, or compliance advice.

---

## Overview

Financial institutions process large volumes of transactions every day. A transaction monitoring system helps identify activity that may warrant further investigation.

This project models that workflow:

```text
                    ┌──────────────────────┐
                    │   Transaction Source  │
                    │  Synthetic / REST API │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │ Transaction Ingestion │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │    AML Rules Engine  │
                    │                      │
                    │ • Amount thresholds  │
                    │ • Velocity checks    │
                    │ • Structuring        │
                    │ • Customer risk      │
                    │ • Country risk       │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │    Risk Assessment   │
                    │                      │
                    │ Score + Explanation  │
                    └──────────┬───────────┘
                               │
                  ┌────────────┴────────────┐
                  ▼                         ▼
             Low / Medium              High / Critical
                  │                         │
                  ▼                         ▼
              Persist                 Generate Alert
                                            │
                                            ▼
                                    Investigation Case
                                            │
                                            ▼
                                      Analyst Review
```

---

# Core Features

## 1. Customer Management

The system maintains customer profiles used during transaction risk evaluation.

Each customer can contain:

* Unique customer ID
* Name
* Account information
* Country
* Customer risk level
* Account creation date
* Status
* Transaction history

Example:

```json
{
  "customerId": "CUST-1024",
  "riskLevel": "MEDIUM",
  "country": "IN",
  "status": "ACTIVE"
}
```

Customer risk level is one of:

```text
LOW
MEDIUM
HIGH
```

The customer's risk level contributes to the final transaction risk assessment.

---

# 2. Transaction Management

Transactions represent financial activity between customers/accounts.

Example:

```json
{
  "transactionId": "TXN-93842",
  "senderId": "CUST-1024",
  "receiverId": "CUST-8841",
  "amount": 485000,
  "currency": "INR",
  "type": "TRANSFER",
  "timestamp": "2026-08-11T14:32:00",
  "destinationCountry": "IN"
}
```

Supported transaction types may include:

* TRANSFER
* CASH_DEPOSIT
* CASH_WITHDRAWAL
* CARD_PAYMENT
* INTERNATIONAL_TRANSFER

Transactions can be:

* Created through REST APIs
* Generated through the synthetic data generator
* Retrieved by ID
* Searched and filtered
* Associated with customers
* Evaluated by the AML engine

---

# 3. Synthetic Transaction Generator

No real financial data is used.

The project includes a synthetic data generator capable of producing realistic transaction datasets.

The generator creates:

### Normal activity

```text
₹2,000
₹4,500
₹8,200
₹3,700
```

### Suspicious patterns

For example:

```text
₹490,000
₹490,000
₹495,000
₹480,000
```

within a short time window.

The generator can control:

* Number of customers
* Number of transactions
* Amount ranges
* Transaction frequency
* Countries
* Customer risk levels
* Suspicious transaction patterns

This allows the system to be tested against both normal and suspicious workloads.

---

# 4. AML Rules Engine

The AML Rules Engine is the core component of the application.

It evaluates transactions against a collection of independent rules.

Each rule returns:

```text
Triggered / Not Triggered
Risk Points
Reason
```

The engine then combines the results into a final risk assessment.

Example:

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

---

# 5. AML Rules

## Large Transaction Rule

Detects unusually large transactions.

Example:

```text
IF transaction amount > configured threshold
THEN add risk points
```

Example configuration:

```text
Threshold: ₹500,000
Risk Points: +25
```

Thresholds should be configurable rather than hardcoded.

---

## Transaction Velocity Rule

Detects unusually frequent transactions within a time window.

Example:

```text
IF customer performs > 10 transactions
WITHIN 10 minutes
THEN +20 risk points
```

This can identify unusual transaction bursts.

---

## Structuring Detection

Structuring refers to splitting transactions into smaller amounts to avoid triggering a larger transaction threshold.

Example:

```text
₹490,000
₹490,000
₹480,000
₹495,000
```

Instead of:

```text
₹1,955,000
```

The system looks for:

* Multiple transactions
* Same customer
* Similar amounts
* Short time window
* Combined amount significantly larger than individual transactions

If the pattern matches the configured rule:

```text
+30 risk points
```

---

## Customer Risk Rule

A customer's existing risk profile contributes to transaction risk.

Example:

```text
LOW     → +0
MEDIUM  → +10
HIGH    → +20
```

---

## Country Risk Rule

The system supports a configurable list of countries requiring additional scrutiny.

For demonstration purposes, this list is configurable and **does not represent an official regulatory classification**.

Example:

```text
IF destination country exists
IN configured elevated-risk list
THEN +20
```

---

# 6. Risk Scoring

Every evaluated transaction receives an explainable risk score.

Example:

```text
Large transaction       +25
Velocity anomaly        +20
Customer risk           +15
Country risk            +20
                         ---
Total                    80
```

Risk levels:

```text
0 – 29    LOW
30 – 59   MEDIUM
60 – 79   HIGH
80+       CRITICAL
```

The thresholds are configurable.

---

# 7. Explainable Risk Assessment

The system does not simply return:

```text
Risk = 80
```

It also explains why.

Example:

```json
{
  "transactionId": "TXN-93842",
  "score": 80,
  "level": "CRITICAL",
  "reasons": [
    "Transaction exceeded configured amount threshold",
    "High transaction velocity detected",
    "Customer has elevated risk profile",
    "Destination country requires additional scrutiny"
  ]
}
```

This makes the risk assessment transparent and easier for an analyst to investigate.

---

# 8. Alert Management

Transactions reaching a configured risk threshold generate alerts.

Example:

```text
Risk Score >= 60
        │
        ▼
     ALERT
        │
        ├── Transaction details
        ├── Risk score
        ├── Triggered rules
        ├── Explanation
        └── Customer information
```

Alerts have statuses:

```text
OPEN
ACKNOWLEDGED
INVESTIGATING
RESOLVED
FALSE_POSITIVE
```

---

# 9. Investigation Case Management

An alert can be converted into an investigation case.

An analyst can:

* View transaction details
* View customer information
* Review previous transactions
* View triggered AML rules
* Review risk score
* Add investigation notes
* Assign the case
* Change case status
* Mark the alert as a false positive
* Resolve the investigation

Example workflow:

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

# 10. Transaction History & Search

The system supports querying historical transactions.

Possible filters:

* Customer
* Transaction type
* Amount range
* Date range
* Risk level
* Country
* Alert status

Pagination is used for large result sets.

Example:

```text
GET /api/v1/transactions?page=0&size=20
```

---

# 11. REST API

The backend exposes RESTful APIs for interacting with the system.

Example endpoints:

```text
POST   /api/v1/transactions
GET    /api/v1/transactions/{id}
GET    /api/v1/transactions
POST   /api/v1/transactions/{id}/evaluate

GET    /api/v1/customers/{id}
GET    /api/v1/customers/{id}/transactions

GET    /api/v1/alerts
GET    /api/v1/alerts/{id}
PATCH  /api/v1/alerts/{id}/status

GET    /api/v1/cases
POST   /api/v1/cases
PATCH  /api/v1/cases/{id}
```

The API will use:

* JSON request/response bodies
* HTTP status codes
* Request validation
* Centralized exception handling
* Pagination
* Filtering

---

# 12. Authentication & Authorization

The application supports authenticated users.

Potential roles:

```text
ADMIN
ANALYST
VIEWER
```

Example permissions:

| Operation            | ADMIN | ANALYST | VIEWER |
|----------------------|------:|--------:|-------:|
| View transactions    |     ✓ |       ✓ |      ✓ |
| Evaluate transaction |     ✓ |       ✓ |      ✓ |
| Manage alerts        |     ✓ |       ✓ |   Read |
| Manage cases         |     ✓ |       ✓ |   Read |
| Configure rules      |     ✓ |       ✗ |      ✗ |
| Manage users         |     ✓ |       ✗ |      ✗ |

Authentication can be implemented using Spring Security and JWT.

---

# 13. Database Design

PostgreSQL is used as the primary relational database.

Core entities:

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
                    ▼
                  Alert
                    │
                    ▼
                   Case
```

Potential tables:

```text
customers
accounts
transactions
risk_assessments
risk_rule_results
alerts
investigation_cases
case_notes
users
aml_rules
```

Hibernate/JPA is used for ORM and entity persistence.

Database indexes will be added for frequently queried fields such as:

* customer ID
* transaction timestamp
* transaction amount
* alert status
* risk level

---

# 14. Redis Caching

Redis is used for frequently accessed data.

Potential cached data:

* Customer risk profiles
* Rule configuration
* Frequently requested transaction summaries
* Dashboard metrics

Example:

```text
Request
   ↓
Redis
   │
   ├── Cache hit → Return
   │
   └── Cache miss
           ↓
       PostgresSQL
           ↓
       Update cache
```

Cache expiration policies will prevent stale data from persisting indefinitely.

---

# 15. Event-Driven Processing

For larger workloads, transaction processing can be decoupled using a message broker such as Kafka or RabbitMQ.

Example:

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

This allows transaction ingestion and risk processing to scale independently.

> Event-driven processing is an optional advanced component and will only be included in the final implementation if completed.

---

# 16. Testing

Unit and integration tests will cover critical business logic.

Important test areas:

* Transaction validation
* Risk scoring
* Individual AML rules
* Structuring detection
* Velocity detection
* Customer risk calculation
* Alert generation
* Repository operations
* REST endpoints

Example:

```text
Transaction
     ↓
AML Rule
     ↓
Expected Risk Points
     ↓
JUnit Assertion
```

---

# 17. Docker

The application is containerized using Docker.

Development environment:

```text
┌──────────────────────────────┐
│ Docker Compose               │
│                              │
│ Spring Boot API              │
│ PostgresSQL                   │
│ Redis                        │
│ Message Broker (optional)    │
└──────────────────────────────┘
```

This allows the entire backend environment to be started consistently.

Example:

```bash
docker compose up -d
```

---

# 18. Technology Stack

### Backend

* Java 17
* Spring Boot
* Spring Security
* Spring Data JPA
* Hibernate
* Maven

### Database

* PostgreSQL
* Redis

### Messaging

* Kafka / RabbitMQ

### Testing

* JUnit
* Mockito
* Spring Boot Test

### DevOps

* Docker
* Docker Compose
* Kubernetes
* CI/CD

### API

* REST
* JSON
* OpenAPI / Swagger

---

# 19. Project Architecture

The application follows a layered architecture:

```text
Controller
    │
    ▼
Service
    │
    ▼
Domain / Rules
    │
    ▼
Repository
    │
    ▼
Database
```

Example:

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
              AMLRulesEngine
                    │
          ┌─────────┼─────────┐
          ▼         ▼         ▼
       Amount    Velocity   Structuring
        Rule       Rule        Rule
```

The rules engine is designed around independent rules so that new rules can be added without modifying the core evaluation workflow.

---

# 20. Design Principles

The project emphasizes:

* SOLID principles
* Separation of concerns
* Dependency injection
* Interface-driven design
* Strategy pattern for AML rules
* Layered architecture
* Centralized exception handling
* Input validation
* Configurable business rules
* Explainable risk assessment
* Testable business logic

---

# 21. Example End-to-End Flow

A suspicious transaction enters the system:

```text
₹490,000
Customer: CUST-1024
Destination: Example Country
```

The system performs:

```text
1. Validate transaction
          ↓
2. Persist transaction
          ↓
3. Retrieve customer profile
          ↓
4. Execute AML rules
          ↓
5. Large Amount Rule
          → +25
          ↓
6. Velocity Rule
          → +20
          ↓
7. Structuring Rule
          → +30
          ↓
8. Customer Risk Rule
          → +15
          ↓
9. Calculate score
          → 90
          ↓
10. Risk level
          → CRITICAL
          ↓
11. Generate alert
          ↓
12. Create investigation case
```

The analyst can then review the case and investigate the underlying activity.

---

# 22. Getting Started

## Prerequisites

Install:

* Java 17+
* Maven
* Docker
* Docker Compose
* PostgreSQL (optional when using Docker)
* Redis (optional when using Docker)

Verify:

```bash
java -version
mvn -version
docker --version
docker compose version
```

---

## Clone

```bash
git clone https://github.com/<username>/financial-crime-monitoring.git

cd financial-crime-monitoring
```

---

## Configuration

Create an environment configuration:

```text
DB_HOST=localhost
DB_PORT=5432
DB_NAME=financial_crime
DB_USERNAME=postgres
DB_PASSWORD=postgres

REDIS_HOST=localhost
REDIS_PORT=6379
```

Never commit production credentials.

---

## Start Infrastructure

```bash
docker compose up -d postgres redis
```

---

## Run Application

```bash
./mvnw spring-boot:run
```

Or:

```bash
mvn spring-boot:run
```

---

## Run Tests

```bash
./mvnw test
```

---

## Build

```bash
./mvnw clean package
```

---

# 23. Synthetic Data Generation

The application can generate demonstration data.

Example:

```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--seed-data=true"
```

The generator creates:

* Customers
* Accounts
* Normal transactions
* High-value transactions
* High-velocity transactions
* Structuring scenarios
* Different customer risk profiles

This allows the complete monitoring workflow to be demonstrated without external data sources.

---

# 24. API Documentation

Once the application is running, API documentation will be available through Swagger/OpenAPI.

Example:

```text
http://localhost:8080/swagger-ui.html
```

The API documentation provides:

* Available endpoints
* Request schemas
* Response schemas
* Authentication requirements
* Example requests

---

# 25. Future Improvements

Potential future extensions:

* ML-based anomaly detection
* Real-time transaction streaming
* Advanced graph-based transaction analysis
* Customer behavioral profiles
* Distributed transaction processing
* Kubernetes deployment
* Cloud deployment on AWS
* CI/CD using Jenkins/GitHub Actions
* OpenTelemetry observability
* Prometheus/Grafana monitoring
* Advanced case-management workflows

---

## Project Goal

The goal of this project is to demonstrate how a modern backend system can combine:

```text
Java
+
Spring Boot
+
Relational Databases
+
Caching
+
Event-Driven Processing
+
Containerization
+
Business Rules
+
Risk Scoring
```

to build a scalable and explainable financial-crime monitoring platform using entirely synthetic data.
