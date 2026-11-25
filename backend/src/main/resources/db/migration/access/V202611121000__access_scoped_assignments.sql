-- Scoped role assignments (Timetia ADR 0004). A role that carries policy-decided permissions has a scope type, and each
-- assignment of it names the place it applies to. Platform roles keep no scope type and every existing assignment
-- keeps working: it gets the all-zero scope id, which means "not limited to a place".

-- GLOBAL, or a kind a module declares (DEPARTMENT, DIVISION, BATCH for Timetia). NULL for platform roles.
ALTER TABLE role ADD COLUMN scope_type VARCHAR(20) NULL;

-- The all-zero id marks an assignment without a place: every platform role, and a role whose scope type is GLOBAL.
-- A person can hold the same role in several places, so the place is part of the key.
ALTER TABLE role_assignment
    ADD COLUMN scope_id BINARY(16) NOT NULL DEFAULT (UNHEX('00000000000000000000000000000000')) AFTER role_id,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (tenant_id, user_id, role_id, scope_id);
