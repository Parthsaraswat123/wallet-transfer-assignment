CREATE TABLE IF NOT EXISTS wallets (
    id VARCHAR(50) PRIMARY KEY,
    balance DECIMAL(18, 4) NOT NULL CHECK (balance >= 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS transfers (
    id VARCHAR(50) PRIMARY KEY,
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    from_wallet_id VARCHAR(50) NOT NULL,
    to_wallet_id VARCHAR(50) NOT NULL,
    amount DECIMAL(18, 4) NOT NULL CHECK (amount > 0),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (from_wallet_id) REFERENCES wallets(id),
    FOREIGN KEY (to_wallet_id) REFERENCES wallets(id)
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id VARCHAR(50) PRIMARY KEY,
    wallet_id VARCHAR(50) NOT NULL,
    transfer_id VARCHAR(50) NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('DEBIT', 'CREDIT')),
    amount DECIMAL(18, 4) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (wallet_id) REFERENCES wallets(id),
    FOREIGN KEY (transfer_id) REFERENCES transfers(id)
);

CREATE TABLE IF NOT EXISTS idempotency_records (
    "key" VARCHAR(100) PRIMARY KEY,
    response_status INTEGER NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
