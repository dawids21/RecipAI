INSERT INTO resource_permission (email, resource_type, resource_id, role)
SELECT email, 'RECIPES_COLLECTION', recipes_collection_id, role FROM recipes_collection_permission;
