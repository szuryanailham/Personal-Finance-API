-- Akun MAIN & SAVINGS per user, saldo awal (opening balance), dan transfer internal antar akun.

-- NULL = user belum menyelesaikan / melewati setup awal
ALTER TABLE users
ADD COLUMN financial_setup_completed_at TIMESTAMP;

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    opening_balance NUMERIC(38, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_accounts_user_account_type UNIQUE (user_id, account_type),
    CONSTRAINT ck_accounts_account_type CHECK (account_type IN ('MAIN', 'SAVINGS')),
    CONSTRAINT ck_accounts_opening_balance CHECK (opening_balance >= 0)
);

-- Backfill: setiap user lama mendapat akun MAIN & SAVINGS dengan saldo awal 0
INSERT INTO accounts (id, user_id, name, account_type, opening_balance)
SELECT gen_random_uuid(), u.id, 'Main Balance', 'MAIN', 0
FROM users u
ON CONFLICT (user_id, account_type) DO NOTHING;

INSERT INTO accounts (id, user_id, name, account_type, opening_balance)
SELECT gen_random_uuid(), u.id, 'Savings', 'SAVINGS', 0
FROM users u
ON CONFLICT (user_id, account_type) DO NOTHING;

-- Transaksi tidak menyimpan akun: akun ditentukan oleh type category
-- (INCOME / EXPENSE -> MAIN, SAVING -> SAVINGS), jadi transaksi lama tidak perlu di-backfill.

CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    from_account_id UUID NOT NULL,
    to_account_id UUID NOT NULL,
    amount NUMERIC(38, 2) NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_transfers_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_transfers_from_account FOREIGN KEY (from_account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_transfers_to_account FOREIGN KEY (to_account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    CONSTRAINT ck_transfers_amount CHECK (amount > 0),
    CONSTRAINT ck_transfers_different_accounts CHECK (from_account_id <> to_account_id)
);

CREATE INDEX idx_transfers_user_id ON transfers (user_id);
CREATE INDEX idx_transfers_from_account_id ON transfers (from_account_id);
CREATE INDEX idx_transfers_to_account_id ON transfers (to_account_id);
