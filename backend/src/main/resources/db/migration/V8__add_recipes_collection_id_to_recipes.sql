-- Add nullable recipes_collection_id column to recipes table
ALTER TABLE recipes
    ADD COLUMN recipes_collection_id UUID;

-- Add foreign key constraint with ON DELETE SET NULL
ALTER TABLE recipes
    ADD CONSTRAINT fk_recipes_collection_id
        FOREIGN KEY (recipes_collection_id)
            REFERENCES recipes_collections (id)
            ON DELETE SET NULL;