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

## Configuration Note

`application.properties` currently stores database credentials directly for local development. Before using this in any other environment (staging/production), move sensitive values to environment variables, e.g.:

```properties
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

## License

Not yet specified.
