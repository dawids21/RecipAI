CREATE TABLE limit_config
(
    id         UUID PRIMARY KEY,
    resource   VARCHAR(64)  NOT NULL,
    subject    VARCHAR(255),                      -- NULL = the default for this resource
    kind       VARCHAR(16)  NOT NULL CHECK (kind IN ('STOCK', 'FLOW')),
    max_value  INTEGER      NOT NULL CHECK (max_value >= 0),
    period     VARCHAR(16)           CHECK (period IN ('DAY', 'WEEK', 'MONTH')),
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_limit_config_resource_subject UNIQUE NULLS NOT DISTINCT (resource, subject),
    CONSTRAINT ck_limit_config_stock_has_no_period CHECK (kind <> 'STOCK' OR period IS NULL)
);

CREATE TABLE limit_usage
(
    resource     VARCHAR(64)  NOT NULL,
    subject      VARCHAR(255) NOT NULL,
    used         INTEGER      NOT NULL,
    period_start TIMESTAMP    NOT NULL,
    PRIMARY KEY (resource, subject)
);

INSERT INTO limit_config (id, resource, subject, kind, max_value, period)
VALUES (gen_random_uuid(), 'EXTRACTION', NULL, 'FLOW', 2, NULL);
