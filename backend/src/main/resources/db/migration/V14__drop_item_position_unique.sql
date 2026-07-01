-- Drop the uniqueness constraint on (shopping_list_id, position): concurrent
-- appends/reorders can legitimately collide on a position; ties are broken by
-- id at the read path instead.
ALTER TABLE shopping_list_items
    DROP CONSTRAINT uk_shopping_list_items_list_position;

-- Widen position scale for fractional-insertion headroom (~40 halvings per gap).
ALTER TABLE shopping_list_items
    ALTER COLUMN position TYPE NUMERIC(21, 12);
