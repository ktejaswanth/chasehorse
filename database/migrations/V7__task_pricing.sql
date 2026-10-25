-- V7: let staff set or correct the reward of a task that is still open.
-- Customers can create tasks but must not set what employees are paid; staff price them.
-- Money is integer paise. The reward of an accepted/in-flight task can never change.
SET search_path = app, public;

CREATE FUNCTION app.set_task_reward(p_task uuid, p_reward_paise bigint) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
  v_actor uuid := app.actor_id();
  v_rows integer;
BEGIN
  IF v_actor IS NULL OR NOT (app.has_active_role(v_actor, 'admin') OR app.has_active_role(v_actor, 'manager') OR app.has_active_role(v_actor, 'operations')) THEN
    RAISE EXCEPTION 'only staff may set a task reward' USING ERRCODE = 'CH403';
  END IF;
  IF p_reward_paise IS NULL OR p_reward_paise < 0 OR p_reward_paise > 10000000000 THEN
    RAISE EXCEPTION 'reward must be between 0 and 10000000000 paise' USING ERRCODE = 'CH422';
  END IF;
  UPDATE app.tasks SET reward_paise = p_reward_paise WHERE id = p_task AND status = 'open';
  GET DIAGNOSTICS v_rows = ROW_COUNT;
  IF v_rows = 0 THEN
    IF NOT EXISTS (SELECT 1 FROM app.tasks WHERE id = p_task) THEN
      RAISE EXCEPTION 'task not found' USING ERRCODE = 'CH404';
    END IF;
    RAISE EXCEPTION 'the reward can only be changed while the task is open' USING ERRCODE = 'CH409';
  END IF;
  PERFORM app.record_audit('task.reward_set', 'task', p_task, jsonb_build_object('reward_paise', p_reward_paise));
END $$;
REVOKE ALL ON FUNCTION app.set_task_reward(uuid, bigint) FROM PUBLIC;
