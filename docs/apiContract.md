# API Contract

## Base URL

```text
/api
```

There is no configured `server.servlet.context-path` — every path below is the literal path declared on its controller method.

---

## Response Envelope

Every endpoint (except `GET /api/flyway/info`) returns the same envelope, `WebResponse<T>`. All four fields are always present in the JSON; unused ones are serialized as `null`.

```json
{
  "data": {},
  "message": "string",
  "errors": "string",
  "paging": {
    "currentPage": 1,
    "totalPage": 1,
    "size": 10
  }
}
```

| Field     | Type              | Description                                                                 |
| --------- | ----------------- | ----------------------------------------------------------------------------- |
| `data`    | object / array     | Payload on success, `null` on error                                          |
| `message` | string             | Human-readable success message, `null` on error                             |
| `errors`  | string             | Single error message, `null` on success (not a list of errors)              |
| `paging`  | object             | Present only on list endpoints (`GET /api/categories`, `GET /api/transaction`) |

`paging` does **not** include a total-element/total-item count — only `currentPage` (1-based), `totalPage`, and `size` (page size, not the count of returned items).

---

## Authentication

There is no Spring Security filter chain. Authentication is enforced per-endpoint by a custom `HandlerMethodArgumentResolver` that resolves a `User` controller-method parameter. An endpoint is "protected" purely because its method signature declares a `User user` parameter — there is no path-based allow/deny list.

```http
Authorization: Bearer <token>
```

- The prefix `Bearer ` is required (case-sensitive).
- `<token>` is **not a JWT** — it's an opaque random UUID string generated at login and stored verbatim in the `users.token` column.
- The stored `tokenExpiredAt` field is never checked by the resolver — **tokens do not currently expire**.
- Missing header, wrong prefix, or a token not found in the database → `401 Unauthorized`, `errors: "Unauthorized"`.

Endpoints are marked below as:

```text
Authentication: Public
```

or

```text
Authentication: Required
```

---

## 1. Authentication

### Register

Create a new user account.

**Endpoint**

```http
POST /api/auth/sign-up
```

**Authentication**

```text
Public
```

**Request Body**

```json
{
  "firstName": "ilham",
  "lastName": "suryana",
  "email": "ilham@example.com",
  "password": "password123"
}
```

| Field       | Type   | Validation                          |
| ----------- | ------ | ------------------------------------ |
| `firstName` | String | Required, max 100 chars              |
| `lastName`  | String | Required, max 100 chars              |
| `email`     | String | Required, max 100 chars, valid email |
| `password`  | String | Required, max 100 chars              |

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "firstName": "ilham",
    "lastName": "suryana",
    "email": "ilham@example.com"
  },
  "message": "Register successfully",
  "errors": null,
  "paging": null
}
```

**Error Response**

**HTTP 409 Conflict** — email already registered

```json
{
  "data": null,
  "message": null,
  "errors": "Email is already registered",
  "paging": null
}
```

**HTTP 400 Bad Request** — validation failure (e.g. missing field, invalid email)

```json
{
  "data": null,
  "message": null,
  "errors": "must not be blank",
  "paging": null
}
```

> Note: on validation failure, `errors` contains only the *first* field violation's message, not the full list.

---

### Login

Authenticate a user and return an access token.

**Endpoint**

```http
POST /api/auth/login
```

**Authentication**

```text
Public
```

**Request Body**

```json
{
  "email": "ilham@example.com",
  "password": "password123"
}
```

| Field      | Type   | Validation               |
| ---------- | ------ | ------------------------- |
| `email`    | String | Required, max 100 chars, valid email |
| `password` | String | Required, max 100 chars   |

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "token": "b1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "expiredAt": 1756645200000
  },
  "message": "Register successfully",
  "errors": null,
  "paging": null
}
```

| Field       | Type   | Description                                     |
| ----------- | ------ | ------------------------------------------------ |
| `token`     | String | Opaque bearer token, not a JWT                   |
| `expiredAt` | Long   | Epoch millis; **not enforced** on subsequent calls |

> Known issues to be aware of when integrating: the success `message` says `"Register successfully"` (copy-paste bug, should say "Login successfully"), and `expiredAt` is not actually ~30 days out as the field name implies — the offset is computed incorrectly server-side.

**Error Response**

**HTTP 401 Unauthorized**

```json
{
  "data": null,
  "message": null,
  "errors": "Username or password wrong",
  "paging": null
}
```

> Note: the request body is **not** annotated `@Valid` on this endpoint. Field-level violations (blank email/password) are validated manually in the service layer and currently surface as an unhandled `500 Internal Server Error` rather than a clean `400`, since that exception type isn't caught by the global error handler.

---

## 2. Category Management

### Create Category

Create a new transaction category.

**Endpoint**

```http
POST /api/categories
```

**Authentication**

```text
Required
```

**Request Header**

```http
Authorization: Bearer <token>
```

**Request Body**

```json
{
  "name": "Food",
  "type": "EXPENSE"
}
```

| Field  | Type   | Validation                          |
| ------ | ------ | ------------------------------------ |
| `name` | String | Required, max 100 chars              |
| `type` | String | Required, one of `EXPENSE`, `INCOME` |

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "name": "Food",
    "type": "EXPENSE"
  },
  "message": "Category created successfully",
  "errors": null,
  "paging": null
}
```

> Note: the create response does not include the generated `id` — use `GET /api/categories` or `GET /api/categories/{categoryId}` to retrieve it afterward.

**Error Response**

**HTTP 409 Conflict** — a category with the same `name` + `type` already exists

```json
{
  "data": null,
  "message": null,
  "errors": "Category already exists",
  "paging": null
}
```

> Note: this uniqueness check is currently **global**, not scoped per user.

---

### Get Category Detail

**Endpoint**

```http
GET /api/categories/{categoryId}
```

**Authentication**

```text
Required
```

**Path Parameter**

| Parameter    | Type | Description          |
| ------------ | ---- | --------------------- |
| `categoryId` | UUID | Category identifier    |

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
    "name": "Food",
    "type": "EXPENSE"
  },
  "message": null,
  "errors": null,
  "paging": null
}
```

**Error Response**

**HTTP 404 Not Found** — not found, or belongs to another user

```json
{
  "data": null,
  "message": null,
  "errors": "Category not found",
  "paging": null
}
```

**HTTP 400 Bad Request** — `categoryId` is not a valid UUID

---

### Get Category List

**Endpoint**

```http
GET /api/categories
```

**Authentication**

```text
Required
```

**Query Parameters**

| Parameter | Type    | Required | Description                                                        |
| --------- | ------- | -------- | -------------------------------------------------------------------- |
| `skip`    | Integer | Yes      | Page number (0-based) — despite the name, this is **not** a row offset |
| `limit`   | Integer | Yes      | Page size                                                             |

Both parameters are required — omitting either currently results in an unhandled `400` outside the standard `WebResponse` envelope (Spring's default missing-parameter error).

**Example**

```http
GET /api/categories?skip=0&limit=10
```

**Success Response**

**HTTP 200 OK**

```json
{
  "data": [
    {
      "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
      "name": "Food",
      "type": "EXPENSE"
    }
  ],
  "message": null,
  "errors": null,
  "paging": {
    "currentPage": 1,
    "totalPage": 1,
    "size": 10
  }
}
```

**Error Response**

**HTTP 400 Bad Request**

```json
{
  "data": null,
  "message": null,
  "errors": "Skip must be greater than or equal to 0",
  "paging": null
}
```

or `"Limit must be greater than 0"` when `limit <= 0`.

---

### Update Category

Full replace of a category (all fields required, despite the `PUT` semantics being effectively the only update mode — there is no partial-update endpoint for categories).

**Endpoint**

```http
PUT /api/categories/{categoryId}
```

**Authentication**

```text
Required
```

**Path Parameter**

| Parameter    | Type | Description       |
| ------------ | ---- | ------------------ |
| `categoryId` | UUID | Category identifier |

**Request Body**

```json
{
  "name": "Groceries",
  "type": "EXPENSE"
}
```

| Field  | Type   | Validation                          |
| ------ | ------ | ------------------------------------ |
| `name` | String | Required, max 100 chars              |
| `type` | String | Required, one of `EXPENSE`, `INCOME` |

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
    "name": "Groceries",
    "type": "EXPENSE"
  },
  "message": null,
  "errors": null,
  "paging": null
}
```

**Error Response**

**HTTP 404 Not Found** — not found, or belongs to another user

---

### Delete Category

**Endpoint**

```http
DELETE /api/categories/{categoryId}
```

**Authentication**

```text
Required
```

**Path Parameter**

| Parameter    | Type | Description       |
| ------------ | ---- | ------------------ |
| `categoryId` | UUID | Category identifier |

**Success Response**

**HTTP 200 OK**

```json
{
  "data": null,
  "message": "Category deleted successfully",
  "errors": null,
  "paging": null
}
```

**Error Response**

**HTTP 404 Not Found** — not found, or belongs to another user

---

## 3. Transaction Management

### Create Transaction

**Endpoint**

```http
POST /api/transaction
```

**Authentication**

```text
Required
```

**Request Body**

```json
{
  "transactonName": "Monthly Salary",
  "amount": 5000000,
  "description": "August salary",
  "categoryId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "date": "2026-08-31"
}
```

| Field             | Type       | Validation                                            |
| ----------------- | ---------- | ------------------------------------------------------ |
| `transactonName`  | String     | Required, max 100 chars — **field name is misspelled in the API, not "transactionName"** |
| `amount`          | BigDecimal | Required — no positive/non-zero constraint is enforced  |
| `description`     | String     | Optional                                               |
| `categoryId`      | UUID       | Required — must belong to the authenticated user        |
| `date`            | Date (`YYYY-MM-DD`) | Required                                       |

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "transactionName": "Monthly Salary",
    "amount": 5000000,
    "description": "August salary",
    "categoryId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
    "date": "2026-08-31"
  },
  "message": "Transaction created Successfully",
  "errors": null,
  "paging": null
}
```

> Note: the response does **not** include the generated `transactionCode`, which is the identifier used by every other transaction endpoint (get/update/delete). Fetch it afterward via `GET /api/transaction?skip=0&limit=...` or by tracking it server-side.

**Error Response**

**HTTP 404 Not Found** — `categoryId` doesn't exist or doesn't belong to the user

```json
{
  "data": null,
  "message": null,
  "errors": "Category not found",
  "paging": null
}
```

---

### Get Transaction Detail

**Endpoint**

```http
GET /api/transaction/{transactionCode}
```

**Authentication**

```text
Required
```

**Path Parameter**

| Parameter         | Type   | Description                                    |
| ------------------ | ------ | ------------------------------------------------ |
| `transactionCode`  | String | Server-generated code, format `TRX-<uuid>`       |

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "transactionName": "Monthly Salary",
    "transactionCode": "TRX-3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "amount": 5000000,
    "description": "August salary",
    "category": {
      "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
      "name": "Salary",
      "type": "INCOME"
    },
    "date": "2026-08-31"
  },
  "message": null,
  "errors": null,
  "paging": null
}
```

**Error Response**

**HTTP 404 Not Found** — not found, or belongs to another user

```json
{
  "data": null,
  "message": null,
  "errors": "Transaction not found",
  "paging": null
}
```

---

### Get Transaction List

**Endpoint**

```http
GET /api/transaction
```

**Authentication**

```text
Required
```

**Query Parameters**

| Parameter | Type    | Required | Description                                                        |
| --------- | ------- | -------- | -------------------------------------------------------------------- |
| `skip`    | Integer | Yes      | Page number (0-based) — despite the name, this is **not** a row offset |
| `limit`   | Integer | Yes      | Page size                                                             |

There is currently no `keyword`, `type`, `categoryId`, `startDate`, or `endDate` filter — only pagination is supported.

**Example**

```http
GET /api/transaction?skip=0&limit=10
```

**Success Response**

**HTTP 200 OK**

```json
{
  "data": [
    {
      "transactionName": "Monthly Salary",
      "transactionCode": "TRX-3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "amount": 5000000,
      "description": "August salary",
      "category": {
        "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
        "name": "Salary",
        "type": "INCOME"
      },
      "date": "2026-08-31"
    }
  ],
  "message": null,
  "errors": null,
  "paging": {
    "currentPage": 1,
    "totalPage": 1,
    "size": 10
  }
}
```

> Note: soft-deleted transactions are **not** currently filtered out of this list or of the detail endpoint.

---

### Update Transaction (Partial)

Only the fields provided are updated; omitted/`null` fields are left unchanged.

**Endpoint**

```http
PATCH /api/transaction/{transactionCode}
```

**Authentication**

```text
Required
```

**Path Parameter**

| Parameter          | Type   | Description               |
| ------------------- | ------ | --------------------------- |
| `transactionCode`  | String | Transaction identifier      |

**Request Body**

```json
{
  "transactonName": "Belanja Bulanan",
  "amount": 20000
}
```

| Field             | Type       | Validation                                    |
| ----------------- | ---------- | ------------------------------------------------ |
| `transactonName`  | String     | Optional, max 100 chars if provided             |
| `amount`          | BigDecimal | Optional                                         |
| `description`     | String     | Optional                                         |
| `categoryId`      | UUID       | Optional — must belong to the authenticated user if provided |
| `date`            | Date (`YYYY-MM-DD`) | Optional                               |

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "transactionName": "Belanja Bulanan",
    "transactionCode": "TRX-3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "amount": 20000,
    "description": "August salary",
    "category": {
      "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
      "name": "Salary",
      "type": "INCOME"
    },
    "date": "2026-08-31"
  },
  "message": null,
  "errors": null,
  "paging": null
}
```

**Error Response**

**HTTP 404 Not Found** — transaction not found, or `categoryId` provided doesn't belong to the user

---

### Delete Transaction

Soft-delete only — the record is flagged `isDeleted = true` but is **not** currently excluded from get/list responses.

**Endpoint**

```http
DELETE /api/transaction/{transactionCode}
```

**Authentication**

```text
Required
```

**Path Parameter**

| Parameter          | Type   | Description               |
| ------------------- | ------ | --------------------------- |
| `transactionCode`  | String | Transaction identifier      |

**Success Response**

**HTTP 200 OK**

```json
{
  "data": null,
  "message": "Transaction deleted successfully",
  "errors": null,
  "paging": null
}
```

**Error Response**

**HTTP 404 Not Found**

---

## 4. Operations

### Flyway Migration Info

Internal/ops endpoint — not part of the finance domain API, returns a raw array (not wrapped in the `WebResponse` envelope).

**Endpoint**

```http
GET /api/flyway/info
```

**Authentication**

```text
Public
```

**Success Response**

**HTTP 200 OK**

```json
[
  {
    "version": "1",
    "description": "init",
    "type": "SQL",
    "state": "SUCCESS",
    "installedOn": "2026-08-01 10:00:00",
    "executionTime_ms": 42,
    "script": "V1__init.sql"
  }
]
```

---

# Not Yet Implemented

The following are referenced in older drafts of this contract but do not exist in the current codebase — remove any client integration built against them:

- `/api/incomes`, `/api/expenses` (income/expense are just `Category.type` values on the unified transaction/category model, not separate resources)
- `/api/financial-summary`
- Transaction search/filter query params (`keyword`, `type`, `categoryId`, `startDate`, `endDate`)
- `204 No Content` responses (every endpoint returns `200 OK` with a JSON body, including deletes)

---

# HTTP Status Codes

| Status Code                 | Description                                                                    |
| ---------------------------- | -------------------------------------------------------------------------------- |
| `200 OK`                     | Request completed successfully (used for creates and deletes too — no `201`/`204`) |
| `400 Bad Request`            | Invalid request, validation error, or malformed path/query parameter            |
| `401 Unauthorized`           | Missing/invalid `Authorization` header or unknown token                         |
| `404 Not Found`               | Resource not found, or not owned by the authenticated user                      |
| `409 Conflict`                | Duplicate resource (email on register, name+type on category create)           |
| `500 Internal Server Error`  | Unexpected server error, including some currently-unhandled validation cases (see Login) |

---

# Authentication

Protected endpoints require a bearer token obtained from `POST /api/auth/login`:

```http
Authorization: Bearer <token>
```

Endpoints that require authentication are marked as:

```text
Authentication: Required
```

Public endpoints are marked as:

```text
Authentication: Public
```
