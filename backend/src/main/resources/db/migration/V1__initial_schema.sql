CREATE TABLE recipes
(
    id   UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    data JSONB        NOT NULL
);

CREATE TABLE user_recipes
(
    email     VARCHAR(255) NOT NULL,
    recipe_id UUID         NOT NULL,
    role      VARCHAR(255) NOT NULL CHECK (role IN ('OWNER', 'EDITOR')),
    PRIMARY KEY (email, recipe_id),
    FOREIGN KEY (recipe_id) REFERENCES recipes (id)
);