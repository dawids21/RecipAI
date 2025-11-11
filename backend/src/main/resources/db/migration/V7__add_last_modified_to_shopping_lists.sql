-- Add last_modified column to shopping_lists table
ALTER TABLE shopping_lists
    ADD COLUMN last_modified TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;