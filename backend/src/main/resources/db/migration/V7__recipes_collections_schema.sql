CREATE TABLE recipes_collections
(
    id   UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE recipes_collection_permission
(
    email                 VARCHAR(255) NOT NULL,
    recipes_collection_id UUID         NOT NULL,
    role                  VARCHAR(255) NOT NULL CHECK (role IN ('OWNER', 'EDITOR')),
    PRIMARY KEY (email, recipes_collection_id),
    FOREIGN KEY (recipes_collection_id) REFERENCES recipes_collections (id)
);