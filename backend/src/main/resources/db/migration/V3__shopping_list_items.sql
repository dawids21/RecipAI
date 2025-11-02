CREATE TABLE shopping_list_items
(
    id       UUID PRIMARY KEY,
    list_id  UUID         NOT NULL,
    name     VARCHAR(255) NOT NULL,
    quantity NUMERIC(12, 3) NULL,
    unit     VARCHAR(64) NULL,
    checked  BOOLEAN      NOT NULL DEFAULT FALSE,
    position INT          NOT NULL,
    version  BIGINT       NOT NULL,
    CONSTRAINT fk_shopping_list_items_list_id
        FOREIGN KEY (list_id)
            REFERENCES shopping_lists (id)
            ON DELETE CASCADE
);
