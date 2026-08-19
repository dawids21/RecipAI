-- Rebuilds limit_usage for the owner-scoped resources from their owning module's permission table.
-- Both the rollout seed and the drift repair for a missed release.
--
-- Subjects whose effective configuration is FLOW are excluded from both statements: for them `used`
-- means "consumed this period" and `period_start` anchors a window, neither of which survives being
-- overwritten with a stock count. "Effective" mirrors LimitConfigRepository.resolve — the subject's
-- own override if one exists, otherwise the resource default (subject IS NULL) — so flipping a
-- resource's default to FLOW spares every subject that has no override.

-- RECIPE
DELETE FROM limit_usage u
 WHERE u.resource = 'RECIPE'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject = u.subject),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW';

INSERT INTO limit_usage (resource, subject, used, period_start)
SELECT 'RECIPE', p.email, COUNT(*), now()
  FROM recipe_permission p
 WHERE p.role = 'OWNER'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'RECIPE' AND c.subject = p.email),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'RECIPE' AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW'
 GROUP BY p.email
    ON CONFLICT (resource, subject) DO NOTHING;

-- RECIPES_COLLECTION
DELETE FROM limit_usage u
 WHERE u.resource = 'RECIPES_COLLECTION'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject = u.subject),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW';

INSERT INTO limit_usage (resource, subject, used, period_start)
SELECT 'RECIPES_COLLECTION', p.email, COUNT(*), now()
  FROM recipes_collection_permission p
 WHERE p.role = 'OWNER'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'RECIPES_COLLECTION' AND c.subject = p.email),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'RECIPES_COLLECTION' AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW'
 GROUP BY p.email
    ON CONFLICT (resource, subject) DO NOTHING;

-- SHOPPING_LIST
DELETE FROM limit_usage u
 WHERE u.resource = 'SHOPPING_LIST'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject = u.subject),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW';

INSERT INTO limit_usage (resource, subject, used, period_start)
SELECT 'SHOPPING_LIST', p.email, COUNT(*), now()
  FROM shopping_list_permission p
 WHERE p.role = 'OWNER'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'SHOPPING_LIST' AND c.subject = p.email),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'SHOPPING_LIST' AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW'
 GROUP BY p.email
    ON CONFLICT (resource, subject) DO NOTHING;

-- MEAL_PLAN
DELETE FROM limit_usage u
 WHERE u.resource = 'MEAL_PLAN'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject = u.subject),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW';

INSERT INTO limit_usage (resource, subject, used, period_start)
SELECT 'MEAL_PLAN', p.email, COUNT(*), now()
  FROM meal_plan_permissions p
 WHERE p.role = 'OWNER'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'MEAL_PLAN' AND c.subject = p.email),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'MEAL_PLAN' AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW'
 GROUP BY p.email
    ON CONFLICT (resource, subject) DO NOTHING;
