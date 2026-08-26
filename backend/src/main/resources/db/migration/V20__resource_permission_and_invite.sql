CREATE TABLE resource_permission (
    email         VARCHAR(255) NOT NULL,
    resource_type VARCHAR(64)  NOT NULL,
    resource_id   UUID         NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('OWNER', 'EDITOR')),
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (email, resource_type, resource_id)
);
-- Serves getPermissions, ownerEmail and resourceDeleted, none of which know the email.
CREATE INDEX idx_resource_permission_resource ON resource_permission (resource_type, resource_id);
-- One OWNER per resource is an invariant the four old tables held by construction only.
CREATE UNIQUE INDEX uq_resource_permission_owner
    ON resource_permission (resource_type, resource_id) WHERE role = 'OWNER';

CREATE TABLE resource_invite (
    id            UUID PRIMARY KEY,
    resource_type VARCHAR(64)  NOT NULL,
    resource_id   UUID         NOT NULL,
    email         VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('OWNER', 'EDITOR')),
    invited_by    VARCHAR(255) NOT NULL,
    label         VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    -- Backs the "no second pending invite" rule at the storage layer, not just in code.
    CONSTRAINT uq_resource_invite_target UNIQUE (resource_type, resource_id, email)
);
CREATE INDEX idx_resource_invite_email    ON resource_invite (email);
CREATE INDEX idx_resource_invite_resource ON resource_invite (resource_type, resource_id);

INSERT INTO resource_permission (email, resource_type, resource_id, role)
SELECT email, 'SHOPPING_LIST', shopping_list_id, role FROM shopping_list_permission;
