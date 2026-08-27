INSERT INTO resource_permission (email, resource_type, resource_id, role)
SELECT email, 'MEAL_PLAN', plan_id, role FROM meal_plan_permissions;
