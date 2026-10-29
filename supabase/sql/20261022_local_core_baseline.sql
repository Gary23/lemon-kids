-- 本地空库最小核心基线。仅在 lemon-kids-local 专用空库执行；历史 SQL 不自动串联。
-- 先覆盖家庭、用户与绑定；任务、奖励及其他域须分别迁移并验证。
CREATE TABLE public.families (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  invite_code text NOT NULL UNIQUE DEFAULT substring(md5(random()::text), 1, 6),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.users (
  uid uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  name text NOT NULL,
  role text NOT NULL CHECK (role IN ('parent', 'child')),
  family_id uuid REFERENCES public.families(id),
  avatar_url text,
  total_points integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX users_family_role_idx ON public.users(family_id, role);
CREATE TABLE public.binding_codes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL CHECK (code ~ '^[0-9]{6}$'),
  family_id uuid NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
  child_uid uuid NOT NULL REFERENCES public.users(uid) ON DELETE CASCADE,
  type text NOT NULL CHECK (type IN ('task', 'monitor', 'literacy')),
  status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'used', 'expired')),
  device_id text,
  expires_at timestamptz NOT NULL DEFAULT 'infinity',
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX binding_codes_live_code_idx ON public.binding_codes(code) WHERE status IN ('active', 'used');
CREATE INDEX binding_codes_child_type_idx ON public.binding_codes(child_uid, type, created_at DESC);
CREATE UNIQUE INDEX binding_codes_active_monitor_idx ON public.binding_codes(child_uid, type) WHERE status = 'active' AND type = 'monitor';
ALTER TABLE public.families ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.binding_codes ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.binding_codes FROM anon, authenticated;
CREATE POLICY families_member_read ON public.families FOR SELECT TO authenticated
 USING (EXISTS (SELECT 1 FROM public.users u WHERE u.uid = auth.uid() AND u.family_id = id));
CREATE POLICY users_self_read ON public.users FOR SELECT TO authenticated USING (uid = auth.uid());
-- 同家庭成员列表由家长读取；子账号不能枚举全家。
CREATE FUNCTION public.is_parent_in_family(p_family_id uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, auth, pg_temp AS $$
  SELECT EXISTS (SELECT 1 FROM public.users WHERE uid = auth.uid() AND role = 'parent' AND family_id = p_family_id);
$$;
REVOKE ALL ON FUNCTION public.is_parent_in_family(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_parent_in_family(uuid) TO authenticated;
CREATE POLICY users_parent_family_read ON public.users FOR SELECT TO authenticated
 USING (public.is_parent_in_family(family_id));
-- 注册与建家庭路径后续由单独的家长端流程适配；本地合成账号由本地 Auth/受控种子创建。

CREATE OR REPLACE FUNCTION public.generate_binding_code(p_child_uid uuid, p_child_name text, p_type text)
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, auth, extensions, pg_temp AS $$
DECLARE
  v_parent public.users%ROWTYPE;
  v_child uuid;
  v_code text;
  v_email text;
  v_password text;
  i integer;
BEGIN
  SELECT * INTO v_parent FROM public.users WHERE uid = auth.uid() AND role = 'parent' AND family_id IS NOT NULL;
  IF NOT FOUND THEN RAISE EXCEPTION '仅家长可生成绑定码'; END IF;
  IF p_type NOT IN ('task', 'monitor', 'literacy') THEN RAISE EXCEPTION '无效绑定类型'; END IF;
  IF p_child_uid IS NOT NULL THEN
    SELECT uid INTO v_child FROM public.users WHERE uid = p_child_uid AND family_id = v_parent.family_id AND role = 'child';
    IF v_child IS NULL THEN RAISE EXCEPTION '孩子不属于当前家庭'; END IF;
    IF p_type = 'task' THEN
      SELECT code INTO v_code FROM public.binding_codes WHERE child_uid = v_child AND type = 'task' AND status = 'active' ORDER BY created_at DESC LIMIT 1;
      IF v_code IS NOT NULL THEN RETURN v_code; END IF;
    END IF;
  ELSE
    IF nullif(trim(p_child_name), '') IS NULL THEN RAISE EXCEPTION '孩子名称不能为空'; END IF;
    v_child := gen_random_uuid();
    v_email := 'child_' || replace(v_child::text, '-', '') || '@lemonkids.auto';
    v_password := 'LmK!d_' || md5(v_child::text || 'salt_lemon_2024');
    INSERT INTO auth.users (instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,created_at,updated_at,raw_user_meta_data,raw_app_meta_data,confirmation_token,recovery_token,email_change,email_change_token_new,email_change_confirm_status)
      VALUES ('00000000-0000-0000-0000-000000000000',v_child,'authenticated','authenticated',v_email,crypt(v_password,gen_salt('bf')),now(),now(),now(),jsonb_build_object('role','child','name',p_child_name),jsonb_build_object('provider','email','providers',array['email']),'','','','',0);
    INSERT INTO auth.identities (id,user_id,identity_data,provider,provider_id,last_sign_in_at,created_at,updated_at)
      VALUES (gen_random_uuid(),v_child,jsonb_build_object('sub',v_child::text,'email',v_email),'email',v_child::text,now(),now(),now());
    INSERT INTO public.users(uid,name,role,family_id) VALUES(v_child,p_child_name,'child',v_parent.family_id);
  END IF;
  -- 家长更新已有孩子密码，保持与客户端确定性推导一致。
  v_password := 'LmK!d_' || md5(v_child::text || 'salt_lemon_2024');
  UPDATE auth.users SET encrypted_password = crypt(v_password,gen_salt('bf')),updated_at = now() WHERE id = v_child;
  UPDATE public.binding_codes SET status = 'expired' WHERE child_uid = v_child AND type = p_type AND status = 'active';
  FOR i IN 1..20 LOOP
    v_code := lpad(floor(random()*1000000)::text,6,'0');
    BEGIN
      INSERT INTO public.binding_codes(code,family_id,child_uid,type,status,expires_at)
        VALUES(v_code,v_parent.family_id,v_child,p_type,'active','infinity');
      RETURN v_code;
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
  END LOOP;
  RAISE EXCEPTION '生成唯一码失败';
END $$;

CREATE OR REPLACE FUNCTION public.get_child_binding_codes(p_family_id uuid)
RETURNS TABLE(code text,child_uid uuid,type text,status text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, auth, pg_temp AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.users WHERE uid = auth.uid() AND role = 'parent' AND family_id = p_family_id) THEN
    RAISE EXCEPTION '无权查看绑定码';
  END IF;
  RETURN QUERY SELECT bc.code,bc.child_uid,bc.type,bc.status FROM public.binding_codes bc
    WHERE bc.family_id = p_family_id AND bc.status = 'active' ORDER BY bc.created_at DESC;
END $$;

CREATE OR REPLACE FUNCTION public.exchange_binding_code(p_code text,p_device_id text,p_type text,p_force_rebind boolean DEFAULT false)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, auth, pg_temp AS $$
DECLARE v_bc public.binding_codes%ROWTYPE; v_email text;
BEGIN
  IF p_code !~ '^[0-9]{6}$' OR p_type NOT IN ('task','monitor','literacy') OR nullif(trim(p_device_id),'') IS NULL THEN
    RETURN jsonb_build_object('status','not_found');
  END IF;
  SELECT * INTO v_bc FROM public.binding_codes WHERE code=p_code AND type=p_type FOR UPDATE;
  IF NOT FOUND THEN RETURN jsonb_build_object('status','not_found'); END IF;
  IF v_bc.status = 'expired' THEN RETURN jsonb_build_object('status','expired'); END IF;
  IF p_type <> 'task' AND v_bc.device_id IS NOT NULL AND v_bc.device_id <> p_device_id AND NOT p_force_rebind THEN
    RETURN jsonb_build_object('status','already_bound','device_id',v_bc.device_id);
  END IF;
  SELECT email INTO v_email FROM auth.users WHERE id=v_bc.child_uid;
  IF v_email IS NULL THEN RAISE EXCEPTION '孩子账号不存在'; END IF;
  UPDATE public.binding_codes SET device_id=CASE WHEN p_type='task' THEN device_id ELSE p_device_id END,
    status=CASE WHEN p_type='task' THEN 'active' ELSE 'used' END WHERE id=v_bc.id;
  RETURN jsonb_build_object('status','success','child_uid',v_bc.child_uid,'family_id',v_bc.family_id,'email',v_email);
END $$;
REVOKE ALL ON FUNCTION public.generate_binding_code(uuid,text,text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_child_binding_codes(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.exchange_binding_code(text,text,text,boolean) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.generate_binding_code(uuid,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_child_binding_codes(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.exchange_binding_code(text,text,text,boolean) TO anon,authenticated;
