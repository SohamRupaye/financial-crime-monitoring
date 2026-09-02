-- Flyway checksums every applied migration, so editing one that has already run
-- fails the next startup. Once a migration has run anywhere but your own
-- machine it is immutable: fix forward with a new version, never by editing.

CREATE TABLE customers
(
    id                 BIGSERIAL PRIMARY KEY,
    customer_reference VARCHAR(32)  NOT NULL UNIQUE,
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    email              VARCHAR(255) NOT NULL UNIQUE,
    date_of_birth      DATE         NOT NULL,
    country_code       VARCHAR(2)   NOT NULL,
    risk_level         VARCHAR(20)  NOT NULL,
    created_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

-- Also declared in @Table(indexes = ...) on the entity. Flyway is what creates
-- them; the annotation is there so the mapping documents them.
CREATE INDEX idx_customers_risk_level ON customers (risk_level);
CREATE INDEX idx_customers_country_code ON customers (country_code);
