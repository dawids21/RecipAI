-- shopping_list_permission is otherwise reachable only through its composite PK
-- (email, shopping_list_id); lookups by list alone — owner resolution on every item
-- create and delete — need an index of their own.
CREATE INDEX idx_shopping_list_permission_list_id ON shopping_list_permission (shopping_list_id);
