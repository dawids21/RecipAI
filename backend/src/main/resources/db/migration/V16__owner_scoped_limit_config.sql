INSERT INTO limit_config (id, resource, subject, kind, max_value, period)
VALUES (gen_random_uuid(), 'RECIPE',             NULL, 'STOCK', 5, NULL),
       (gen_random_uuid(), 'RECIPES_COLLECTION', NULL, 'STOCK', 2, NULL),
       (gen_random_uuid(), 'SHOPPING_LIST',      NULL, 'STOCK', 2, NULL);
