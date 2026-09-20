# API Contract

## Base URL

```text
/api
```

---

## 1. Authentication

### Register

Create a new user account.

**Endpoint**

```http
POST /api/auth/register
```

**Authentication**

```text
Public
```

**Request Body**

```json
{
  "firstName": "ilham",
  "lastName":"suryana",
  "email": "ilham@example.com",
  "password": "password123"
}
```

**Success Response**

**HTTP 201 Created**

```json
{
  "data": {
    "username": "ilham",
    "email": "ilham@example.com"
  },
  "errors": null
}
```

**Error Response**

**HTTP 400 Bad Request**

```json
{
  "data": null,
  "errors": "Email is already registered"
}
```

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

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "token": "jwt-token",
    "expiresAt": "2026-09-01T12:00:00Z"
  },
  "errors": null
}
```

**Error Response**

**HTTP 401 Unauthorized**

```json
{
  "data": null,
  "errors": "Invalid email or password"
}
```

---

## 2. Income Management

### Create Income

Create a new income transaction.

**Endpoint**

```http
POST /api/incomes
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
  "amount": 5000000,
  "description": "Monthly Salary",
  "categoryId": "category-uuid",
  "date": "2026-08-31"
}
```

**Success Response**

**HTTP 201 Created**

```json
{
  "data": {
    "id": "income-uuid",
    "amount": 5000000,
    "description": "Monthly Salary",
    "categoryId": "category-uuid",
    "date": "2026-08-31"
  },
  "errors": null
}
```

---

### Get Income List

Retrieve a list of income transactions.

**Endpoint**

```http
GET /api/incomes
```

**Authentication**

```text
Required
```

**Query Parameters**

```text
page
size
category
startDate
endDate
```

**Example**

```http
GET /api/incomes?page=0&size=10&startDate=2026-08-01&endDate=2026-08-31
```

**Success Response**

**HTTP 200 OK**

```json
{
  "data": [
    {
      "id": "income-uuid",
      "amount": 5000000,
      "description": "Monthly Salary",
      "category": "Salary",
      "date": "2026-08-31"
    }
  ],
  "errors": null
}
```

---

## 3. Expense Management

### Create Expense

Create a new expense transaction.

**Endpoint**

```http
POST /api/expenses
```

**Authentication**

```text
Required
```

**Request Body**

```json
{
  "amount": 50000,
  "description": "Lunch",
  "categoryId": "category-uuid",
  "date": "2026-08-31"
}
```

**Success Response**

**HTTP 201 Created**

```json
{
  "data": {
    "id": "expense-uuid",
    "amount": 50000,
    "description": "Lunch",
    "categoryId": "category-uuid",
    "date": "2026-08-31"
  },
  "errors": null
}
```

---

### Get Expense List

Retrieve a list of expense transactions.

**Endpoint**

```http
GET /api/expenses
```

**Authentication**

```text
Required
```

**Query Parameters**

```text
page
size
category
startDate
endDate
```

**Example**

```http
GET /api/expenses?page=0&size=10&category=food
```

**Success Response**

**HTTP 200 OK**

```json
{
  "data": [
    {
      "id": "expense-uuid",
      "amount": 50000,
      "description": "Lunch",
      "category": "Food",
      "date": "2026-08-31"
    }
  ],
  "errors": null
}
```

---

## 4. Transaction Management

### Get Transaction Detail

Retrieve a specific transaction.

**Endpoint**

```http
GET /api/transactions/{transactionId}
```

**Authentication**

```text
Required
```

**Path Parameter**

| Parameter       | Type | Description            |
| --------------- | ---- | ---------------------- |
| `transactionId` | UUID | Transaction identifier |

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "id": "transaction-uuid",
    "type": "EXPENSE",
    "amount": 50000,
    "description": "Lunch",
    "category": "Food",
    "date": "2026-08-31"
  },
  "errors": null
}
```

---

### Update Transaction

Update an existing transaction.

**Endpoint**

```http
PUT /api/transactions/{transactionId}
```

**Authentication**

```text
Required
```

**Request Body**

```json
{
  "amount": 75000,
  "description": "Dinner",
  "categoryId": "category-uuid",
  "date": "2026-08-31"
}
```

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "id": "transaction-uuid",
    "type": "EXPENSE",
    "amount": 75000,
    "description": "Dinner",
    "category": "Food",
    "date": "2026-08-31"
  },
  "errors": null
}
```

---

### Delete Transaction

Delete a transaction.

**Endpoint**

```http
DELETE /api/transactions/{transactionId}
```

**Authentication**

```text
Required
```

**Success Response**

**HTTP 204 No Content**

```text
No response body
```

---

## 5. Transaction Categorization

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

**Request Body**

```json
{
  "name": "Food",
  "type": "EXPENSE"
}
```

**Success Response**

**HTTP 201 Created**

```json
{
  "data": {
    "id": "category-uuid",
    "name": "Food",
    "type": "EXPENSE"
  },
  "errors": null
}
```

---

### Get Categories

Retrieve available transaction categories.

**Endpoint**

```http
GET /api/categories
```

**Authentication**

```text
Required
```

**Success Response**

**HTTP 200 OK**

```json
{
  "data": [
    {
      "id": "category-uuid",
      "name": "Food",
      "type": "EXPENSE"
    },
    {
      "id": "category-uuid",
      "name": "Salary",
      "type": "INCOME"
    }
  ],
  "errors": null
}
```

---

## 6. Financial Summary

### Get Financial Summary

Retrieve a financial summary for a specific period.

**Endpoint**

```http
GET /api/financial-summary
```

**Authentication**

```text
Required
```

**Query Parameters**

| Parameter   | Type | Required | Description         |
| ----------- | ---- | -------- | ------------------- |
| `startDate` | Date | Yes      | Start of the period |
| `endDate`   | Date | Yes      | End of the period   |

**Example**

```http
GET /api/financial-summary?startDate=2026-08-01&endDate=2026-08-31
```

**Success Response**

**HTTP 200 OK**

```json
{
  "data": {
    "totalIncome": 5000000,
    "totalExpense": 2500000,
    "balance": 2500000,
    "period": {
      "startDate": "2026-08-01",
      "endDate": "2026-08-31"
    }
  },
  "errors": null
}
```

---

## 7. Transaction Search & Filtering

### Search Transactions

Search and filter transactions based on specific criteria.

**Endpoint**

```http
GET /api/transactions
```

**Authentication**

```text
Required
```

**Query Parameters**

| Parameter    | Type    | Required | Description           |
| ------------ | ------- | -------- | --------------------- |
| `keyword`    | String  | No       | Search by description |
| `type`       | String  | No       | `INCOME` or `EXPENSE` |
| `categoryId` | UUID    | No       | Filter by category    |
| `startDate`  | Date    | No       | Start date            |
| `endDate`    | Date    | No       | End date              |
| `page`       | Integer | No       | Page number           |
| `size`       | Integer | No       | Number of records     |

**Example**

```http
GET /api/transactions?keyword=food&type=EXPENSE&page=0&size=10
```

**Success Response**

**HTTP 200 OK**

```json
{
  "data": [
    {
      "id": "transaction-uuid",
      "type": "EXPENSE",
      "amount": 50000,
      "description": "Lunch",
      "category": "Food",
      "date": "2026-08-31"
    }
  ],
  "errors": null
}
```

---

# HTTP Status Codes

| Status Code                 | Description                               |
| --------------------------- | ----------------------------------------- |
| `200 OK`                    | Request completed successfully            |
| `201 Created`               | Resource successfully created             |
| `204 No Content`            | Request completed without a response body |
| `400 Bad Request`           | Invalid request or validation error       |
| `401 Unauthorized`          | Authentication is required or invalid     |
| `403 Forbidden`             | User does not have permission             |
| `404 Not Found`             | Resource not found                        |
| `409 Conflict`              | Resource conflict                         |
| `500 Internal Server Error` | Unexpected server error                   |

---

# Authentication

Protected endpoints require a valid JWT access token.

```http
Authorization: Bearer <access-token>
```

Endpoints that require authentication are marked as:

```text
Authentication: Required
```

Public endpoints are marked as:

```text
Authentication: Public
```
