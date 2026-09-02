-- Flyway migration.
--
-- Filename format is mandatory and parsed, not decorative:
--   V1__create_customers_table.sql
--   ^  ^^                      ^
--   |  ||                      └─ .sql
--   |  |└─ description, underscores become spaces in the log
--   |  └─ TWO underscores separate version from description
--   └─ V = versioned (runs once, in order). R = repeatable, U = undo.
--
-- Flyway records each applied file in a `flyway_schema_history` table along with
-- a checksum. Editing an already-applied migration changes that checksum and the
-- next startup fails. Once a migration has run anywhere but your own machine,
-- it is immutable — fix things forward with V2, never by editing V1.

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

-- Indexes are declared here as well as in @Table(indexes = ...) on the entity.
-- Flyway is what actually creates them; the annotation exists so the mapping
-- documents them and `ddl-auto: validate` can check they are present.
CREATE INDEX idx_customers_risk_level ON customers (risk_level);
CREATE INDEX idx_customers_country_code ON customers (country_code);
