ALTER TABLE sys_tenant_member
    DROP CHECK chk_tenant_member_role;

UPDATE sys_tenant_member
SET role = 'ADMIN'
WHERE role IN ('OWNER', 'ADMIN');

UPDATE sys_tenant_member
SET role = 'USER'
WHERE role = 'MEMBER';

ALTER TABLE sys_tenant_member
    ADD CONSTRAINT chk_tenant_member_role CHECK (role IN ('ADMIN', 'USER', 'OPERATOR'));
