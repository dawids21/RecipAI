CREATE TABLE meal_plans
(
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    color      VARCHAR(7)   NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE TABLE meal_plan_permissions
(
    email   VARCHAR(255) NOT NULL,
    plan_id UUID         NOT NULL REFERENCES meal_plans (id),
    role    VARCHAR(255) NOT NULL CHECK (role IN ('OWNER', 'EDITOR')),
    PRIMARY KEY (email, plan_id)
);

CREATE TABLE meal_plan_entries
(
    id               BIGSERIAL PRIMARY KEY,
    plan_id          UUID      NOT NULL REFERENCES meal_plans (id) ON DELETE CASCADE,
    date             DATE      NOT NULL,
    recipe_id        UUID,
    placeholder_text VARCHAR(255),
    serving_size     INTEGER,
    created_at       TIMESTAMP NOT NULL
);

CREATE INDEX idx_meal_plans_created_at ON meal_plans (created_at);
CREATE INDEX idx_meal_plan_entries_plan_date ON meal_plan_entries (plan_id, date);
