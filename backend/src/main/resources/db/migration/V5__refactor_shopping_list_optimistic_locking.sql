-- Delete existing data (as allowed by requirements)
DELETE
FROM shopping_list_items;
DELETE
FROM shopping_lists;

-- Add version column to shopping_lists
ALTER TABLE shopping_lists
    ADD COLUMN version BIGINT NOT NULL;

-- Remove fields from shopping_list_items
ALTER TABLE shopping_list_items DROP COLUMN checked;
ALTER TABLE shopping_list_items DROP COLUMN version;

-- Create new checkbox entity table
CREATE TABLE shopping_list_item_checkbox
(
    shopping_list_item_id UUID PRIMARY KEY,
    checked               BOOLEAN NOT NULL DEFAULT FALSE,
    version               BIGINT  NOT NULL,
    CONSTRAINT fk_shopping_list_item_checkbox_item_id
        FOREIGN KEY (shopping_list_item_id)
            REFERENCES shopping_list_items (id)
            ON DELETE CASCADE
);