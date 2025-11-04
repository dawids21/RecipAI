-- Delete all existing shopping list items first (to avoid FK constraint violation)
DELETE
FROM shopping_list_items;

-- Delete all existing shopping lists (orphaned data, no real users yet)
DELETE
FROM shopping_lists;

-- Create shopping_list_permission table with composite PK (email, shopping_list_id)
CREATE TABLE shopping_list_permission
(
    email            VARCHAR(255) NOT NULL,
    shopping_list_id UUID         NOT NULL,
    role             VARCHAR(255) NOT NULL CHECK (role IN ('OWNER', 'EDITOR')),
    PRIMARY KEY (email, shopping_list_id),
    FOREIGN KEY (shopping_list_id) REFERENCES shopping_lists (id)
);
