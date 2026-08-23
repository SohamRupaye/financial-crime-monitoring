CREATE TABLE transactions
(
    id                          BIGSERIAL PRIMARY KEY,
    transaction_reference       VARCHAR(32)    NOT NULL UNIQUE,
    account_id                  BIGINT         NOT NULL,
    transaction_type            VARCHAR(30)    NOT NULL,
    amount                      NUMERIC(19, 4) NOT NULL,
    currency                    VARCHAR(3)     NOT NULL,
    counterparty_account_number VARCHAR(34),
    counterparty_country        VARCHAR(2)     NOT NULL,
    occurred_at                 TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at                  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    FOREIGN KEY (account_id) REFERENCES accounts (id),
    -- The rules engine assumes a positive amount; a zero or negative one would
    -- score as harmless. Enforced here so bad data cannot exist at all, not just
    -- be rejected by @Positive on the request DTO.
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)
);

-- Covers "this account's activity in the last N minutes, newest first", which is
-- every velocity and structuring lookback.
CREATE INDEX idx_transactions_account_occurred_at
    ON transactions (account_id, occurred_at DESC);

CREATE INDEX idx_transactions_occurred_at ON transactions (occurred_at DESC);
CREATE INDEX idx_transactions_amount ON transactions (amount);
