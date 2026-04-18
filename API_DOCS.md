# Mini Banking API — Frontend Integration Guide

## Base URL
```
http://localhost:8080/api/v1
```
Swagger UI (full interactive docs): `http://localhost:8080/swagger-ui/index.html`

---

## Authentication

All endpoints except `POST /auth/register`, `POST /auth/login`, and `POST /auth/refresh` require a Bearer token.

```
Authorization: Bearer <accessToken>
```

**Token lifetimes:**
- Access token: **15 minutes**
- Refresh token: **7 days**

---

## Rate Limiting

All requests are rate-limited per IP address:

| Endpoint group | Limit |
|----------------|-------|
| `/api/v1/auth/**` | **10 requests / minute** |
| All other endpoints | **100 requests / minute** |

Exceeding the limit returns `429 Too Many Requests`:
```json
{"status":429,"error":"Too Many Requests","message":"Rate limit exceeded. Try again later."}
```

---

## Response Envelope

Every successful response is wrapped:
```json
{
  "success": true,
  "message": "Human-readable message",
  "data": { ... },
  "timestamp": "2024-01-15T10:30:00"
}
```

`data` is `null` for operations that return no payload (e.g. logout, close account).

---

## Error Format (RFC 7807 Problem Detail)

All errors — validation, auth, business logic, server errors — use this shape:
```json
{
  "type": "https://banking.com/errors/banking-exception",
  "title": "BankingException",
  "status": 422,
  "detail": "Insufficient funds. Available: 1000, Required: 5000",
  "instance": "/api/v1/transactions/transfer",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

Validation errors include an extra `fieldErrors` map:
```json
{
  "type": "https://banking.com/errors/validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "Request validation failed",
  "fieldErrors": {
    "email": "Must be a valid email address",
    "password": "Password must contain at least one uppercase letter..."
  }
}
```

---

## Enums

| Enum | Values |
|------|--------|
| `AccountType` | `SAVINGS`, `CHECKING` |
| `AccountStatus` | `ACTIVE`, `FROZEN`, `CLOSED` |
| `Currency` | `UZS`, `USD`, `EUR` |
| `TransactionType` | `DEPOSIT`, `WITHDRAWAL`, `TRANSFER` |
| `TransactionStatus` | `PENDING`, `COMPLETED`, `FAILED`, `REVERSED` |
| `Role` | `ROLE_USER`, `ROLE_ADMIN` |

---

## Endpoints

### Authentication — `/api/v1/auth`

---

#### `POST /auth/register`
Register a new user account.

**Auth required:** No

**Request body:**
```json
{
  "firstName": "Ali",
  "lastName": "Karimov",
  "email": "ali@example.com",
  "password": "SecurePass@1",
  "phoneNumber": "+998901234567",
  "dateOfBirth": "1995-06-15"
}
```

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `firstName` | string | Yes | 1–100 chars |
| `lastName` | string | Yes | 1–100 chars |
| `email` | string | Yes | Valid email, max 255 chars |
| `password` | string | Yes | 8–128 chars; must contain uppercase, lowercase, digit, special char (`@$!%*?&_-#`) |
| `phoneNumber` | string | Yes | Format: `+998XXXXXXXXX` |
| `dateOfBirth` | date | No | ISO-8601 (`YYYY-MM-DD`), must be in the past |

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Registration successful",
  "data": {
    "id": "uuid",
    "firstName": "Ali",
    "lastName": "Karimov",
    "email": "ali@example.com",
    "phoneNumber": "+998901234567",
    "dateOfBirth": "1995-06-15",
    "role": "ROLE_USER",
    "enabled": true,
    "createdAt": "2024-01-15T10:30:00"
  }
}
```

**Error codes:** `400` validation failure, `409` email/phone already registered

---

#### `POST /auth/login`
Authenticate and receive JWT tokens.

**Auth required:** No

**Request body:**
```json
{
  "email": "ali@example.com",
  "password": "SecurePass@1"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "d1e2f3...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

**Error codes:** `400` validation, `401` invalid credentials, `423` account locked (after 5 failed attempts, locked for 30 min)

> **Security note:** Error messages are intentionally generic — the API does not distinguish "user not found" from "wrong password" to prevent username enumeration.

---

#### `POST /auth/refresh`
Exchange a refresh token for a new token pair (rotating refresh tokens).

**Auth required:** No

**Request body:**
```json
{
  "refreshToken": "d1e2f3..."
}
```

**Response `200 OK`:** Same shape as login response.

**Error codes:** `400` validation, `401` token expired/invalid/revoked

---

#### `POST /auth/logout`
Revoke all refresh tokens for the current user.

**Auth required:** Yes

**Request body:** None

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

---

### Users — `/api/v1/users`

---

#### `GET /users/me`
Get the authenticated user's profile.

**Auth required:** Yes

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Profile retrieved successfully",
  "data": {
    "id": "uuid",
    "firstName": "Ali",
    "lastName": "Karimov",
    "email": "ali@example.com",
    "phoneNumber": "+998901234567",
    "dateOfBirth": "1995-06-15",
    "role": "ROLE_USER",
    "enabled": true,
    "createdAt": "2024-01-15T10:30:00"
  }
}
```

---

#### `PUT /users/me`
Update mutable profile fields. Only non-null fields are applied.

**Auth required:** Yes

**Request body (all fields optional):**
```json
{
  "firstName": "Alisher",
  "lastName": "Karimov",
  "phoneNumber": "+998909876543",
  "dateOfBirth": "1995-06-15"
}
```

| Field | Type | Rules |
|-------|------|-------|
| `firstName` | string | 1–100 chars |
| `lastName` | string | 1–100 chars |
| `phoneNumber` | string | `+998XXXXXXXXX` |
| `dateOfBirth` | date | `YYYY-MM-DD`, must be past |

**Response `200 OK`:** Updated `UserResponse` (same shape as `GET /users/me`)

> Email and password changes are not supported in V1 — dedicated flows are planned.

---

### Accounts — `/api/v1/accounts`

---

#### `POST /accounts`
Open a new bank account.

**Auth required:** Yes

**Request body:**
```json
{
  "accountType": "SAVINGS",
  "currency": "UZS",
  "isPrimary": true
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `accountType` | enum | Yes | `SAVINGS` or `CHECKING` |
| `currency` | enum | Yes | `UZS`, `USD`, `EUR` |
| `isPrimary` | boolean | No | If `true`, demotes any existing primary account |

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Account created successfully",
  "data": {
    "id": "uuid",
    "accountNumber": "8600123456789012",
    "ownerId": "uuid",
    "accountType": "SAVINGS",
    "status": "ACTIVE",
    "balance": 0.0000,
    "currency": "UZS",
    "dailyTransferLimit": 50000000,
    "monthlyTransferLimit": 500000000,
    "isPrimary": true,
    "createdAt": "2024-01-15T10:30:00"
  }
}
```

**Error codes:** `400` validation, `422` max 5 accounts per user reached

---

#### `GET /accounts`
List all accounts belonging to the authenticated user.

**Auth required:** Yes

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Accounts retrieved successfully",
  "data": [ { ...AccountResponse }, ... ]
}
```

---

#### `GET /accounts/{id}`
Get a single account by UUID.

**Auth required:** Yes

**Path params:** `id` — account UUID

**Response `200 OK`:** Single `AccountResponse`

**Error codes:** `404` account not found, `403` account belongs to another user

---

#### `DELETE /accounts/{id}`
Close an account (soft delete — sets status to `CLOSED`).

**Auth required:** Yes

**Path params:** `id` — account UUID

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Account closed successfully"
}
```

**Error codes:** `404` not found, `403` unauthorized, `422` account already closed, non-zero balance, or pending transactions

> Accounts with remaining balance must have all funds withdrawn or transferred first.
> Accounts with unsettled (pending) transactions cannot be closed until they settle.

---

### Transactions — `/api/v1/transactions`

---

#### `POST /transactions/deposit`
Deposit funds into an account.

**Auth required:** Yes

**Request body:**
```json
{
  "accountId": "uuid",
  "amount": 500000,
  "description": "Salary"
}
```

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `accountId` | UUID | Yes | Must belong to authenticated user |
| `amount` | decimal | Yes | `0.0001` – `1,000,000,000` |
| `description` | string | No | Max 255 chars |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Deposit completed successfully",
  "data": {
    "id": "uuid",
    "referenceNumber": "TXN-20240115-AB12CD34",
    "targetAccountId": "uuid",
    "type": "DEPOSIT",
    "status": "COMPLETED",
    "amount": 500000.0000,
    "fee": 0.0000,
    "balanceBeforeTarget": 0.0000,
    "balanceAfterTarget": 500000.0000,
    "currency": "UZS",
    "description": "Salary",
    "processedAt": "2024-01-15T10:30:00",
    "createdAt": "2024-01-15T10:30:00"
  }
}
```

**Error codes:** `400` validation, `403` account not owned by user, `422` account not active

---

#### `POST /transactions/withdraw`
Withdraw funds from an account.

**Auth required:** Yes

**Request body:**
```json
{
  "accountId": "uuid",
  "amount": 100000,
  "description": "ATM withdrawal"
}
```

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `accountId` | UUID | Yes | Must belong to authenticated user |
| `amount` | decimal | Yes | `0.0001` – `1,000,000,000` |
| `description` | string | No | Max 255 chars |

**Fee policy:** Withdrawals above **10,000,000 UZS** incur a fee (default **0.5%**) deducted from the account balance in addition to the withdrawal amount.

**Response `200 OK`:** `TransactionResponse` with `sourceAccountId`, `balanceBeforeSource`, `balanceAfterSource`, and `fee` populated.

**Error codes:** `400` validation, `403` unauthorized, `422` insufficient funds / account not active

---

#### `POST /transactions/transfer`
Transfer funds between two accounts.

**Auth required:** Yes

**Headers:**

| Header | Required | Format | Description |
|--------|----------|--------|-------------|
| `X-Idempotency-Key` | **Yes** | UUID (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`) | Safe-retry guarantee — resubmitting the same key returns the original result without re-processing |

> If the header is missing, the server returns `400 Bad Request`.
> If the value is not a valid UUID, the server returns `422 Unprocessable Entity`.

**Request body:**
```json
{
  "sourceAccountId": "uuid",
  "targetAccountId": "uuid",
  "amount": 200000,
  "description": "Rent payment"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `sourceAccountId` | UUID | Yes | Must belong to authenticated user |
| `targetAccountId` | UUID | Yes | Can belong to any user |
| `amount` | decimal | Yes | `0.0001` – `500,000,000` |
| `description` | string | No | Max 255 chars |

**Limits (per source account, rolling window in UTC):**

| Window | Limit |
|--------|-------|
| Daily | **50,000,000 UZS** |
| Monthly | **500,000,000 UZS** |

**Fee:** No fee for same-currency internal transfers.

**Idempotency behaviour:**
- Same key + same parameters → original `TransactionResponse` returned immediately
- Same key + different parameters → `422` idempotency key conflict

**Response `200 OK`:** Full `TransactionResponse` with both source and target balance snapshots.

**Error codes:**
- `400` validation, missing `X-Idempotency-Key` header
- `403` source account not owned by user
- `409` concurrent modification (optimistic lock — safe to retry with a **new** idempotency key)
- `422` insufficient funds, account not active, cross-currency transfer, daily/monthly limit exceeded, same source/target, idempotency key conflict, invalid key format

---

#### `GET /transactions/{accountId}/history`
Get paginated transaction history for an account.

**Auth required:** Yes

**Path params:** `accountId` — account UUID (must belong to authenticated user)

**Query params:**

| Param | Default | Max | Description |
|-------|---------|-----|-------------|
| `page` | `0` | — | Page index (0-based) |
| `size` | `10` | **100** | Items per page |
| `sort` | `createdAt,desc` | — | Sort field and direction |

**Example:** `GET /transactions/uuid/history?page=0&size=20&sort=processedAt,desc`

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Transaction history retrieved",
  "data": {
    "content": [ { ...TransactionResponse }, ... ],
    "totalElements": 42,
    "totalPages": 5,
    "number": 0,
    "size": 10,
    "first": true,
    "last": false
  }
}
```

**Error codes:** `403` account not owned by user, `404` account not found

---

## Common HTTP Status Codes

| Status | Meaning |
|--------|---------|
| `200` | OK |
| `201` | Created (new resource) |
| `400` | Validation error or missing required header |
| `401` | Missing/expired/invalid token |
| `403` | Authenticated but not authorized for this resource |
| `404` | Resource not found |
| `409` | Conflict (concurrent modification — retry with a new idempotency key) |
| `422` | Business rule violation |
| `423` | Account locked (too many failed logins) |
| `429` | Rate limit exceeded |
| `500` | Unexpected server error |

---

## Frontend Integration Tips

**1. Token storage**
Store `accessToken` in memory (not localStorage) and `refreshToken` in an `HttpOnly` cookie if possible. If using localStorage, be aware of XSS risk.

**2. Auto-refresh flow**
When any request returns `401`, call `POST /auth/refresh` with the stored refresh token and retry the original request once. If refresh also returns `401`, redirect to login.

**3. Amount precision**
All monetary amounts use up to 4 decimal places (e.g. `500000.0000`). Display with appropriate locale formatting.

**4. Account number format**
Account numbers are 16-digit strings starting with `8600`. Display with spaces for readability: `8600 1234 5678 9012`.

**5. Pagination**
The history endpoint returns Spring's `Page<T>` object. Use `content` for the rows and `totalPages`/`totalElements` for pagination UI. Maximum page size is **100**.

**6. Idempotency keys for transfers**
Generate a UUID client-side (`crypto.randomUUID()`) and include it as `X-Idempotency-Key`. The header is **required**. On network timeout or retry, reuse the **same** key — the server guarantees exactly-once execution. On a new transfer, generate a **new** key.

**7. Rate limiting**
Auth endpoints allow 10 requests/minute per IP. Build exponential back-off when you receive `429` — do not hammer the login endpoint on failures.

**8. Concurrent modification (409)**
A `409` on transfer means two requests touched the same account simultaneously. Retry the request once with a **new** `X-Idempotency-Key` after a short delay (~200ms).
