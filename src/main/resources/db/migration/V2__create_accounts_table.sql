CREATE TABLE accounts
(
    id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(34) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance NUMERIC(19,4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    opened_at DATE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE INDEX idx_accounts_customer_id ON accounts (customer_id);
