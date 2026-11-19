
-- Add role to existing users.
ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER';

-- Restrict roles to supported values.
ALTER TABLE users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'));
