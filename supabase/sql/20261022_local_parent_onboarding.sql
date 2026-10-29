-- 仅适用于已安装 20261022_local_core_baseline.sql 的 lemon-kids-local 专用项目。
-- 家长注册与创建家庭均以 Auth UID 为准；不接受客户端提交角色、积分或家庭 ID。

CREATE OR REPLACE FUNCTION public.create_local_parent_profile(p_name text)
RETURNS public.users LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, auth, pg_temp AS $$
DECLARE v_user public.users%ROWTYPE;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION '需要登录'; END IF;
  IF nullif(trim(p_name), '') IS NULL THEN RAISE EXCEPTION '家长名称不能为空'; END IF;
  SELECT * INTO v_user FROM public.users WHERE uid = auth.uid();
  IF FOUND THEN
    IF v_user.role <> 'parent' THEN RAISE EXCEPTION '已有非家长资料'; END IF;
    RETURN v_user;
  END IF;
  INSERT INTO public.users(uid,name,role,family_id,total_points)
    VALUES(auth.uid(),trim(p_name),'parent',NULL,0) RETURNING * INTO v_user;
  RETURN v_user;
END $$;

CREATE OR REPLACE FUNCTION public.create_local_family(p_name text)
RETURNS public.families LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, auth, pg_temp AS $$
DECLARE v_parent public.users%ROWTYPE; v_family public.families%ROWTYPE; v_code text; i integer;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION '需要登录'; END IF;
  IF nullif(trim(p_name), '') IS NULL THEN RAISE EXCEPTION '家庭名称不能为空'; END IF;
  SELECT * INTO v_parent FROM public.users WHERE uid=auth.uid() FOR UPDATE;
  IF NOT FOUND OR v_parent.role <> 'parent' THEN RAISE EXCEPTION '仅家长可创建家庭'; END IF;
  IF v_parent.family_id IS NOT NULL THEN
    SELECT * INTO v_family FROM public.families WHERE id=v_parent.family_id;
    RETURN v_family;
  END IF;
  FOR i IN 1..20 LOOP
    v_code := upper(substr(md5(gen_random_uuid()::text),1,6));
    BEGIN
      INSERT INTO public.families(name,invite_code) VALUES(trim(p_name),v_code)
        RETURNING * INTO v_family;
      EXIT;
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
  END LOOP;
  IF v_family.id IS NULL THEN RAISE EXCEPTION '生成邀请码失败'; END IF;
  UPDATE public.users SET family_id=v_family.id WHERE uid=auth.uid();
  RETURN v_family;
END $$;

CREATE OR REPLACE FUNCTION public.protect_local_user_fields()
RETURNS trigger LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
  IF current_user <> 'postgres' AND
     (NEW.uid IS DISTINCT FROM OLD.uid OR NEW.role IS DISTINCT FROM OLD.role OR
      NEW.family_id IS DISTINCT FROM OLD.family_id OR
      NEW.total_points IS DISTINCT FROM OLD.total_points OR
      NEW.created_at IS DISTINCT FROM OLD.created_at) THEN
    RAISE EXCEPTION '用户身份、家庭和积分不可直接修改';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER protect_local_user_fields_before_update
BEFORE UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION public.protect_local_user_fields();

CREATE POLICY users_self_profile_update ON public.users FOR UPDATE TO authenticated
USING (uid=auth.uid()) WITH CHECK (uid=auth.uid());

REVOKE ALL ON FUNCTION public.create_local_parent_profile(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_local_family(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_local_parent_profile(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_local_family(text) TO authenticated;
