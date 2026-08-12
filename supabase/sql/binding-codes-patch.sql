-- 绑定码功能修复：显示已有码 + task码多设备复用 + 长期本地恢复
-- 在 Supabase SQL Editor 中执行

-- ============================================================
-- 1. 修改 generate_binding_code：task类型已有活跃码直接返回，不再重新生成失效旧的
-- ============================================================
CREATE OR REPLACE FUNCTION generate_binding_code(p_child_uid UUID, p_child_name TEXT, p_type TEXT)
RETURNS TEXT AS $$
DECLARE
    v_parent_uid UUID := auth.uid();
    v_family_id UUID;
    v_child_uid UUID;
    v_code TEXT;
    v_email TEXT;
    v_password TEXT;
    v_attempts INT := 0;
    v_existing_code TEXT;
BEGIN
    SELECT family_id INTO v_family_id
        FROM users WHERE uid = v_parent_uid AND role = 'parent';
    IF v_family_id IS NULL THEN
        RAISE EXCEPTION '只有家长可以生成绑定码';
    END IF;

    IF p_child_uid IS NOT NULL THEN
        SELECT uid INTO v_child_uid
            FROM users WHERE uid = p_child_uid
                AND family_id = v_family_id AND role = 'child';
        IF v_child_uid IS NULL THEN
            RAISE EXCEPTION '未找到该家庭中的孩子';
        END IF;

        -- 已有孩子：检查是否已有活跃的任务码（任务码允许多设备复用）
        IF p_type = 'task' THEN
            SELECT code INTO v_existing_code
                FROM binding_codes
                WHERE child_uid = v_child_uid AND type = 'task'
                    AND status = 'active'
                LIMIT 1;
            IF v_existing_code IS NOT NULL THEN
                RETURN v_existing_code;
            END IF;
        END IF;

        v_password := 'LmK!d_' || md5(v_child_uid::text || 'salt_lemon_2024');
        UPDATE auth.users
            SET encrypted_password = crypt(v_password, gen_salt('bf'))
            WHERE id = v_child_uid;
    ELSE
        IF p_child_name IS NULL OR p_child_name = '' THEN
            RAISE EXCEPTION '创建新孩子时必须提供名字';
        END IF;

        v_child_uid := gen_random_uuid();
        v_email := 'child_' || replace(v_child_uid::text, '-', '') || '@lemonkids.auto';
        v_password := 'LmK!d_' || md5(v_child_uid::text || 'salt_lemon_2024');

        INSERT INTO auth.users (
            instance_id, id, aud, role, email, encrypted_password,
            email_confirmed_at, created_at, updated_at,
            raw_user_meta_data, raw_app_meta_data,
            confirmation_token, email_change,
            email_change_token_new, email_change_confirm_status
        ) VALUES (
            '00000000-0000-0000-0000-000000000000',
            v_child_uid,
            'authenticated',
            'authenticated',
            v_email,
            crypt(v_password, gen_salt('bf')),
            now(), now(), now(),
            jsonb_build_object('role', 'child', 'name', p_child_name),
            jsonb_build_object('provider', 'email', 'providers', array['email']),
            '', '', '', 0
        );

        BEGIN
            INSERT INTO auth.identities (
                id, user_id, identity_data, provider, provider_id,
                last_sign_in_at, created_at, updated_at
            ) VALUES (
                gen_random_uuid(),
                v_child_uid,
                jsonb_build_object('sub', v_child_uid::text, 'email', v_email),
                'email',
                v_child_uid::text,
                now(), now(), now()
            ) ON CONFLICT DO NOTHING;
        EXCEPTION WHEN OTHERS THEN
            NULL;
        END;

        INSERT INTO users (uid, name, role, family_id)
        VALUES (v_child_uid, p_child_name, 'child', v_family_id);
    END IF;

    -- 使该孩子同类型的旧活跃码失效
    UPDATE binding_codes SET status = 'expired'
        WHERE child_uid = v_child_uid AND type = p_type
            AND status = 'active';

    LOOP
        v_code := lpad(floor(random() * 1000000)::text, 6, '0');
        v_attempts := v_attempts + 1;
        IF NOT EXISTS (
            SELECT 1 FROM binding_codes
                WHERE code = v_code AND status IN ('active', 'used')
        ) THEN
            EXIT;
        END IF;
        IF v_attempts >= 10 THEN
            RAISE EXCEPTION '生成唯一码失败，请重试';
        END IF;
    END LOOP;

    -- 绑定码不设置到期日；仅在家长重新生成同类型绑定码或主动删除时失效。
    -- 使用 PostgreSQL 的 infinity 保持与既有 expires_at 校验条件兼容。
    INSERT INTO binding_codes (code, family_id, child_uid, type, status, expires_at)
    VALUES (v_code, v_family_id, v_child_uid, p_type, 'active', 'infinity'::timestamptz);

    RETURN v_code;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ============================================================
-- 2. 新增 get_child_binding_codes：获取家庭中所有孩子的活跃绑定码
-- ============================================================
CREATE OR REPLACE FUNCTION get_child_binding_codes(p_family_id UUID)
RETURNS TABLE(code TEXT, child_uid UUID, type TEXT, status TEXT) AS $$
BEGIN
    RETURN QUERY
        SELECT bc.code::TEXT, bc.child_uid, bc.type::TEXT, bc.status::TEXT
        FROM binding_codes bc
        WHERE bc.family_id = p_family_id
            AND bc.status = 'active'
        ORDER BY bc.created_at DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
