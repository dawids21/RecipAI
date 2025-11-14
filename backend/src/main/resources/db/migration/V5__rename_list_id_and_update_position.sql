-- Rename column from list_id to shopping_list_id
ALTER TABLE shopping_list_items
    RENAME COLUMN list_id TO shopping_list_id;

-- Update foreign key constraint name to match new column name
ALTER TABLE shopping_list_items
DROP
CONSTRAINT fk_shopping_list_items_list_id;

ALTER TABLE shopping_list_items
    ADD CONSTRAINT fk_shopping_list_items_shopping_list_id
        FOREIGN KEY (shopping_list_id)
            REFERENCES shopping_lists (id)
            ON DELETE CASCADE;

-- Change position from INT to NUMERIC with higher precision
-- Old: INT (e.g., 1, 2, 3)
-- New: NUMERIC(15, 6) (e.g., 1.000000, 2.000000, 3.000000)
ALTER TABLE shopping_list_items
ALTER
COLUMN position TYPE NUMERIC(15, 6);

-- Add unique constraint on (shopping_list_id, position)
ALTER TABLE shopping_list_items
    ADD CONSTRAINT uk_shopping_list_items_list_position
        UNIQUE (shopping_list_id, position);