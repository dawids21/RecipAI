-- Add created_at columns as nullable first
ALTER TABLE recipes
    ADD COLUMN created_at TIMESTAMP;
ALTER TABLE shopping_lists
    ADD COLUMN created_at TIMESTAMP;
ALTER TABLE recipes_collections
    ADD COLUMN created_at TIMESTAMP;

-- Backfill existing records with stable timestamps (one second apart, ordered by UUID)
DO
$$
    DECLARE
        base_time TIMESTAMP := '2025-01-01 00:00:00';
        counter   INT       := 0;
        rec       RECORD;
    BEGIN
        -- Backfill recipes
        FOR rec IN (SELECT id FROM recipes ORDER BY id)
            LOOP
                UPDATE recipes SET created_at = base_time + (counter || ' seconds')::INTERVAL WHERE id = rec.id;
                counter := counter + 1;
            END LOOP;

        -- Reset counter and backfill shopping_lists
        counter := 0;
        FOR rec IN (SELECT id FROM shopping_lists ORDER BY id)
            LOOP
                UPDATE shopping_lists SET created_at = base_time + (counter || ' seconds')::INTERVAL WHERE id = rec.id;
                counter := counter + 1;
            END LOOP;

        -- Reset counter and backfill recipes_collections
        counter := 0;
        FOR rec IN (SELECT id FROM recipes_collections ORDER BY id)
            LOOP
                UPDATE recipes_collections
                SET created_at = base_time + (counter || ' seconds')::INTERVAL
                WHERE id = rec.id;
                counter := counter + 1;
            END LOOP;
    END
$$;

-- Set NOT NULL constraint
ALTER TABLE recipes
    ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE shopping_lists
    ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE recipes_collections
    ALTER COLUMN created_at SET NOT NULL;

-- Set DEFAULT for future inserts
ALTER TABLE recipes
    ALTER COLUMN created_at SET DEFAULT NOW();
ALTER TABLE shopping_lists
    ALTER COLUMN created_at SET DEFAULT NOW();
ALTER TABLE recipes_collections
    ALTER COLUMN created_at SET DEFAULT NOW();

-- Add indexes for performance
CREATE INDEX idx_recipes_created_at ON recipes (created_at);
CREATE INDEX idx_shopping_lists_created_at ON shopping_lists (created_at);
CREATE INDEX idx_recipes_collections_created_at ON recipes_collections (created_at);
