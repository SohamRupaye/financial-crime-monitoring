CREATE TABLE alerts
(
    id                 BIGSERIAL PRIMARY KEY,
    alert_reference    VARCHAR(32) NOT NULL UNIQUE,
    -- UNIQUE: re-scoring a transaction must not stack a second alert onto the
    -- same finding.
    risk_assessment_id BIGINT      NOT NULL UNIQUE,
    status             VARCHAR(20) NOT NULL,
    created_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    FOREIGN KEY (risk_assessment_id) REFERENCES risk_assessments (id)
);

-- The analyst queue is "everything not yet closed", so status is what gets
-- filtered on.
CREATE INDEX idx_alerts_status ON alerts (status);
