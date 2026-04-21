<div align="center">

# 🏦 Mini Banking API

**Production-ready banking backend built with Java 17 & Spring Boot 3**

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?style=flat-square&logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

[Features](#-features) · [Architecture](#-architecture) · [Quick Start](#-quick-start) · [API Reference](#-api-reference) · [Security](#-security) · [Testing](#-testing)

</div>

---

## Overview

Mini Banking API is a fully featured, security-hardened RESTful backend that simulates core operations of a real banking system. Built from scratch with production concerns in mind: atomic transactions, distributed rate limiting, audit trails, JWT token lifecycle management, and thorough penetration testing.

Designed as a portfolio project to demonstrate deep knowledge of Spring Security internals, concurrent transaction management, and backend security engineering.

---

## Features

### Authentication & Token Management
- Register, login, logout with full JWT lifecycle
- **Access token** — 15-minute HS512 signed JWT with `jti` claim
- **Refresh token** — 7-day rotating token, stored as SHA-256 hash (raw value never touches the DB)
- **Token rotation** — every refresh invalidates the old token and issues a new pair
- **Replay attack detection** — if a revoked token is reused, all user tokens are immediately revoked

### Account Management
- Create up to **5 accounts** per user (SAVINGS / CHECKING)
- Supported currencies: **UZS, USD, EUR**
- Primary account designation (enforced via partial unique index)
- Account closure with balance validation
- Real-time balance queries

### Transaction Engine
- **Deposit** and **Withdrawal** with automatic fee calculation
- **Transfer** between accounts with full audit trail
- **Idempotency** — `X-Idempotency-Key` header prevents duplicate transactions on network retry
- **Daily limit**: 50,000,000 UZS | **Monthly limit**: 500,000,000 UZS
- Large withdrawal fee: 0.5% (configurable, applied above 10,000,000 UZS)
- Banker's rounding (HALF_EVEN) for all monetary calculations
- Paginated transaction history (max 100 per page)

### Security
- **Account lockout** — 5 failed logins → 30-minute lock (atomic DB update, TOCTOU-safe)
- **Timing attack prevention** — dummy BCrypt hash for non-existent email paths
- **Rate limiting** — Bucket4j token bucket: 10 req/min on auth endpoints, 100 req/min on all others
- **Pessimistic locking** — `SELECT FOR UPDATE` on transfers, deadlock-safe via UUID-ordered lock acquisition
- **CORS** — profile-based allowed origins (dev / prod)
- Generic error messages — no username enumeration possible
- RFC 7807 ProblemDetail error format across all endpoints

### Audit & Observability
- Full audit log for every sensitive action (login, transfer, account open/close)
- PII masking in logs — emails, phone numbers, account numbers are partially redacted
- IP address and User-Agent captured per event
- Spring Boot Actuator: `/actuator/health`, `/actuator/info`, `/actuator/metrics`

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        Client                           │
└───────────────────────────┬─────────────────────────────┘
                            │ HTTPS
┌───────────────────────────▼─────────────────────────────┐
│              RateLimitingFilter (Bucket4j)               │
│         Auth: 10/min  │  API: 100/min  (per IP)         │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│           JwtAuthenticationFilter (HS512)               │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│                      Controllers                         │
│   AuthController  │  AccountController                  │
│   TransactionController  │  UserController              │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│                       Services                          │
│   @Transactional(REPEATABLE_READ) + Pessimistic Lock    │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│             Spring Data JPA Repositories                │
│              (Native queries for atomic ops)            │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│                   PostgreSQL 16                         │
│          12 Flyway migrations │ 6 tables                │
└─────────────────────────────────────────────────────────┘
```

### Database Schema

| Table | Description |
|-------|-------------|
| `users` | User accounts with lockout tracking |
| `accounts` | Bank accounts (SAVINGS/CHECKING, multi-currency) |
| `transactions` | Full transaction ledger with idempotency |
| `refresh_tokens` | SHA-256 hashed refresh tokens with device info |
| `audit_logs` | Immutable event log for all sensitive operations |

### Project Structure

```
src/
├── main/java/com/banking/
│   ├── audit/          # AuditLogService, AuditMasker
│   ├── config/         # Security, Web, OpenAPI, ApplicationProperties
│   ├── controller/     # REST endpoints (4 controllers)
│   ├── dto/            # Request / Response records
│   ├── entity/         # JPA entities
│   ├── enums/          # Role, AccountType, Currency, TransactionType...
│   ├── exception/      # GlobalExceptionHandler + domain exceptions
│   ├── mapper/         # MapStruct mappers
│   ├── repository/     # Spring Data JPA repositories
│   ├── security/       # JWT, filters, UserDetails
│   └── service/        # Business logic implementations
└── main/resources/
    ├── application.yml          # Multi-profile config (dev/test/prod)
    └── db/migration/            # V1–V12 Flyway migrations
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.3.5 |
| Security | Spring Security 6, JJWT 0.12.x (HS512), BCrypt |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| ORM | Spring Data JPA / Hibernate |
| Rate Limiting | Bucket4j |
| Code Generation | MapStruct 1.5.5, Lombok |
| API Docs | Springdoc OpenAPI 3 (Swagger UI) |
| Testing | JUnit 5, Mockito, Testcontainers |
| Build | Maven 3.9 |
| Containerization | Docker, Docker Compose |

---

## Quick Start

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running

### 1. Clone the repository
```bash
git clone https://github.com/your-username/mini-banking-api.git
cd mini-banking-api
```

### 2. Configure environment
```bash
cp .env.example .env
```

Edit `.env` and set:
```env
DB_PASSWORD=your_strong_password

# Generate a secure JWT secret:
# openssl rand -base64 64
JWT_SECRET=your_base64_encoded_512bit_secret
```

### 3. Start with Docker
```bash
docker compose up -d
```

### 4. Verify
```bash
# Health check
curl http://localhost:8080/actuator/health

# Interactive API docs
open http://localhost:8080/swagger-ui/index.html
```

### Run locally (without Docker)

Requires: Java 17+, PostgreSQL 16 running locally

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## API Reference

Base URL: `http://localhost:8080/api/v1`

### Authentication

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/auth/register` | Register new user | Public |
| POST | `/auth/login` | Login, receive token pair | Public |
| POST | `/auth/refresh` | Rotate refresh token | Public |
| POST | `/auth/logout` | Revoke all refresh tokens | Bearer |

### Accounts

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/accounts` | Create new account | Bearer |
| GET | `/accounts` | List my accounts | Bearer |
| GET | `/accounts/{id}` | Get account details | Bearer |
| DELETE | `/accounts/{id}` | Close account | Bearer |

### Transactions

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/transactions/deposit` | Deposit funds | Bearer |
| POST | `/transactions/withdraw` | Withdraw funds | Bearer |
| POST | `/transactions/transfer` | Transfer between accounts | Bearer |
| GET | `/transactions/{accountId}/history` | Paginated history | Bearer |

> **Transfer** requires `X-Idempotency-Key: <uuid>` header.

### Users

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/users/me` | Get my profile | Bearer |
| PUT | `/users/me` | Update profile | Bearer |

### Example: Register & Login

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Ali",
    "lastName": "Valiyev",
    "email": "ali@example.com",
    "password": "Secure@Pass123",
    "phoneNumber": "+998901234567"
  }'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "ali@example.com", "password": "Secure@Pass123"}'
```

### Example: Transfer with Idempotency

```bash
curl -X POST http://localhost:8080/api/v1/transactions/transfer \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "sourceAccountId": "...",
    "targetAccountId": "...",
    "amount": 500000,
    "description": "Rent payment"
  }'
```

---

## Security

### Penetration Testing Results

The API was self-tested against common attack vectors:

| Attack | Result |
|--------|--------|
| JWT `alg:none` (unsigned token) | Blocked |
| JWT secret brute-force | Blocked (512-bit key) |
| IDOR (access other user's accounts) | Blocked (ownership check) |
| Race condition / double-spend (20 concurrent) | Blocked (pessimistic lock) |
| Negative / zero / overflow amounts | Blocked (validation) |
| SQL injection (all input surfaces) | Blocked (JPA parameterized queries) |
| Refresh token replay attack | Blocked (rotation + full revoke) |
| Mass account creation | Blocked (5-account limit) |
| Actuator sensitive endpoints | Blocked (auth required) |

### Key Security Design Decisions

**JWT Storage:** Refresh tokens are stored as SHA-256 hashes. The raw token is returned to the client exactly once and never persisted.

**Lockout atomicity:** Failed login counter increments and lockout are applied in a single `UPDATE ... WHERE` statement — no TOCTOU race possible between concurrent login attempts.

**Timing equalization:** When an email doesn't exist, a dummy BCrypt hash is computed to match the response time of a real failed login. This prevents timing-based username enumeration.

**Transfer deadlock prevention:** When locking two accounts for a transfer, they are always acquired in ascending UUID order regardless of source/target — eliminates circular wait.

**Token replay detection:** If a revoked or expired refresh token is submitted, the system assumes a compromise and immediately revokes all tokens for that user.

---

## Testing

```bash
# Run all tests (uses Testcontainers — Docker required)
./mvnw test

# Run only unit tests
./mvnw test -pl . -Dtest="*ServiceTest,*ProviderTest"

# Run integration tests
./mvnw test -Dtest="*IntegrationTest"
```

### Test Coverage

| Layer | Tests |
|-------|-------|
| Unit | AuthServiceImpl, TransactionServiceImpl, AccountServiceImpl, JwtTokenProvider |
| Integration | Transfer flow (race conditions, limits, idempotency) |
| Controller | AuthController, TransactionController (MockMvc) |

---

## Configuration

All configuration is in `src/main/resources/application.yml` with three profiles:

| Profile | Usage | Database |
|---------|-------|----------|
| `dev` | Local development | `localhost:5432` |
| `test` | Automated tests | Testcontainers (auto) |
| `prod` | Production (Docker) | Env vars required |

Key configurable properties:

```yaml
app:
  banking:
    max-accounts-per-user: 5
    daily-transfer-limit: 50000000
    monthly-transfer-limit: 500000000
    large-withdrawal-threshold: 10000000
    large-withdrawal-fee-percent: 0.5
    account-lockout-attempts: 5
    account-lockout-duration-minutes: 30
  security:
    jwt:
      access-token-expiration: 900000    # 15 minutes
      refresh-token-expiration: 604800000 # 7 days
```

---

## Default Admin Account

A seeded admin user is created via `V7` migration:

| Field | Value |
|-------|-------|
| Email | `admin@banking.com` |
| Password | `Admin@123456` |

> **Change this immediately after first deploy.**

---

## License

This project is licensed under the MIT License.

---

<div align="center">

Built by **Husniddin Mahmudov**

</div>
