INSERT INTO resource_permission (email, resource_type, resource_id, role)
SELECT email, 'RECIPE', recipe_id, role FROM recipe_permission;
