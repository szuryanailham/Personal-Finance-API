# Personal Finance API

A REST API for tracking and managing personal finances — income, expenses, transaction categories, and financial summaries over a given period. Built with **Spring Boot** and **PostgreSQL**.

## Features

* User registration & login (authentication)
* Transaction recording (income / expense)
* Transaction category management (create, get, update, delete)
* Database migration history (Flyway info endpoint)
* Transaction summary & search *(planned, see [docs/apiContract.md](docs/apiContract.md))*

> Status: this project is under active development. The full list of planned features is in [docs/requirment.md](docs/requirment.md).

## Tech Stack

* Java 21
* Spring Boot (Web MVC, Data JPA, Validation)
* PostgreSQL
* Flyway (database migration)
* Lombok
* Maven

## Prerequisites

* JDK 21+
* Maven (or use the bundled `./mvnw`)
* Docker & Docker Compose (to run PostgreSQL)

## Running Locally

For automated testing and VPS deployment from `release/v.0.1`, follow the
[CI/CD setup guide](docs/deployment.md).

1. Clone this repository.

2. Start the PostgreSQL database via Docker Compose:

   ```bash
   docker-compose up -d
   ```

3. Adjust the database connection settings in `src/main/resources/application.properties` if needed (default: `localhost:5432/personal-finance`).

4. Run the application:

   ```bash
   ./mvnw spring-boot:run
   ```

   Database migrations (Flyway) run automatically on startup.

5. The application will be available at `http://localhost:8080`.

## Available Endpoints

| Method | Endpoint               | Description                | Auth     |
| ------ | ----------------------- | --------------------------- | -------- |
| POST   | `/api/auth/sign-up`     | Register a new user         | Public   |
| POST   | `/api/auth/login`       | Log in a user                | Public   |
| POST   | `/api/auth/google`      | Log in / sign up with a Google ID token (`{"idToken": "..."}`); requires `GOOGLE_CLIENT_IDS` | Public   |
| POST   | `/api/transaction`      | Create a new transaction    | Required |
| GET    | `/api/transaction/{transactionCode}` | Get transaction detail | Required |
| GET    | `/api/transaction`      | List transactions (paginated) | Required |
| PATCH  | `/api/transaction/{transactionCode}` | Update a transaction   | Required |
| DELETE | `/api/transaction/{transactionCode}` | Delete a transaction (soft delete) | Required |
| POST   | `/api/categories`       | Create a new category       | Required |
| GET    | `/api/categories/{id}`  | Get category detail         | Required |
| GET    | `/api/categories`       | List categories             | Required |
| PUT    | `/api/categories/{id}`  | Update a category           | Required |
| DELETE | `/api/categories/{id}`  | Delete a category           | Required |
| GET    | `/api/flyway/info`      | Database migration status   | -        |

The full API contract (including planned endpoints such as financial summary and transaction search) is in [docs/apiContract.md](docs/apiContract.md).

## Project Structure

```text
src/main/java/com/ilham/personal_finance_api/
├── controller/   # REST controllers
├── services/     # Business logic
├── repository/   # Spring Data JPA repositories
├── entity/       # JPA entities
├── model/        # Request/response DTOs
├── exception/    # Custom exceptions
├── resolver/     # Custom argument resolvers
└── security/     # Security utilities
```

Database migrations live in `src/main/resources/db/migration`.

The separate Artoku SEO blog schema, ERD, provisioning instructions, and query
examples are documented in [Blog database design](docs/blog-database-design.md).
Its migrations live in `src/main/resources/db/blog-migration` and must run only
against the dedicated `artoku_blog` database using a separate Flyway invocation.
CMS registration, login, session verification, and logout are available through
the optional blog module: [configuration and API contract](docs/blog-auth-api.md).

## Configuration Note

`application.properties` currently stores database credentials directly for local development. Before using this in any other environment (staging/production), move sensitive values to environment variables, e.g.:

```properties
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

## License

Not yet specified.

### Authentication methods and email uniqueness

Manual registration rejects any registered email with HTTP 409. Google Sign-In
verifies the ID token and email, then reuses the account with the same email,
regardless of its recorded authentication provider. This preserves Google login
for legacy accounts and allows existing password users to sign in with Google.
Existing profiles, passwords, providers, and financial data are preserved;
password login remains available for password accounts. New Google users are
created with provider `GOOGLE`. Email lookup and database uniqueness are
case-insensitive, including concurrent registrations and accounts created while
a manual-registration OTP is pending.

Migration V7 adds `users.auth_provider` (`PASSWORD` or `GOOGLE`). Existing rows
are assigned `PASSWORD` because the old schema did not record the provider.
These accounts can still sign in with a verified Google token; no provider
backfill is required. Resolve any existing emails differing only by case before
applying V7; its unique index deliberately fails rather than merging or deleting
accounts.


### Password reset

`POST /api/auth/forgot-password` accepts `{"email":"user@example.com"}` and sends a reset link using the existing SMTP settings. Email is normalized by trimming and lowercasing, as in registration. Only persisted accounts are eligible: pending sign-ups return 404 until registration OTP verification creates the account. Legacy password accounts remain eligible. Google accounts return 400 directing users to Google recovery.

Responses use `WebResponse`: 200 with `message: "Password reset email sent"`; 400 for invalid input or Google accounts; 404 for unknown/pending email; 503 with a safe error if email delivery fails. Email must be nonblank, valid, and at most 100 characters.

`POST /api/auth/reset-password` accepts `{"token":"<token from email>","newPassword":"new secret"}`. Password must be nonblank, at most 100 characters and at most 72 UTF-8 bytes; whitespace is preserved. Returns 200 with `message: "Password reset successfully, please log in"`, or 400 for invalid input, unknown, expired, replaced, or already consumed tokens. Errors use the envelope's `errors` field; successful reset responses contain no credentials or user details.

Links expire exactly 15 minutes after issuance and are single use. Requesting another link replaces the previous link; failed SMTP delivery preserves the previous link. Successful reset revokes the existing bearer session and requires normal login. No additional OTP is required: registration verifies initial mailbox ownership, and the reset link proves current mailbox access.

Set `PASSWORD_RESET_URL` to the frontend reset page in production (default `http://localhost:3000/reset-password`). It must be an absolute HTTP(S) URL without credentials or a fragment. The backend appends/replaces the `token` query parameter; the frontend submits it to the reset endpoint. Frontend implementation is separate. Only a SHA-256 token hash is stored in PostgreSQL.

CMS blog category and tag CRUD: [API contract and Postman collection](docs/blog-taxonomy-api.md).

Create artikel blog dengan kategori dan tag: [API contract](docs/blog-post-api.md).
