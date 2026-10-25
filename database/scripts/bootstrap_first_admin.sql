-- ONE-TIME: make the first administrator. Run as the database owner in the SQL editor.
-- 1) The person must first sign up in Supabase Auth (so a row exists in auth.users).
-- 2) Replace ADMIN_EMAIL_HERE (3 places), run, and check the result.
-- Normal role changes afterwards go through the API (admin only). The flag app.bootstrap exists only
-- inside this transaction and is the sole way to create an admin without an existing admin.
BEGIN;
SELECT set_config('app.bootstrap', 'on', true);

INSERT INTO app.profiles (id, email, display_name, account_status)
SELECT id, email, 'Administrator', 'active' FROM auth.users WHERE lower(email) = lower('ADMIN_EMAIL_HERE')
ON CONFLICT (id) DO UPDATE SET account_status = 'active';

INSERT INTO app.user_roles (user_id, role)
SELECT id, 'admin' FROM auth.users WHERE lower(email) = lower('ADMIN_EMAIL_HERE')
ON CONFLICT DO NOTHING;

SELECT p.id, p.email, p.account_status, array_agg(r.role) AS roles
  FROM app.profiles p JOIN app.user_roles r ON r.user_id = p.id
 WHERE lower(p.email) = lower('ADMIN_EMAIL_HERE') GROUP BY p.id;
COMMIT;
