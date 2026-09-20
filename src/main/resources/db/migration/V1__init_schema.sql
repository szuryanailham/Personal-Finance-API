CREATE TABLE users (
    id UUID PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    token VARCHAR(255),
    token_expired_at BIGINT,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    transaction_code VARCHAR(255) NOT NULL,
    transaction_name VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    category_id UUID NOT NULL,
    amount NUMERIC(38, 2) NOT NULL,
    description VARCHAR(255),
    transaction_date TIMESTAMP NOT NULL,
    CONSTRAINT uk_transactions_transaction_code UNIQUE (transaction_code),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE INDEX idx_categories_user_id ON categories (user_id);
CREATE INDEX idx_transactions_user_id ON transactions (user_id);
CREATE INDEX idx_transactions_category_id ON transactions (category_id);
