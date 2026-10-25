-- Run ONCE per database, as the owner (postgres), in the Supabase SQL editor.
-- Creates the least-privilege role the Spring Boot API connects with. Replace the password placeholder
-- with a long random value held in your secret manager; never commit it.
--
-- Why BYPASSRLS: every app table has Row Level Security enabled with no policy for this role, because
-- authorisation is enforced by the API plus the permission-checking database functions. Without
-- BYPASSRLS the role would see no rows. If your Supabase plan does not allow BYPASSRLS on custom roles,
-- connect the API as the table owner instead (owners bypass RLS unless FORCE ROW LEVEL SECURITY is set)
-- and record that in your deployment notes.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'chasehorse_api') THEN
    CREATE ROLE chasehorse_api LOGIN PASSWORD 'CHANGE_ME_BEFORE_RUNNING' NOSUPERUSER NOCREATEDB NOCREATEROLE BYPASSRLS;
  END IF;
END $$;

GRANT USAGE ON SCHEMA app TO chasehorse_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA app TO chasehorse_api;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA app TO chasehorse_api;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA app TO chasehorse_api;
-- needed by app.actor_id()
GRANT USAGE ON SCHEMA auth TO chasehorse_api;
GRANT EXECUTE ON FUNCTION auth.uid() TO chasehorse_api;

-- Append-only tables are also protected by triggers; belt and braces:
REVOKE UPDATE, DELETE, TRUNCATE ON app.wallet_ledger_entries, app.audit_events, app.approval_decisions,
                                     app.task_status_history, app.account_approvals FROM chasehorse_api;

-- Keep future Flyway-created objects accessible (run as the migration owner):
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO chasehorse_api;
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT USAGE, SELECT ON SEQUENCES TO chasehorse_api;
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT EXECUTE ON FUNCTIONS TO chasehorse_api;
