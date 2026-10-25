-- Database behaviour tests. Run via database/tests/run.sh against a THROWAWAY database.
-- Each check records PASS/FAIL in t.results; run.sh fails if any FAIL exists.
\set ON_ERROR_STOP on
-- Deliberately NOT including schema app: the API calls these functions with the default search_path,
-- so the suite proves they never depend on it.
SET search_path = public;

DROP SCHEMA IF EXISTS t CASCADE;
CREATE SCHEMA t;
CREATE TABLE t.results (n serial, name text, ok boolean, detail text);

CREATE FUNCTION t.as_user(u uuid) RETURNS void LANGUAGE sql AS $$ SELECT set_config('app.actor_id', u::text, false) $$;
CREATE FUNCTION t.as_nobody() RETURNS void LANGUAGE sql AS $$ SELECT set_config('app.actor_id', '', false) $$;

CREATE FUNCTION t.check(p_name text, p_cond boolean, p_detail text DEFAULT '') RETURNS void LANGUAGE plpgsql AS $$
BEGIN INSERT INTO t.results(name, ok, detail) VALUES (p_name, coalesce(p_cond, false), p_detail); END $$;

-- statement must FAIL with the given SQLSTATE
CREATE FUNCTION t.expect_fail(p_name text, p_sql text, p_state text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN
    EXECUTE p_sql;
    INSERT INTO t.results(name, ok, detail) VALUES (p_name, false, 'expected error ' || p_state || ' but statement succeeded');
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO t.results(name, ok, detail) VALUES (p_name, SQLSTATE = p_state, 'got ' || SQLSTATE || ': ' || SQLERRM);
  END;
END $$;

CREATE FUNCTION t.expect_ok(p_name text, p_sql text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN
    EXECUTE p_sql;
    INSERT INTO t.results(name, ok, detail) VALUES (p_name, true, '');
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO t.results(name, ok, detail) VALUES (p_name, false, SQLSTATE || ': ' || SQLERRM);
  END;
END $$;

-- ---------------------------------------------------------------------------
-- Fixtures (created by the DB owner under the explicit bootstrap flag)
-- ---------------------------------------------------------------------------
SELECT set_config('app.bootstrap', 'on', false);
INSERT INTO auth.users (id, email) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'admin@t.test'),
 ('a0000000-0000-0000-0000-000000000002', 'manager@t.test'),
 ('a0000000-0000-0000-0000-000000000003', 'ops@t.test'),
 ('a0000000-0000-0000-0000-000000000004', 'ops2@t.test'),
 ('a0000000-0000-0000-0000-000000000005', 'manager2@t.test'),
 ('a0000000-0000-0000-0000-000000000011', 'emp1@t.test'),
 ('a0000000-0000-0000-0000-000000000012', 'emp2@t.test'),
 ('a0000000-0000-0000-0000-000000000013', 'newbie@t.test'),
 ('a0000000-0000-0000-0000-000000000021', 'cust@t.test'),
 ('a0000000-0000-0000-0000-000000000022', 'othercust@t.test');
INSERT INTO app.profiles (id, email, display_name, account_status, location_consent_at) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'admin@t.test', 'Admin', 'active', NULL),
 ('a0000000-0000-0000-0000-000000000002', 'manager@t.test', 'Manager', 'active', NULL),
 ('a0000000-0000-0000-0000-000000000003', 'ops@t.test', 'Ops', 'active', NULL),
 ('a0000000-0000-0000-0000-000000000004', 'ops2@t.test', 'Ops2', 'active', NULL),
 ('a0000000-0000-0000-0000-000000000005', 'manager2@t.test', 'Manager2', 'active', NULL),
 ('a0000000-0000-0000-0000-000000000011', 'emp1@t.test', 'Emp1', 'active', now()),
 ('a0000000-0000-0000-0000-000000000012', 'emp2@t.test', 'Emp2', 'active', now()),
 ('a0000000-0000-0000-0000-000000000021', 'cust@t.test', 'Cust', 'active', NULL),
 ('a0000000-0000-0000-0000-000000000022', 'othercust@t.test', 'Other', 'active', NULL);
INSERT INTO app.user_roles (user_id, role) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'admin'),
 ('a0000000-0000-0000-0000-000000000002', 'manager'),
 ('a0000000-0000-0000-0000-000000000005', 'manager'),
 ('a0000000-0000-0000-0000-000000000003', 'operations'),
 ('a0000000-0000-0000-0000-000000000004', 'operations'),
 ('a0000000-0000-0000-0000-000000000011', 'employee'),
 ('a0000000-0000-0000-0000-000000000012', 'employee'),
 ('a0000000-0000-0000-0000-000000000021', 'customer'),
 ('a0000000-0000-0000-0000-000000000022', 'customer');
INSERT INTO app.employees (user_id, region, skills) VALUES
 ('a0000000-0000-0000-0000-000000000011', 'Vijayawada', ARRAY['shelf']),
 ('a0000000-0000-0000-0000-000000000012', 'Vijayawada', ARRAY['shelf','survey']);
-- Benz Circle, Vijayawada
INSERT INTO app.employee_locations (user_id, lat, lng, captured_at) VALUES
 ('a0000000-0000-0000-0000-000000000011', 16.4985, 80.6563, now()),
 ('a0000000-0000-0000-0000-000000000012', 16.4985, 80.6563, now());
INSERT INTO app.organizations (id, name, kind) VALUES
 ('b0000000-0000-0000-0000-000000000001', 'Sample Mart (test)', 'customer'),
 ('b0000000-0000-0000-0000-000000000002', 'Other Co (test)', 'customer');
INSERT INTO app.org_members (org_id, user_id) VALUES
 ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000021'),
 ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000022');
SELECT set_config('app.bootstrap', 'off', false);

-- ===========================================================================
-- A. Identity & privilege escalation
-- ===========================================================================
SELECT t.as_user('a0000000-0000-0000-0000-000000000013');
SELECT t.expect_ok('A1 new user can self-register as employee (pending)',
  $q$SELECT app.register_employee('newbie@t.test','Newbie','+910000000000','Vijayawada')$q$);
SELECT t.check('A2 self-registered account is pending, not active',
  (SELECT account_status = 'pending' FROM app.profiles WHERE id = 'a0000000-0000-0000-0000-000000000013'));
SELECT t.expect_fail('A3 user cannot self-grant manager',
  $q$INSERT INTO app.user_roles(user_id, role) VALUES ('a0000000-0000-0000-0000-000000000013','manager')$q$, 'CH403');
SELECT t.expect_fail('A4 user cannot self-grant admin',
  $q$INSERT INTO app.user_roles(user_id, role) VALUES ('a0000000-0000-0000-0000-000000000013','admin')$q$, 'CH403');
SELECT t.expect_fail('A5 user cannot self-activate',
  $q$UPDATE app.profiles SET account_status='active' WHERE id='a0000000-0000-0000-0000-000000000013'$q$, 'CH403');
SELECT t.expect_fail('A6 pending user cannot self-approve via function',
  $q$SELECT app.decide_account('a0000000-0000-0000-0000-000000000013','approved','x')$q$, 'CH403');

SELECT t.as_user('a0000000-0000-0000-0000-000000000002');  -- manager
SELECT t.expect_fail('A7 manager cannot grant manager role',
  $q$INSERT INTO app.user_roles(user_id, role) VALUES ('a0000000-0000-0000-0000-000000000013','manager')$q$, 'CH403');
SELECT t.expect_fail('A8 manager cannot approve a non-employee (manager) account',
  $q$SELECT app.decide_account('a0000000-0000-0000-0000-000000000005','suspended','nope nope')$q$, 'CH403');
SELECT t.expect_fail('A9 role rows cannot be updated',
  $q$UPDATE app.user_roles SET role='admin' WHERE user_id='a0000000-0000-0000-0000-000000000011'$q$, 'CH403');
SELECT t.expect_ok('A10 manager approves a pending EMPLOYEE account',
  $q$SELECT app.decide_account('a0000000-0000-0000-0000-000000000013','approved',NULL)$q$);
SELECT t.check('A11 account became active',
  (SELECT account_status = 'active' FROM app.profiles WHERE id = 'a0000000-0000-0000-0000-000000000013'));
SELECT t.expect_fail('A12 deciding twice is rejected (not pending anymore)',
  $q$SELECT app.decide_account('a0000000-0000-0000-0000-000000000013','approved',NULL)$q$, 'CH409');
SELECT t.expect_fail('A13 reject requires a reason',
  $q$SELECT app.decide_account('a0000000-0000-0000-0000-000000000013','suspended','')$q$, '23514');

SELECT t.as_user('a0000000-0000-0000-0000-000000000001');  -- admin
SELECT t.expect_ok('A14 admin grants manager role',
  $q$INSERT INTO app.user_roles(user_id, role) VALUES ('a0000000-0000-0000-0000-000000000013','manager')$q$);
SELECT t.check('A15 grant is attributed to the admin',
  (SELECT granted_by = 'a0000000-0000-0000-0000-000000000001' FROM app.user_roles WHERE user_id='a0000000-0000-0000-0000-000000000013' AND role='manager'));
SELECT t.check('A16 role grant was audited',
  EXISTS (SELECT 1 FROM app.audit_events WHERE action='role.granted' AND entity_id='a0000000-0000-0000-0000-000000000013'
          AND actor_id='a0000000-0000-0000-0000-000000000001'));
SELECT t.as_nobody();
SELECT t.expect_fail('A17 no authenticated actor => no role changes',
  $q$INSERT INTO app.user_roles(user_id, role) VALUES ('a0000000-0000-0000-0000-000000000011','admin')$q$, 'CH403');
SELECT t.as_user('a0000000-0000-0000-0000-000000000001');
SELECT t.expect_fail('A18 audit log cannot be edited',
  $q$UPDATE app.audit_events SET action='tampered'$q$, 'CH403');
SELECT t.expect_fail('A19 audit log cannot be deleted',
  $q$DELETE FROM app.audit_events$q$, 'CH403');
SELECT t.expect_fail('A20 audit log cannot be truncated',
  $q$TRUNCATE app.audit_events$q$, 'CH403');
SELECT t.expect_fail('A22 duplicate email (any case) rejected',
  $q$UPDATE app.profiles SET email='ADMIN@t.test' WHERE id='a0000000-0000-0000-0000-000000000002'$q$, '23505');

-- ===========================================================================
-- B. Location consent
-- ===========================================================================
SELECT t.as_user('a0000000-0000-0000-0000-000000000013');
SELECT t.expect_fail('B1 location update without consent is rejected',
  $q$SELECT app.update_my_location(16.5, 80.6, 10, now())$q$, 'CH403');
SELECT t.as_user('a0000000-0000-0000-0000-000000000013');
UPDATE app.employees SET region = region WHERE user_id = 'a0000000-0000-0000-0000-000000000013';
SELECT t.expect_ok('B2 giving consent', $q$SELECT app.set_location_consent(true)$q$);
SELECT t.expect_ok('B3 location update with consent', $q$SELECT app.update_my_location(16.5, 80.6, 10, now())$q$);
SELECT t.check('B4 location stored', EXISTS (SELECT 1 FROM app.employee_locations WHERE user_id='a0000000-0000-0000-0000-000000000013'));
SELECT t.expect_ok('B5 withdrawing consent', $q$SELECT app.set_location_consent(false)$q$);
SELECT t.check('B6 withdrawing consent erases the stored location',
  NOT EXISTS (SELECT 1 FROM app.employee_locations WHERE user_id='a0000000-0000-0000-0000-000000000013'));

-- ===========================================================================
-- C. Checklists, tasks, discovery
-- ===========================================================================
SELECT t.as_user('a0000000-0000-0000-0000-000000000011');
SELECT t.expect_fail('C1 employee cannot create checklist templates',
  $q$SELECT app.save_checklist_template(NULL,'X','shelf','[{"label":"a"}]'::jsonb)$q$, 'CH403');
SELECT t.as_user('a0000000-0000-0000-0000-000000000002');
SELECT t.expect_fail('C2 empty checklist rejected',
  $q$SELECT app.save_checklist_template(NULL,'Empty','shelf','[]'::jsonb)$q$, 'CH422');
CREATE TEMP TABLE ctx (k text PRIMARY KEY, v text);
INSERT INTO ctx SELECT 'ver1', app.save_checklist_template(NULL, 'Shelf audit (test)', 'shelf',
  '[{"label":"Photo of shelf","required":true,"requiresPhoto":true},
    {"label":"Planogram followed","required":true,"requiresPhoto":false},
    {"label":"Note competitor displays","required":false,"requiresPhoto":false}]'::jsonb);
SELECT t.check('C3 manager created checklist version 1 with 3 items',
  (SELECT count(*) = 3 FROM app.checklist_items WHERE version_id = (SELECT v::uuid FROM ctx WHERE k='ver1')));
SELECT t.expect_fail('C4 checklist items are immutable',
  $q$UPDATE app.checklist_items SET label='changed'$q$, 'CH403');

-- tasks: near (Benz Circle area), far (Hyderabad), skill-gated
CREATE FUNCTION t.mk_task(p_title text, p_lat numeric, p_lng numeric, p_skills text[], p_reward bigint) RETURNS uuid LANGUAGE sql AS $$
  SELECT app.create_task(p_title, 'test', 'shelf', 'normal', 'b0000000-0000-0000-0000-000000000001', NULL,
    'addr', 'city', 'AP', '520010', p_lat, p_lng, 200, p_reward, now() - interval '1 minute', now() + interval '3 days',
    p_skills, (SELECT v::uuid FROM ctx WHERE k='ver1'))
$$;
GRANT ALL ON TABLE ctx TO PUBLIC;

SELECT t.as_user('a0000000-0000-0000-0000-000000000011');
SELECT t.expect_fail('C5 employee cannot create tasks',
  $q$SELECT t.mk_task('nope task', 16.5, 80.65, '{}', 10000)$q$, 'CH403');
SELECT t.as_user('a0000000-0000-0000-0000-000000000022');  -- customer of ANOTHER org
SELECT t.expect_fail('C6 customer cannot create tasks for another organisation',
  $q$SELECT t.mk_task('nope task', 16.5, 80.65, '{}', 10000)$q$, 'CH403');
SELECT t.as_user('a0000000-0000-0000-0000-000000000021');  -- member of org 1
SELECT t.expect_ok('C7 customer can create a task for their own organisation',
  $q$INSERT INTO ctx SELECT 'tNearCust', t.mk_task('Near task by customer', 16.5, 80.655, '{}', 45000)$q$);
SELECT t.as_user('a0000000-0000-0000-0000-000000000002');
INSERT INTO ctx SELECT 'tNear',  t.mk_task('Near task',           16.5000, 80.6600, '{}',        45000);
INSERT INTO ctx SELECT 'tFar',   t.mk_task('Far task (Hyderabad)', 17.3850, 78.4867, '{}',        45000);
INSERT INTO ctx SELECT 'tSkill', t.mk_task('Needs survey skill',   16.4990, 80.6570, ARRAY['survey'], 45000);
INSERT INTO ctx SELECT 'tNeg',   t.mk_task('Zero reward task',     16.5000, 80.6601, '{}',        0);
SELECT t.expect_fail('C8 negative reward rejected by constraint',
  $q$SELECT t.mk_task('negative reward', 16.5, 80.6, '{}', -1)$q$, '23514');
SELECT t.check('C9 new task starts open and has a history row',
  (SELECT status='open' FROM app.tasks WHERE id=(SELECT v::uuid FROM ctx WHERE k='tNear'))
  AND EXISTS (SELECT 1 FROM app.task_status_history WHERE task_id=(SELECT v::uuid FROM ctx WHERE k='tNear') AND from_status IS NULL));

SELECT t.as_user('a0000000-0000-0000-0000-000000000011');   -- skills: shelf
SELECT t.check('C10 nearby shows near task, hides far task and skill-gated task',
  (SELECT count(*) FROM app.nearby_open_tasks()) = 3  -- tNearCust, tNear, tNeg
  AND NOT EXISTS (SELECT 1 FROM app.nearby_open_tasks() n WHERE n.task_id IN ((SELECT v::uuid FROM ctx WHERE k='tFar'), (SELECT v::uuid FROM ctx WHERE k='tSkill'))),
  (SELECT string_agg(task_id::text, ',') FROM app.nearby_open_tasks()));
SELECT t.as_user('a0000000-0000-0000-0000-000000000012');   -- skills: shelf, survey
SELECT t.check('C11 employee with the skill also sees the skill-gated task',
  EXISTS (SELECT 1 FROM app.nearby_open_tasks() n WHERE n.task_id = (SELECT v::uuid FROM ctx WHERE k='tSkill')));
SELECT t.as_user('a0000000-0000-0000-0000-000000000011');
SELECT t.expect_fail('C12 cannot accept a task that is out of radius',
  $q$SELECT app.accept_task((SELECT v::uuid FROM ctx WHERE k='tFar'))$q$, 'CH403');
SELECT t.expect_fail('C13 cannot accept a task missing the required skill',
  $q$SELECT app.accept_task((SELECT v::uuid FROM ctx WHERE k='tSkill'))$q$, 'CH403');
SELECT t.expect_ok('C14 employee rejects the zero-reward offer',
  $q$SELECT app.reject_task_offer((SELECT v::uuid FROM ctx WHERE k='tNeg'), 'not interested')$q$);
SELECT t.check('C15 rejected task disappears from that employee''s nearby list',
  NOT EXISTS (SELECT 1 FROM app.nearby_open_tasks() n WHERE n.task_id = (SELECT v::uuid FROM ctx WHERE k='tNeg')));
SELECT t.as_user('a0000000-0000-0000-0000-000000000013');   -- consent withdrawn above
SELECT t.check('C16 no consented location => nothing discoverable',
  (SELECT count(*) FROM app.nearby_open_tasks()) = 0);

-- ===========================================================================
-- D. State machine (direct manipulation must fail)
-- ===========================================================================
SELECT t.as_user('a0000000-0000-0000-0000-000000000001');
SELECT t.expect_fail('D1 illegal transition open -> approved',
  $q$UPDATE app.tasks SET status='approved' WHERE id=(SELECT v::uuid FROM ctx WHERE k='tNear')$q$, 'CH409');
SELECT t.expect_fail('D2 illegal transition open -> submitted',
  $q$UPDATE app.tasks SET status='submitted' WHERE id=(SELECT v::uuid FROM ctx WHERE k='tNear')$q$, 'CH409');
SELECT t.expect_fail('D3 open task cannot carry an assignee (exclusivity invariant)',
  $q$UPDATE app.tasks SET assigned_to='a0000000-0000-0000-0000-000000000011' WHERE id=(SELECT v::uuid FROM ctx WHERE k='tNear')$q$, '23514');
SELECT t.expect_fail('D4 only active employees can be assigned by staff',
  $q$SELECT app.assign_task((SELECT v::uuid FROM ctx WHERE k='tNear'), 'a0000000-0000-0000-0000-000000000021')$q$, 'CH422');

-- ===========================================================================
-- E. Work flow: accept -> checklist -> evidence -> submit
-- ===========================================================================
SELECT t.as_user('a0000000-0000-0000-0000-000000000011');
SELECT t.expect_ok('E1 employee accepts the near task',
  $q$SELECT app.accept_task((SELECT v::uuid FROM ctx WHERE k='tNear'))$q$);
SELECT t.check('E2 task is accepted by that employee',
  (SELECT status='accepted' AND assigned_to='a0000000-0000-0000-0000-000000000011' FROM app.tasks WHERE id=(SELECT v::uuid FROM ctx WHERE k='tNear')));
SELECT t.as_user('a0000000-0000-0000-0000-000000000012');
SELECT t.expect_fail('E3 a second employee cannot accept the same task',
  $q$SELECT app.accept_task((SELECT v::uuid FROM ctx WHERE k='tNear'))$q$, 'CH409');
SELECT t.expect_fail('E4 other employee cannot touch the checklist',
  $q$SELECT app.save_checklist_response((SELECT v::uuid FROM ctx WHERE k='tNear'), (SELECT id FROM app.checklist_items WHERE position=2 AND version_id=(SELECT v::uuid FROM ctx WHERE k='ver1')), 'done', NULL, NULL)$q$, 'CH403');

SELECT t.as_user('a0000000-0000-0000-0000-000000000011');
SELECT t.expect_fail('E5 checklist is locked until the task is started',
  $q$SELECT app.save_checklist_response((SELECT v::uuid FROM ctx WHERE k='tNear'), (SELECT id FROM app.checklist_items WHERE position=2 AND version_id=(SELECT v::uuid FROM ctx WHERE k='ver1')), 'done', NULL, NULL)$q$, 'CH409');
SELECT t.expect_ok('E6 employee starts the task', $q$SELECT app.start_task((SELECT v::uuid FROM ctx WHERE k='tNear'))$q$);
SELECT t.expect_fail('E7 submit with nothing done is rejected (required items)',
  $q$SELECT app.submit_task((SELECT v::uuid FROM ctx WHERE k='tNear'))$q$, 'CH422');
SELECT t.expect_fail('E8 required item cannot be marked not-applicable',
  $q$SELECT app.save_checklist_response((SELECT v::uuid FROM ctx WHERE k='tNear'), (SELECT id FROM app.checklist_items WHERE position=2 AND version_id=(SELECT v::uuid FROM ctx WHERE k='ver1')), 'not_applicable', NULL, 'because')$q$, 'CH422');
SELECT t.expect_ok('E9 mark both required items done',
  $q$SELECT app.save_checklist_response((SELECT v::uuid FROM ctx WHERE k='tNear'), id, 'done', NULL, NULL)
     FROM app.checklist_items WHERE version_id=(SELECT v::uuid FROM ctx WHERE k='ver1') AND required$q$);
SELECT t.expect_fail('E10 submit without the required photo is rejected',
  $q$SELECT app.submit_task((SELECT v::uuid FROM ctx WHERE k='tNear'))$q$, 'CH422');
SELECT t.expect_fail('E11 unsupported file type rejected',
  $q$SELECT * FROM app.begin_evidence_upload((SELECT v::uuid FROM ctx WHERE k='tNear'), NULL, 'application/pdf', now(), NULL, NULL, NULL)$q$, 'CH422');
SELECT t.expect_fail('E12 item from another checklist rejected',
  $q$SELECT * FROM app.begin_evidence_upload((SELECT v::uuid FROM ctx WHERE k='tNear'), gen_random_uuid(), 'image/jpeg', now(), NULL, NULL, NULL)$q$, 'CH422');
SELECT t.as_user('a0000000-0000-0000-0000-000000000012');
SELECT t.expect_fail('E13 only the assignee can upload evidence',
  $q$SELECT * FROM app.begin_evidence_upload((SELECT v::uuid FROM ctx WHERE k='tNear'), NULL, 'image/jpeg', now(), NULL, NULL, NULL)$q$, 'CH403');
SELECT t.as_user('a0000000-0000-0000-0000-000000000011');
INSERT INTO ctx SELECT 'ev1', evidence_id::text FROM app.begin_evidence_upload(
  (SELECT v::uuid FROM ctx WHERE k='tNear'),
  (SELECT id FROM app.checklist_items WHERE position=1 AND version_id=(SELECT v::uuid FROM ctx WHERE k='ver1')),
  'image/jpeg', now() - interval '2 minutes', 16.5001, 80.6601, 8);
SELECT t.check('E14 storage path is server-generated under tasks/<task>/<evidence>.jpg',
  (SELECT storage_path = 'tasks/' || task_id || '/' || id || '.jpg' FROM app.task_evidence WHERE id=(SELECT v::uuid FROM ctx WHERE k='ev1')));
SELECT t.check('E15 distance to task computed server-side', (SELECT distance_to_task_m BETWEEN 0 AND 100 FROM app.task_evidence WHERE id=(SELECT v::uuid FROM ctx WHERE k='ev1')),
  (SELECT distance_to_task_m::text FROM app.task_evidence WHERE id=(SELECT v::uuid FROM ctx WHERE k='ev1')));
SELECT t.expect_fail('E16 a pending (unconfirmed) upload does not satisfy the photo requirement',
  $q$SELECT app.submit_task((SELECT v::uuid FROM ctx WHERE k='tNear'))$q$, 'CH422');
SELECT t.expect_fail('E17 oversize upload rejected by constraint',
  $q$SELECT app.confirm_evidence_upload((SELECT v::uuid FROM ctx WHERE k='ev1'), 99999999999, repeat('a',64))$q$, '23514');
SELECT t.expect_fail('E18 malformed hash rejected',
  $q$SELECT app.confirm_evidence_upload((SELECT v::uuid FROM ctx WHERE k='ev1'), 1000, 'not-a-hash')$q$, '23514');
SELECT t.expect_ok('E19 confirm upload', $q$SELECT app.confirm_evidence_upload((SELECT v::uuid FROM ctx WHERE k='ev1'), 123456, repeat('a',64))$q$);
SELECT t.expect_ok('E20 submit succeeds with checklist + photo', $q$SELECT app.submit_task((SELECT v::uuid FROM ctx WHERE k='tNear'))$q$);
SELECT t.check('E21 task is submitted and submission_no = 1',
  (SELECT status='submitted' AND submission_no=1 FROM app.tasks WHERE id=(SELECT v::uuid FROM ctx WHERE k='tNear')));
SELECT t.expect_fail('E22 checklist locked after submission',
  $q$SELECT app.save_checklist_response((SELECT v::uuid FROM ctx WHERE k='tNear'), (SELECT id FROM app.checklist_items WHERE position=3 AND version_id=(SELECT v::uuid FROM ctx WHERE k='ver1')), 'done', NULL, NULL)$q$, 'CH409');

-- ===========================================================================
-- F. AI verification is advisory and cannot be faked
-- ===========================================================================
SELECT t.as_user('a0000000-0000-0000-0000-000000000001');
SELECT t.expect_fail('F1 "completed" AI result without provider/model is rejected',
  $q$SELECT app.record_ai_result((SELECT v::uuid FROM ctx WHERE k='tNear'),'completed','looks_consistent',0.9,'{}','ok',NULL,NULL,NULL)$q$, '23514');
SELECT t.expect_fail('F2 "pending" AI row cannot already carry a result',
  $q$SELECT app.record_ai_result((SELECT v::uuid FROM ctx WHERE k='tNear'),'pending','looks_consistent',0.9,'{}','ok','p','m',NULL)$q$, '23514');
SELECT t.expect_fail('F3 confidence outside 0..1 rejected',
  $q$SELECT app.record_ai_result((SELECT v::uuid FROM ctx WHERE k='tNear'),'completed','looks_consistent',1.5,'{}','ok','p','m',NULL)$q$, '23514');
SELECT t.expect_ok('F4 pending AI request recorded (no result)',
  $q$SELECT app.record_ai_result((SELECT v::uuid FROM ctx WHERE k='tNear'),'pending',NULL,NULL,'{}',NULL,NULL,NULL,NULL)$q$);
SELECT t.check('F5 AI result never changed the task status',
  (SELECT status = 'submitted' FROM app.tasks WHERE id=(SELECT v::uuid FROM ctx WHERE k='tNear')));

-- ===========================================================================
-- G. Two-stage human approval
-- ===========================================================================
SELECT t.as_user('a0000000-0000-0000-0000-000000000003');   -- operations
SELECT t.expect_fail('G1 operations cannot approve before the manager has',
  $q$SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tNear'),'operations','approved',NULL)$q$, 'CH409');
SELECT t.as_user('a0000000-0000-0000-0000-000000000011');
SELECT t.expect_fail('G2 employee cannot review (even their own work)',
  $q$SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tNear'),'manager','approved',NULL)$q$, 'CH403');
SELECT t.as_user('a0000000-0000-0000-0000-000000000003');
SELECT t.expect_fail('G3 operations role cannot do the manager stage',
  $q$SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tNear'),'manager','approved',NULL)$q$, 'CH403');
SELECT t.as_user('a0000000-0000-0000-0000-000000000002');   -- manager
SELECT t.expect_fail('G4 rejecting requires a reason',
  $q$SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tNear'),'manager','rework_requested',' ')$q$, '23514');
SELECT t.expect_ok('G5 manager approves', $q$SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tNear'),'manager','approved','looks good')$q$);
SELECT t.check('G6 task is manager_approved (awaiting operations)', (SELECT status='manager_approved' FROM app.tasks WHERE id=(SELECT v::uuid FROM ctx WHERE k='tNear')));
SELECT t.expect_fail('G7 manager cannot record the same stage twice',
  $q$SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tNear'),'manager','approved',NULL)$q$, 'CH409');
SELECT t.check('G8 no reward exists yet', NOT EXISTS (SELECT 1 FROM app.wallet_ledger_entries));
SELECT t.as_user('a0000000-0000-0000-0000-000000000011');
SELECT t.expect_fail('G9 employee cannot credit their own reward',
  $q$SELECT app.credit_task_reward((SELECT v::uuid FROM ctx WHERE k='tNear'))$q$, 'CH403');
SELECT t.as_user('a0000000-0000-0000-0000-000000000003');
SELECT t.expect_fail('G10 cannot credit before final approval',
  $q$SELECT app.credit_task_reward((SELECT v::uuid FROM ctx WHERE k='tNear'))$q$, 'CH409');
SELECT t.expect_ok('G11 operations approves', $q$SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tNear'),'operations','approved','ok')$q$);
SELECT t.check('G12 task is approved', (SELECT status='approved' FROM app.tasks WHERE id=(SELECT v::uuid FROM ctx WHERE k='tNear')));
SELECT t.check('G13 history records every transition',
  (SELECT string_agg(coalesce(from_status::text,'-') || '>' || to_status::text, ',' ORDER BY occurred_at, id)
     FROM app.task_status_history WHERE task_id=(SELECT v::uuid FROM ctx WHERE k='tNear'))
  = '->open,open>accepted,accepted>in_progress,in_progress>submitted,submitted>manager_approved,manager_approved>approved',
  (SELECT string_agg(coalesce(from_status::text,'-') || '>' || to_status::text, ',' ORDER BY occurred_at, id) FROM app.task_status_history WHERE task_id=(SELECT v::uuid FROM ctx WHERE k='tNear')));
SELECT t.check('G14 decisions are append-only',
  (SELECT count(*) = 2 FROM app.approval_decisions WHERE task_id=(SELECT v::uuid FROM ctx WHERE k='tNear')));
SELECT t.expect_fail('G15 approval decisions cannot be edited',
  $q$UPDATE app.approval_decisions SET reason='x'$q$, 'CH403');

-- ===========================================================================
-- H. Wallet ledger
-- ===========================================================================
SELECT t.as_user('a0000000-0000-0000-0000-000000000003');
INSERT INTO ctx SELECT 'credit1', app.credit_task_reward((SELECT v::uuid FROM ctx WHERE k='tNear'))::text;
SELECT t.check('H1 reward credited with the amount from the TASK row (45000 paise)',
  (SELECT amount_paise = 45000 AND balance_after_paise = 45000 FROM app.wallet_ledger_entries WHERE id=(SELECT v::uuid FROM ctx WHERE k='credit1')));
SELECT t.check('H2 replay returns the same entry id (idempotent)',
  app.credit_task_reward((SELECT v::uuid FROM ctx WHERE k='tNear')) = (SELECT v::uuid FROM ctx WHERE k='credit1'));
SELECT t.check('H3 still exactly one ledger row for the task',
  (SELECT count(*) = 1 FROM app.wallet_ledger_entries WHERE task_id=(SELECT v::uuid FROM ctx WHERE k='tNear')));
SELECT t.check('H4 cached balance = 45000',
  (SELECT balance_paise = 45000 FROM app.wallet_accounts WHERE user_id='a0000000-0000-0000-0000-000000000011'));
SELECT t.expect_fail('H5 a different idempotency key cannot double-pay the same task (unique per task)',
  $q$INSERT INTO app.wallet_ledger_entries(user_id,entry_type,amount_paise,idempotency_key,task_id)
     VALUES ('a0000000-0000-0000-0000-000000000011','task_reward',45000,'another-key',(SELECT v::uuid FROM ctx WHERE k='tNear'))$q$, '23505');
SELECT t.expect_fail('H6 ledger rows cannot be updated', $q$UPDATE app.wallet_ledger_entries SET amount_paise = 999999$q$, 'CH403');
SELECT t.expect_fail('H7 ledger rows cannot be deleted', $q$DELETE FROM app.wallet_ledger_entries$q$, 'CH403');
SELECT t.expect_fail('H8 ledger cannot be truncated', $q$TRUNCATE app.wallet_ledger_entries$q$, 'CH403');
SELECT t.expect_fail('H9 balance cannot be edited directly',
  $q$UPDATE app.wallet_accounts SET balance_paise = 99999999 WHERE user_id='a0000000-0000-0000-0000-000000000011'$q$, 'CH403');
SELECT t.expect_fail('H10 non-admin cannot reverse an entry',
  $q$SELECT app.reverse_ledger_entry((SELECT v::uuid FROM ctx WHERE k='credit1'),'mistake')$q$, 'CH403');
SELECT t.as_user('a0000000-0000-0000-0000-000000000001');
SELECT t.expect_fail('H11 reversal needs a reason', $q$SELECT app.reverse_ledger_entry((SELECT v::uuid FROM ctx WHERE k='credit1'),'')$q$, 'CH422');
INSERT INTO ctx SELECT 'rev1', app.reverse_ledger_entry((SELECT v::uuid FROM ctx WHERE k='credit1'), 'duplicate award')::text;
SELECT t.check('H12 reversal is a compensating negative entry; original row untouched',
  (SELECT amount_paise = -45000 AND reverses_entry_id = (SELECT v::uuid FROM ctx WHERE k='credit1') FROM app.wallet_ledger_entries WHERE id=(SELECT v::uuid FROM ctx WHERE k='rev1'))
  AND (SELECT amount_paise = 45000 FROM app.wallet_ledger_entries WHERE id=(SELECT v::uuid FROM ctx WHERE k='credit1')));
SELECT t.check('H13 reversal replay is idempotent',
  app.reverse_ledger_entry((SELECT v::uuid FROM ctx WHERE k='credit1'), 'duplicate award') = (SELECT v::uuid FROM ctx WHERE k='rev1'));
SELECT t.expect_fail('H14 a reversal cannot be reversed', $q$SELECT app.reverse_ledger_entry((SELECT v::uuid FROM ctx WHERE k='rev1'),'undo undo')$q$, 'CH409');
SELECT t.check('H15 balance back to 0', (SELECT balance_paise = 0 FROM app.wallet_accounts WHERE user_id='a0000000-0000-0000-0000-000000000011'));
SELECT t.expect_fail('H16 overdraft is impossible (negative balance rejected)',
  $q$INSERT INTO app.wallet_ledger_entries(user_id,entry_type,amount_paise,idempotency_key,memo) VALUES ('a0000000-0000-0000-0000-000000000011','adjustment',-1,'adj-overdraft','x')$q$, 'CH409');
SELECT t.check('H17 reconciliation finds no discrepancies', (SELECT count(*) = 0 FROM app.wallet_discrepancies()));
SELECT t.check('H18 credit and reversal were audited',
  (SELECT count(*) = 2 FROM app.audit_events WHERE action IN ('wallet.reward_credited','wallet.entry_reversed')));

-- ===========================================================================
-- I. Rework loop (resubmission needs new evidence; reviewers must differ)
-- ===========================================================================
SELECT t.as_user('a0000000-0000-0000-0000-000000000002');
INSERT INTO ctx SELECT 'tRework', t.mk_task('Rework task', 16.5002, 80.6602, '{}', 30000);
SELECT t.as_user('a0000000-0000-0000-0000-000000000012');
SELECT app.accept_task((SELECT v::uuid FROM ctx WHERE k='tRework'));
SELECT app.start_task((SELECT v::uuid FROM ctx WHERE k='tRework'));
SELECT app.save_checklist_response((SELECT v::uuid FROM ctx WHERE k='tRework'), id, 'done', NULL, NULL)
  FROM app.checklist_items WHERE version_id=(SELECT v::uuid FROM ctx WHERE k='ver1') AND required;
INSERT INTO ctx SELECT 'evR1', evidence_id::text FROM app.begin_evidence_upload((SELECT v::uuid FROM ctx WHERE k='tRework'),
  (SELECT id FROM app.checklist_items WHERE position=1 AND version_id=(SELECT v::uuid FROM ctx WHERE k='ver1')), 'image/png', now(), NULL, NULL, NULL);
SELECT app.confirm_evidence_upload((SELECT v::uuid FROM ctx WHERE k='evR1'), 5000, repeat('b',64));
SELECT app.submit_task((SELECT v::uuid FROM ctx WHERE k='tRework'));
SELECT t.as_user('a0000000-0000-0000-0000-000000000002');
SELECT t.expect_ok('I1 manager requests rework with a reason',
  $q$SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tRework'),'manager','rework_requested','photo is blurry, retake')$q$);
SELECT t.as_user('a0000000-0000-0000-0000-000000000012');
SELECT t.expect_ok('I2 employee resumes', $q$SELECT app.start_task((SELECT v::uuid FROM ctx WHERE k='tRework'))$q$);
SELECT t.expect_fail('I3 resubmission requires a NEW photo for the new submission',
  $q$SELECT app.submit_task((SELECT v::uuid FROM ctx WHERE k='tRework'))$q$, 'CH422');
INSERT INTO ctx SELECT 'evR2', evidence_id::text FROM app.begin_evidence_upload((SELECT v::uuid FROM ctx WHERE k='tRework'),
  (SELECT id FROM app.checklist_items WHERE position=1 AND version_id=(SELECT v::uuid FROM ctx WHERE k='ver1')), 'image/png', now(), NULL, NULL, NULL);
SELECT t.expect_fail('I4 same image bytes cannot be re-uploaded for the same task (duplicate hash)',
  $q$SELECT app.confirm_evidence_upload((SELECT v::uuid FROM ctx WHERE k='evR2'), 5000, repeat('b',64))$q$, '23505');
SELECT app.confirm_evidence_upload((SELECT v::uuid FROM ctx WHERE k='evR2'), 6000, repeat('c',64));
SELECT t.expect_ok('I5 resubmit', $q$SELECT app.submit_task((SELECT v::uuid FROM ctx WHERE k='tRework'))$q$);
SELECT t.check('I6 submission_no is now 2', (SELECT submission_no = 2 FROM app.tasks WHERE id=(SELECT v::uuid FROM ctx WHERE k='tRework')));
SELECT t.as_user('a0000000-0000-0000-0000-000000000005');   -- manager2
SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tRework'),'manager','approved','fixed');
SELECT t.as_user('a0000000-0000-0000-0000-000000000001');   -- admin tries the OPS stage after being... (not the manager reviewer) -> allowed
SELECT t.as_user('a0000000-0000-0000-0000-000000000005');
SELECT t.expect_fail('I7 manager cannot also act as operations approver (role)',
  $q$SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tRework'),'operations','approved',NULL)$q$, 'CH403');
-- two-person rule: make the same admin do both stages
INSERT INTO ctx SELECT 'tTwo', t.mk_task('Two-person task', 16.5003, 80.6603, '{}', 10000);
SELECT t.as_user('a0000000-0000-0000-0000-000000000012');
SELECT app.accept_task((SELECT v::uuid FROM ctx WHERE k='tTwo'));
SELECT app.start_task((SELECT v::uuid FROM ctx WHERE k='tTwo'));
SELECT app.save_checklist_response((SELECT v::uuid FROM ctx WHERE k='tTwo'), id, 'done', NULL, NULL)
  FROM app.checklist_items WHERE version_id=(SELECT v::uuid FROM ctx WHERE k='ver1') AND required;
INSERT INTO ctx SELECT 'evT', evidence_id::text FROM app.begin_evidence_upload((SELECT v::uuid FROM ctx WHERE k='tTwo'),
  (SELECT id FROM app.checklist_items WHERE position=1 AND version_id=(SELECT v::uuid FROM ctx WHERE k='ver1')), 'image/webp', now(), NULL, NULL, NULL);
SELECT app.confirm_evidence_upload((SELECT v::uuid FROM ctx WHERE k='evT'), 7000, repeat('d',64));
SELECT app.submit_task((SELECT v::uuid FROM ctx WHERE k='tTwo'));
SELECT t.as_user('a0000000-0000-0000-0000-000000000001');
SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tTwo'),'manager','approved','admin as manager');
SELECT t.expect_fail('I8 two-person rule: same admin cannot also give the operations approval',
  $q$SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tTwo'),'operations','approved',NULL)$q$, 'CH403');
SELECT t.as_user('a0000000-0000-0000-0000-000000000003');
SELECT t.expect_ok('I9 a different operations user can approve', $q$SELECT app.decide_task((SELECT v::uuid FROM ctx WHERE k='tTwo'),'operations','approved',NULL)$q$);

-- cancelled task
SELECT t.as_user('a0000000-0000-0000-0000-000000000002');
SELECT t.expect_fail('I10 cancellation requires a reason', $q$SELECT app.cancel_task((SELECT v::uuid FROM ctx WHERE k='tNeg'), '')$q$, 'CH422');
SELECT t.expect_ok('I11 manager cancels an open task', $q$SELECT app.cancel_task((SELECT v::uuid FROM ctx WHERE k='tNeg'), 'customer withdrew')$q$);
SELECT t.expect_fail('I12 cancelled task is terminal',
  $q$UPDATE app.tasks SET status='open' WHERE id=(SELECT v::uuid FROM ctx WHERE k='tNeg')$q$, 'CH409');

-- ===========================================================================
-- J. RLS / Data API exposure (what a signed-in or anonymous client could do)
-- ===========================================================================
SELECT t.as_nobody();
-- the probes below run under the restricted roles and must still record results
GRANT USAGE ON SCHEMA t TO authenticated, anon;
GRANT INSERT ON t.results TO authenticated, anon;
GRANT USAGE ON SEQUENCE t.results_n_seq TO authenticated, anon;
DO $$
DECLARE n bigint;
BEGIN
  -- signed-in employee 1
  PERFORM set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-000000000011', false);
  SET ROLE authenticated;
  BEGIN SELECT count(*) INTO n FROM app.profiles; INSERT INTO t.results(name, ok, detail) VALUES ('J1 authenticated sees only their own profile row', n = 1, n::text);
  EXCEPTION WHEN OTHERS THEN INSERT INTO t.results(name, ok, detail) VALUES ('J1 authenticated sees only their own profile row', false, SQLERRM); END;
  BEGIN SELECT count(*) INTO n FROM app.user_roles; INSERT INTO t.results(name, ok, detail) VALUES ('J2 authenticated sees only their own roles', n = 1, n::text);
  EXCEPTION WHEN OTHERS THEN INSERT INTO t.results(name, ok, detail) VALUES ('J2 authenticated sees only their own roles', false, SQLERRM); END;
  BEGIN SELECT count(*) INTO n FROM app.wallet_ledger_entries; INSERT INTO t.results(name, ok, detail) VALUES ('J3 authenticated sees only their own ledger rows', n = 2, n::text);
  EXCEPTION WHEN OTHERS THEN INSERT INTO t.results(name, ok, detail) VALUES ('J3 authenticated sees only their own ledger rows', false, SQLERRM); END;
  BEGIN PERFORM 1 FROM app.tasks LIMIT 1; INSERT INTO t.results(name, ok, detail) VALUES ('J4 authenticated cannot read tasks directly', false, 'read succeeded');
  EXCEPTION WHEN insufficient_privilege THEN INSERT INTO t.results(name, ok, detail) VALUES ('J4 authenticated cannot read tasks directly', true, ''); END;
  BEGIN INSERT INTO app.user_roles(user_id, role) VALUES ('a0000000-0000-0000-0000-000000000011','admin'); INSERT INTO t.results(name, ok, detail) VALUES ('J5 authenticated cannot insert roles (self-promotion)', false, 'insert succeeded');
  EXCEPTION WHEN insufficient_privilege THEN INSERT INTO t.results(name, ok, detail) VALUES ('J5 authenticated cannot insert roles (self-promotion)', true, ''); END;
  BEGIN UPDATE app.profiles SET account_status='suspended' WHERE id='a0000000-0000-0000-0000-000000000011'; INSERT INTO t.results(name, ok, detail) VALUES ('J6 authenticated cannot update profiles', false, 'update succeeded');
  EXCEPTION WHEN insufficient_privilege THEN INSERT INTO t.results(name, ok, detail) VALUES ('J6 authenticated cannot update profiles', true, ''); END;
  BEGIN UPDATE app.wallet_accounts SET balance_paise = 1000000; INSERT INTO t.results(name, ok, detail) VALUES ('J7 authenticated cannot change wallet balance', false, 'update succeeded');
  EXCEPTION WHEN insufficient_privilege THEN INSERT INTO t.results(name, ok, detail) VALUES ('J7 authenticated cannot change wallet balance', true, ''); END;
  BEGIN PERFORM app.accept_task(gen_random_uuid()); INSERT INTO t.results(name, ok, detail) VALUES ('J8 authenticated cannot call business functions directly', false, 'call succeeded');
  EXCEPTION WHEN insufficient_privilege THEN INSERT INTO t.results(name, ok, detail) VALUES ('J8 authenticated cannot call business functions directly', true, ''); END;
  RESET ROLE;
  PERFORM set_config('request.jwt.claim.sub', '', false);
  -- anonymous
  SET ROLE anon;
  BEGIN PERFORM 1 FROM app.profiles LIMIT 1; INSERT INTO t.results(name, ok, detail) VALUES ('J9 anon cannot read anything in app', false, 'read succeeded');
  EXCEPTION WHEN insufficient_privilege THEN INSERT INTO t.results(name, ok, detail) VALUES ('J9 anon cannot read anything in app', true, ''); END;
  RESET ROLE;
END $$;
SELECT t.check('J10 every app table has RLS enabled',
  (SELECT count(*) = 0 FROM pg_tables WHERE schemaname='app' AND NOT rowsecurity),
  (SELECT string_agg(tablename, ',') FROM pg_tables WHERE schemaname='app' AND NOT rowsecurity));
SELECT t.check('J11 evidence bucket is private with a 15 MB cap',
  (SELECT NOT public AND file_size_limit = 15728640 FROM storage.buckets WHERE id='task-evidence'));
SELECT t.check('J12 V1 placeholder tables are gone',
  to_regclass('app.users') IS NULL AND to_regclass('app.roles') IS NULL);

-- ===========================================================================
-- K. Task pricing (V7)
-- ===========================================================================
SELECT t.as_user('a0000000-0000-0000-0000-000000000002');
CREATE TEMP TABLE kctx AS SELECT app.create_task('Pricing task','t','shelf','normal','b0000000-0000-0000-0000-000000000001',NULL,'a','c','s','1',16.5,80.66,200,0,now()-interval '1 minute',now()+interval '2 days','{}',(SELECT id FROM app.checklist_template_versions ORDER BY created_at LIMIT 1)) AS id;
SELECT t.expect_ok('K1 staff can price an open task', $q$SELECT app.set_task_reward((SELECT id FROM kctx), 12345)$q$);
SELECT t.check('K2 the reward was stored', (SELECT reward_paise = 12345 FROM app.tasks WHERE id = (SELECT id FROM kctx)));
SELECT t.as_user('a0000000-0000-0000-0000-000000000011');
SELECT t.expect_fail('K3 an employee cannot price a task', $q$SELECT app.set_task_reward((SELECT id FROM kctx), 99999999)$q$, 'CH403');
SELECT t.as_user('a0000000-0000-0000-0000-000000000002');
SELECT t.expect_fail('K4 negative reward rejected', $q$SELECT app.set_task_reward((SELECT id FROM kctx), -1)$q$, 'CH422');
SELECT t.expect_ok('K5 assign for pricing-lock test', $q$SELECT app.assign_task((SELECT id FROM kctx), 'a0000000-0000-0000-0000-000000000011')$q$);
SELECT t.expect_fail('K6 reward is locked once the task is in flight', $q$SELECT app.set_task_reward((SELECT id FROM kctx), 1)$q$, 'CH409');

-- ---------------------------------------------------------------------------
SELECT n, CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS result, name, CASE WHEN ok THEN '' ELSE detail END AS detail FROM t.results ORDER BY n;
SELECT count(*) FILTER (WHERE ok) AS passed, count(*) FILTER (WHERE NOT ok) AS failed FROM t.results;
