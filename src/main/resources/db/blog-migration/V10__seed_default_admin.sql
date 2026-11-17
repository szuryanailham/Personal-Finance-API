-- Bootstrap one CMS admin only when no ADMIN assignment exists.
-- Login: super@admin.com / superadmin2026! (BCrypt cost 12).
-- An existing email is never promoted or given a new password.
WITH default_admin AS (
    INSERT INTO users (display_name, email, password_hash, status)
    SELECT 'Admin', 'super@admin.com',
           '$2a$12$6zStA4vvqJAUpfEJQCTyCeku1iq2DK5iknRKokyX.dD.p1eDpelR6',
           'ACTIVE'
    WHERE NOT EXISTS (
        SELECT 1 FROM user_roles ur
        JOIN roles r ON r.id = ur.role_id
        WHERE r.name = 'ADMIN'
    )
    ON CONFLICT DO NOTHING
    RETURNING id
)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM default_admin u CROSS JOIN roles r
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
