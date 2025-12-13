CREATE TABLE recipe_images
(
    id      UUID PRIMARY KEY REFERENCES recipes (id) ON DELETE CASCADE,
    images  JSONB  NOT NULL,
    version BIGINT NOT NULL
);
