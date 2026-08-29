CREATE TABLE risk_assessments
(
    id             BIGSERIAL PRIMARY KEY,
    -- UNIQUE, not just a foreign key: one assessment per transaction.
    transaction_id BIGINT      NOT NULL UNIQUE,
    score          INTEGER     NOT NULL,
    risk_level     VARCHAR(20) NOT NULL,
    assessed_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT chk_risk_assessments_score_range CHECK (score BETWEEN 0 AND 100)
);

CREATE INDEX idx_risk_assessments_risk_level ON risk_assessments (risk_level);
CREATE INDEX idx_risk_assessments_score ON risk_assessments (score);

CREATE TABLE risk_rule_results
(
    id                 BIGSERIAL PRIMARY KEY,
    risk_assessment_id BIGINT      NOT NULL,
    rule_code          VARCHAR(30) NOT NULL,
    triggered          BOOLEAN     NOT NULL,
    points             INTEGER     NOT NULL,
    -- Null when the rule did not fire; there is nothing to explain.
    reason             VARCHAR(500),
    created_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    -- ON DELETE CASCADE matches orphanRemoval on the entity, so the two agree
    -- about what happens to results when an assessment goes.
    FOREIGN KEY (risk_assessment_id) REFERENCES risk_assessments (id) ON DELETE CASCADE,
    -- A rule may only report once per assessment. Without this, a duplicated
    -- rule code would double-count into the score with nothing to show it.
    CONSTRAINT uq_risk_rule_results_assessment_code UNIQUE (risk_assessment_id, rule_code),
    CONSTRAINT chk_risk_rule_results_points CHECK (points >= 0)
);

CREATE INDEX idx_risk_rule_results_assessment ON risk_rule_results (risk_assessment_id);
CREATE INDEX idx_risk_rule_results_rule_code ON risk_rule_results (rule_code);
