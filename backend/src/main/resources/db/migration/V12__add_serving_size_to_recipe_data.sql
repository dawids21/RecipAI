-- Backfill existing recipes.data JSONB with servingSize: 1
-- Only update recipes that don't already have a servingSize field

UPDATE recipes
SET data = jsonb_set(data, '{servingSize}', '1', true)
WHERE NOT (data ? 'servingSize');
