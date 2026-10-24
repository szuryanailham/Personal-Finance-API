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

## CORS

CORS is enabled for every path under `/api/**`.

| Setting           | Value                                                                                   |
| ----------------- | --------------------------------------------------------------------------------------- |
| Allowed origins   | `app.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`, comma-separated). Default: `http://localhost:3000` |
| Allowed methods   | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`                                      |
| Allowed headers   | `Authorization`, `Content-Type`, `Accept`                                               |
| Preflight max-age | `3600` seconds                                                                          |

Credentials (`allowCredentials`) are not enabled. Auth uses the `Authorization` header, not cookies, so clients don't need them.

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

### Get Category Expense Statistic

Total expense per category over a date range, for a pie chart. Only categories of type `EXPENSE` and active transactions (`is_deleted = false`) belonging to the logged-in user are counted. Soft-deleted categories still appear if they have active transactions in the range.

**Endpoint**

```http
GET /api/categories/statistic?period=CUSTOM&startDate=2026-01-01&endDate=2026-06-30
```

**Authentication**

```text
Required
```

**Query Parameter**

| Parameter   | Type                | Required          | Default   | Description |
| ----------- | ------------------- | ----------------- | --------- | ----------- |
| `period`    | String              | No                | `MONTHLY` | `YEARLY`, `MONTHLY`, `WEEKLY`, `TODAY`, or `CUSTOM` (case-insensitive) |
| `startDate` | Date (`YYYY-MM-DD`) | Only for `CUSTOM` | -         | Start of range (inclusive) |
| `endDate`   | Date (`YYYY-MM-DD`) | Only for `CUSTOM` | -         | End of range (inclusive, the whole day is counted) |

| `period`  | Range (inclusive) |
| --------- | ----------------- |
| `YEARLY`  | Jan 1 – Dec 31 of the current year |
| `MONTHLY` | 1st – last day of the current month |
| `WEEKLY`  | Last 7 days including today (today − 6 days – today) |
| `TODAY`   | Today only |
| `CUSTOM`  | `startDate` – `endDate` |

`startDate` and `endDate` are ignored unless `period=CUSTOM`. "Today" follows the business time zone (`app.timezone`, default `Asia/Jakarta`).

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "period": {
      "startDate": "2026-01-01",
      "endDate": "2026-06-30"
    },
    "totalExpense": 9250000,
    "categories": [
      { "categoryId": "c1a2...", "categoryName": "Food",      "totalAmount": 2750000, "percentage": 29.73, "transactionCount": 42 },
      { "categoryId": "c3b4...", "categoryName": "Transport", "totalAmount": 2000000, "percentage": 21.62, "transactionCount": 25 },
      { "categoryId": "c5d6...", "categoryName": "Shopping",  "totalAmount": 1870000, "percentage": 20.22, "transactionCount": 12 },
      { "categoryId": "c7e8...", "categoryName": "Bills",     "totalAmount": 1730000, "percentage": 18.70, "transactionCount": 8 },
      { "categoryId": "c9f0...", "categoryName": "Other",     "totalAmount": 900000,  "percentage": 9.73,  "transactionCount": 15 }
    ]
  },
  "message": "Expense statistics retrieved successfully",
  "errors": null,
  "paging": null
}
```

- `categories` is sorted by `totalAmount` descending, then `categoryName` ascending; categories with no expense in the range are not included.
- `percentage` = `totalAmount / totalExpense × 100`, rounded to 2 decimals (HALF_UP). Because of rounding, the sum can be off from 100 by ±0.01.
- With no transactions: `totalExpense` = `0` and `categories` = `[]`.

**Error Response**

**HTTP 400 Bad Request**

| Condition | `errors` |
| --------- | -------- |
| Invalid `period` value | `Invalid period: DAILY` |
| `period=CUSTOM` without both `startDate` and `endDate` | `startDate and endDate are required for CUSTOM period` |
| `startDate` after `endDate` | `startDate must be before or equal to endDate` |
| Invalid date format | `Parameter 'startDate' has invalid value: 2026-13-01` |

```json
{
  "data": null,
  "message": null,
  "errors": "startDate must be before or equal to endDate",
  "paging": null
}
```

**HTTP 401 Unauthorized** — token missing or invalid

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
| `transactionCode`  | String | Server-generated code, format `TRX-yyyyMMdd-NNN` (e.g. `TRX-20260831-001`) |

The date part is the **creation** date in the business timezone (`app.timezone`), not the transaction's `date`. `NNN` is a 3-digit sequence that resets every day and is shared by all users.

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "transactionName": "Monthly Salary",
    "transactionCode": "TRX-20260831-001",
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
| `search`  | String  | No       | Case-insensitive substring match on `transactionName` **or** `description` |
| `categoryId` | UUID | No      | Only transactions in this category. A category the user doesn't own just returns an empty list (no `404`) |
| `type`    | String  | No       | Category type: `INCOME`, `EXPENSE`, or `SAVING` (case-sensitive)       |
| `period`  | String  | No       | Date range preset, see table below. Case-insensitive. Default: `ALL`  |
| `startDate` | Date (`YYYY-MM-DD`) | Only for `period=MANUAL` | First day of the range (inclusive) |
| `endDate` | Date (`YYYY-MM-DD`) | Only for `period=MANUAL` | Last day of the range (inclusive). Must not be before `startDate` |
| `date`    | Date (`YYYY-MM-DD`) | No | Only transactions on that day (00:00:00–23:59:59). **Overrides** `period`, `startDate`, and `endDate` when present |
| `sort`    | String  | No       | Sort order, see table below. Default: `date` (newest first)           |

Filters are combined with AND, so `categoryId` together with a `type` that doesn't match that category returns an empty list.

**Period values** ("today" is in the business timezone, `app.timezone`, default `Asia/Jakarta`)

| `period`        | Range                                                          |
| --------------- | -------------------------------------------------------------- |
| `ALL` (default) | No date restriction                                            |
| `TODAY`         | Today only                                                     |
| `LAST_7_DAYS`   | Today and the 6 days before it                                 |
| `LAST_14_DAYS`  | Today and the 13 days before it                                |
| `MANUAL`        | `startDate` through `endDate`, both inclusive (both required)  |

`startDate` and `endDate` are ignored unless `period=MANUAL`. An empty `period` is treated as `ALL`.

**Sort values**

| `sort`             | Order                             |
| ------------------ | --------------------------------- |
| `date` (default)   | Transaction date, newest first    |
| `-date`            | Transaction date, oldest first    |
| `amount`           | Amount, highest first             |
| `-amount`          | Amount, lowest first              |
| `name`             | Transaction name, A→Z             |
| `-name`            | Transaction name, Z→A             |
| `category`         | Category name, A→Z                |
| `-category`        | Category name, Z→A                |
| `transactionCode`  | Transaction code, ascending       |
| `-transactionCode` | Transaction code, descending      |

Values are case-sensitive. Ties are always broken by `transactionCode` ascending, so pages stay stable. Note that for `date` and `amount`, the version *without* `-` is descending. That is the opposite of `name`, `category`, and `transactionCode`.

**Example**

```http
GET /api/transaction?skip=0&limit=10&search=salary&type=INCOME&sort=-amount
GET /api/transaction?skip=0&limit=10&categoryId=d290f1ee-6c54-4b01-90e6-d701748f0851&period=LAST_7_DAYS
GET /api/transaction?skip=0&limit=10&period=MANUAL&startDate=2026-08-01&endDate=2026-08-31
```

**Success Response**

**HTTP 200 OK**

```json
{
  "data": [
    {
      "transactionName": "Monthly Salary",
      "transactionCode": "TRX-20260831-001",
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

**Error Response**

**HTTP 400 Bad Request**

| Cause                          | `errors`                                              |
| ------------------------------ | ----------------------------------------------------- |
| `skip < 0`                     | `"Skip must be greater than or equal to 0"`           |
| `limit <= 0`                   | `"Limit must be greater than 0"`                      |
| Unknown `sort` value           | `"Invalid sort value: <value>"`                       |
| Unknown `period` value         | `"Invalid period: <value>"`                           |
| `period=MANUAL` without both `startDate` and `endDate` | `"startDate and endDate are required for MANUAL period"` |
| `endDate` before `startDate`   | `"endDate must not be before startDate"`              |
| Invalid `type`, `categoryId`, `date`, `startDate`, or `endDate` value | `"Parameter '<name>' has invalid value: <value>"` |

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
    "transactionCode": "TRX-20260831-001",
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

### Bulk Delete Transactions

Soft-deletes many transactions at once (`isDeleted = true`). Partial success: codes that are not found, belong to another user, or are already deleted are skipped and returned in `skippedCodes` — the rest are still deleted. Duplicate codes are counted once.

**Endpoint**

```http
DELETE /api/transaction/bulk
```

**Authentication**

```text
Required
```

**Request Body**

```json
{
  "transactionCodes": ["TRX-001", "TRX-002", "TRX-404"]
}
```

| Field              | Type     | Rules                          |
| ------------------ | -------- | ------------------------------ |
| `transactionCodes` | String[] | Required, 1–500 items, no blank |

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "deletedCount": 2,
    "deletedCodes": ["TRX-001", "TRX-002"],
    "skippedCodes": ["TRX-404"]
  },
  "message": "2 transactions deleted successfully",
  "errors": null,
  "paging": null
}
```

**Error Response**

**HTTP 400 Bad Request** — empty list, more than 500 items, or a blank code

**HTTP 401 Unauthorized**

---

### Bulk Update Transactions (Partial)

Applies the same field values to many transactions at once. Only non-null fields are changed; at least one field must be provided. Duplicate codes are de-duplicated. All-or-nothing: if any code is not found / deleted / owned by another user, or `categoryId` is not an active category of the user, nothing is updated.

**Endpoint**

```http
PATCH /api/transaction/bulk
```

**Authentication**

```text
Required
```

**Request Body**

```json
{
  "transactionCodes": ["TRX-20261001-001", "TRX-20261001-002"],
  "transactionName": "Gaji",
  "categoryId": "3f1c...",
  "date": "2026-10-01",
  "description": "Gaji Oktober"
}
```

| Field              | Type                | Rules                                  |
| ------------------ | ------------------- | -------------------------------------- |
| `transactionCodes` | String[]            | Required, 1–500 items, each not blank  |
| `transactionName`  | String              | Optional, max 100 chars                |
| `categoryId`       | UUID                | Optional, active category of user      |
| `date`             | Date (`YYYY-MM-DD`) | Optional                               |
| `description`      | String              | Optional                               |

**Success Response**

**HTTP 200 OK**

```json
{
  "message": "2 transactions updated successfully",
  "updatedCount": 2,
  "errors": null,
  "paging": null
}
```

**Error Response**

**HTTP 400 Bad Request** — empty list, more than 500 codes, blank code, name > 100 chars, or no field to update

**HTTP 401 Unauthorized**

**HTTP 404 Not Found** — `Transaction not found: TRX-404, ...` or `Category not found: <id>`

---

### Get Transaction Statistics (Summary)

Returns the authenticated user's financial summary for a period: total balance, total income, total expense, and total saving, each compared with the previous calendar month.

**Endpoint**

```http
GET /api/transaction/stat?startDate=2026-09-01&endDate=2026-09-30
```

**Authentication**

```text
Required
```

**Request Header**

```http
Authorization: Bearer <token>
```

**Query Parameters**

| Parameter   | Type                | Required | Description                                                         |
| ----------- | ------------------- | -------- | ------------------------------------------------------------------- |
| `startDate` | Date (`YYYY-MM-DD`) | No       | Start of period, inclusive. Default: first day of the current month |
| `endDate`   | Date (`YYYY-MM-DD`) | No       | End of period, inclusive (until 23:59:59). Default: last day of the current month |

"Current month" is determined in the business timezone (`app.timezone`, default `Asia/Jakarta`), not the server's timezone.

**Calculation Rules**

- Only the authenticated user's transactions with `isDeleted = false` are counted.
- Type is taken from the transaction's category (`INCOME` / `EXPENSE` / `SAVING`).
- `totalBalance.amount = income - expense + saving`. Saving is money added to the `SAVINGS` account, so it increases the balance. The export summary's "Total Balance" uses the same formula.
- Every `amount` has a floor of `0`. A negative balance (expense > income) comes back as `0`, and its `changePercentage` is calculated from that `0`. The previous month's value is not floored.
- Comparison period: the full calendar month before the month of `startDate` (e.g. `startDate=2026-09-10` compares against `2026-08-01`..`2026-08-31`).
- `changePercentage = (current - previous) * 100 / |previous|`, rounded `HALF_UP` to 2 decimals.
  - previous `0` and current `0` → `0.00`
  - previous `0` and current non-zero → `100.00`

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "totalBalance": {
      "amount": 10100000.00,
      "changePercentage": 31.17
    },
    "totalIncome": {
      "amount": 12100000.00,
      "changePercentage": 21.00
    },
    "totalExpense": {
      "amount": 2000000.00,
      "changePercentage": -13.04
    },
    "totalSaving": {
      "amount": 1500000.00,
      "changePercentage": 50.00
    }
  },
  "message": null,
  "errors": null,
  "paging": null
}
```

| Field          | Type   | Description                         |
| -------------- | ------ | ----------------------------------- |
| `totalBalance` | Object | Income − expense + saving for the period |
| `totalIncome`  | Object | Total `INCOME` for the period       |
| `totalExpense` | Object | Total `EXPENSE` for the period      |
| `totalSaving`  | Object | Total `SAVING` for the period       |

Each summary object:

| Field              | Type       | Description                                                                 |
| ------------------ | ---------- | --------------------------------------------------------------------------- |
| `amount`           | BigDecimal | Raw amount (not formatted); never negative, `0` when there are no transactions, never `null` |
| `changePercentage` | BigDecimal | Change vs. the previous calendar month, 2 decimals, negative means decrease |

**Error Response**

**HTTP 400 Bad Request** — `endDate` before `startDate` (including when only one parameter is given and the default for the other makes the range invalid)

```json
{
  "data": null,
  "message": null,
  "errors": "endDate must be after startDate",
  "paging": null
}
```

**HTTP 400 Bad Request** — invalid date format (e.g. `startDate=01-09-2026`)

**HTTP 401 Unauthorized** — missing or invalid token

```json
{
  "data": null,
  "message": null,
  "errors": "Unauthorized",
  "paging": null
}
```

---

### Get Transaction Statistic (Daily Chart)

Returns daily income and expense totals for the current month or the last 7 days. It's meant for charts. Days with no transactions are included with `0` values.

**Endpoint**

```http
GET /api/transaction/statistic?periode=Monthly
```

**Authentication**

```text
Required
```

**Query Parameters**

| Parameter | Type   | Required | Description                                                  |
| --------- | ------ | -------- | ------------------------------------------------------------ |
| `periode` | String | No       | `Monthly` or `Weekly` (case-sensitive). Default: `Monthly`   |

> Note: the parameter name is spelled `periode` (not `period`), and the values must be capitalized exactly as shown.

**Period Rules** ("today" is in the business timezone, `app.timezone`)

| `periode` | `startDate`                     | `endDate`                      |
| --------- | ------------------------------- | ------------------------------ |
| `Monthly` | First day of the current month  | Last day of the current month  |
| `Weekly`  | Today minus 6 days              | Today                          |

- `Monthly` returns one item for every day of the month, **including future days** (these are `0`).
- `Weekly` is a rolling 7-day window ending today, not a calendar week.
- Only the authenticated user's transactions with `isDeleted = false` are counted.
- `SAVING` transactions are not included in `income` or `expense`.
- `items` is ordered by date ascending.

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "period": "Weekly",
    "startDate": "2026-09-20",
    "endDate": "2026-09-26",
    "items": [
      { "date": "2026-09-20", "income": 0, "expense": 150000.00 },
      { "date": "2026-09-21", "income": 0, "expense": 0 },
      { "date": "2026-09-22", "income": 5000000.00, "expense": 75000.00 },
      { "date": "2026-09-23", "income": 0, "expense": 0 },
      { "date": "2026-09-24", "income": 0, "expense": 320000.00 },
      { "date": "2026-09-25", "income": 0, "expense": 0 },
      { "date": "2026-09-26", "income": 0, "expense": 45000.00 }
    ]
  },
  "message": null,
  "errors": null,
  "paging": null
}
```

| Field       | Type                | Description                                |
| ----------- | ------------------- | ------------------------------------------ |
| `period`    | String              | The resolved period (`Monthly` / `Weekly`)   |
| `startDate` | Date (`YYYY-MM-DD`) | First day of the range, inclusive          |
| `endDate`   | Date (`YYYY-MM-DD`) | Last day of the range, inclusive           |
| `items`     | Array               | One entry per day from `startDate` to `endDate` |

Each item:

| Field     | Type                | Description                          |
| --------- | ------------------- | ------------------------------------ |
| `date`    | Date (`YYYY-MM-DD`) | The day                              |
| `income`  | BigDecimal          | Sum of `INCOME` transactions that day  |
| `expense` | BigDecimal          | Sum of `EXPENSE` transactions that day |

**Error Response**

**HTTP 400 Bad Request**: `periode` is not `Monthly` or `Weekly`

```json
{
  "data": null,
  "message": null,
  "errors": "periode is not valid",
  "paging": null
}
```

**HTTP 401 Unauthorized**: missing or invalid token

---

## 4. Financial Setup & Accounts

Every user has exactly two accounts: `MAIN` ("Main Balance") and `SAVINGS` ("Savings"). They are created when a user registers (manual sign-up or Google). Accounts that are missing for older users are created the first time they are needed.

Balance rules:

- `openingBalance` is the money the user already had when they started using the app. It is set once through the initial setup and is never changed by transactions.
- The account of a transaction is decided by its category type. Clients never choose it:
  - `INCOME` and `EXPENSE` → `MAIN`
  - `SAVING` → `SAVINGS`. A saving adds money to `SAVINGS` and does **not** take it from `MAIN`.
- `MAIN.currentBalance = openingBalance + INCOME − EXPENSE + transfers in − transfers out`
- `SAVINGS.currentBalance = openingBalance + SAVING + transfers in − transfers out`
- Changing a category's type moves all of its transactions to the matching account. Balances are calculated when they are read, so they update immediately.
- To spend savings, transfer money from `SAVINGS` to `MAIN` (see Transfers).
- `totalBalance` is the sum of all accounts' `currentBalance`. Transfers never change it.
- Opening balances and transfers are **not** income, expense, or saving. They never appear in `/api/transaction/stat`, `/api/transaction/statistic`, `/api/categories/statistic`, or the export.

### Get Financial Setup

```http
GET /api/financial-setup
```

**Authentication**: Required

**HTTP 200 OK**

```json
{
  "data": {
    "setupCompleted": false,
    "accounts": [
      { "id": "uuid", "accountType": "MAIN", "name": "Main Balance", "openingBalance": 0, "currentBalance": 0 },
      { "id": "uuid", "accountType": "SAVINGS", "name": "Savings", "openingBalance": 0, "currentBalance": 0 }
    ],
    "totalBalance": 0
  }
}
```

### Complete Financial Setup

Sets the opening balances. This can be done **once** per user.

```http
PUT /api/financial-setup
```

**Authentication**: Required

| Field     | Type   | Required | Description                                          |
| --------- | ------ | -------- | ---------------------------------------------------- |
| `balance` | Number | No       | Opening balance of `MAIN`; omitted or `null` → `0`   |
| `savings` | Number | No       | Opening balance of `SAVINGS`; omitted or `null` → `0` |

Both values must be `>= 0` with at most 13 integer digits and 2 decimals. An empty body (or no body) completes the setup with both balances set to `0`.

```json
{ "balance": 5000000, "savings": 2000000 }
```

**HTTP 200 OK**: same shape as Get Financial Setup, with `setupCompleted: true`.

**HTTP 400 Bad Request**: negative value or invalid precision, e.g. `"errors": "Balance must not be negative"`

**HTTP 409 Conflict**: `"errors": "Financial setup has already been completed"`. This is returned when the setup was already completed or skipped. Opening balances are never overwritten.

### Skip Financial Setup

```http
POST /api/financial-setup/skip
```

**Authentication**: Required

Marks the setup as completed and leaves both opening balances at `0`. The call is idempotent: calling it again, or calling it after the setup is already completed, returns `200` with the current state and changes nothing.

---

## 5. Transfers

### Create Transfer

Moves money between the authenticated user's own `MAIN` and `SAVINGS` accounts. A transfer is not income or expense.

```http
POST /api/transfers
```

**Authentication**: Required

| Field           | Type   | Required | Description                                  |
| --------------- | ------ | -------- | -------------------------------------------- |
| `fromAccountId` | UUID   | Yes      | Source account (must belong to the user)     |
| `toAccountId`   | UUID   | Yes      | Destination account (must differ from source) |
| `amount`        | Number | Yes      | `> 0`, at most 13 integer digits and 2 decimals |
| `note`          | String | No       | Max 255 characters                           |

```json
{ "fromAccountId": "uuid-savings", "toAccountId": "uuid-main", "amount": 500000, "note": "Move savings to main" }
```

**HTTP 200 OK**

```json
{
  "data": {
    "id": "uuid",
    "fromAccountId": "uuid-savings",
    "toAccountId": "uuid-main",
    "amount": 500000,
    "note": "Move savings to main",
    "createdAt": "2026-10-24T10:15:30.123456"
  },
  "message": "Transfer created successfully"
}
```

| Status | When |
| ------ | ---- |
| `400`  | Missing field, amount `<= 0` or bad precision, or same source and destination (`"Source and destination accounts must be different"`) |
| `404`  | Either account does not exist or belongs to another user (`"Account not found"`) |
| `409`  | Source balance is lower than the amount (`"Insufficient balance in source account"`). Nothing is saved. |

Concurrent transfers from the same account are serialized, so the source account can never go below zero through transfers.

### Get Transfer List

```http
GET /api/transfers?skip=0&limit=10
```

**Authentication**: Required

Returns the user's transfers, newest first, using the same `paging` object as `GET /api/transaction`. `skip` defaults to `0` and `limit` defaults to `10`. If `skip < 0` or `limit <= 0`, the response is `400`.

---

## 6. Operations

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
- Transaction list filter `keyword` (use `search` instead)
- Custom date ranges on `GET /api/transaction/statistic` (only `Monthly` / `Weekly` presets)
- `204 No Content` responses (every endpoint returns `200 OK` with a JSON body, including deletes)

---

# HTTP Status Codes

| Status Code                 | Description                                                                    |
| ---------------------------- | -------------------------------------------------------------------------------- |
| `200 OK`                     | Request completed successfully (used for creates and deletes too — no `201`/`204`) |
| `400 Bad Request`            | Invalid request, validation error, or malformed path/query parameter            |
| `401 Unauthorized`           | Missing/invalid `Authorization` header or unknown token                         |
| `404 Not Found`               | Resource not found, or not owned by the authenticated user                      |
| `409 Conflict`                | Duplicate resource (email on register, name+type on category create), financial setup already completed, insufficient balance for a transfer |
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
