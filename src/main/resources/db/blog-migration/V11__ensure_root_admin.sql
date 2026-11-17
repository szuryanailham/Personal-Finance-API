-- Provision the designated CMS administrator, including existing accounts.
-- Applied once by Flyway: later password changes survive application restarts.
-- Login: superadmin@gmail.com / superadmin2026! (BCrypt cost 12).
WITH root_admin AS (
    INSERT INTO users (display_name, email, password_hash, status)
    VALUES ('Admin', 'superadmin@gmail.com',
            '$2a$12$6zStA4vvqJAUpfEJQCTyCeku1iq2DK5iknRKokyX.dD.p1eDpelR6',
            'ACTIVE')
    ON CONFLICT (lower(email)) DO UPDATE
    SET password_hash = EXCLUDED.password_hash,
        status = EXCLUDED.status
    RETURNING id
)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM root_admin u CROSS JOIN roles r
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
