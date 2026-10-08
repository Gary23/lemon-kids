-- 在迁移已部署的数据库执行；使用现有家长与孩子，所有测试写入在末尾回滚。
begin;
do $$
begin
    perform set_config('request.jwt.claim.sub', (
        select p.uid::text from public.users p where p.role = 'parent'
        and exists(select 1 from public.users c where c.family_id = p.family_id and c.role = 'child') limit 1
    ), true);
end;
$$;
set local role authenticated;
do $$
declare
    v_parent uuid := auth.uid();
    v_family uuid;
    v_child uuid;
    v_other_child uuid;
    v_old integer;
    v_version integer;
    v_new integer;
    v_result public.badge_progress%rowtype;
    v_audit_before integer;
    v_denied boolean;
begin
    select family_id into v_family from public.users where uid = v_parent and role = 'parent';
    select uid into v_child from public.users where family_id = v_family and role = 'child' limit 1;
    if v_family is null or v_child is null then raise exception 'Test family unavailable'; end if;
    select value, version into v_old, v_version from public.badge_progress
        where child_id = v_child and badge_key = 'recognition' and segment = '';
    v_new := case when v_old = 5 then 6 else 5 end;
    select count(*) into v_audit_before from public.badge_progress_audit
        where child_id = v_child and badge_key = 'recognition';
    select * into v_result from public.set_badge_progress(v_child,'recognition','',v_new,coalesce(v_version,0));
    if v_result.value <> v_new or v_result.version <> coalesce(v_version,0)+1 then
        raise exception 'Set absolute value/version failed';
    end if;
    perform public.set_badge_progress(v_child,'recognition','',v_new,v_result.version);
    if (select count(*) from public.badge_progress_audit where child_id = v_child and badge_key = 'recognition') <> v_audit_before+1 then
        raise exception 'Idempotent write produced extra audit';
    end if;
    if not exists(select 1 from public.badge_progress_audit where child_id = v_child and badge_key = 'recognition'
        and old_value = coalesce(v_old,0) and new_value = v_new and changed_by = v_parent) then
        raise exception 'Correction audit missing';
    end if;
    v_denied := false;
    begin perform public.set_badge_progress(v_child,'recognition','',10,coalesce(v_version,0));
    exception when others then v_denied := true; end;
    if not v_denied then raise exception 'Stale version accepted'; end if;
    v_denied := false;
    begin perform public.set_badge_progress(v_child,'recognition','',3001,v_result.version);
    exception when others then v_denied := true; end;
    if not v_denied then raise exception 'Over-cap value accepted'; end if;
    v_denied := false;
    begin perform public.set_badge_progress(v_child,'english_reading','A',51,0);
    exception when others then v_denied := true; end;
    if not v_denied then raise exception 'English segment cap bypassed'; end if;
    v_denied := false;
    begin update public.badge_progress set value = 1 where child_id = v_child;
    exception when others then v_denied := true; end;
    if not v_denied then raise exception 'Direct write bypassed RPC'; end if;
    v_denied := false;
    begin perform public.set_badge_progress(gen_random_uuid(),'recognition','',1,0);
    exception when others then v_denied := true; end;
    if not v_denied then raise exception 'Outside-family child accepted'; end if;
    perform set_config('request.jwt.claim.sub', v_child::text, true);
    if not exists(select 1 from public.badge_progress where child_id = v_child and badge_key = 'recognition' and value = v_new) then
        raise exception 'Child cannot read own progress'; end if;
    v_denied := false;
    begin perform public.set_badge_progress(v_child,'recognition','',8,v_result.version);
    exception when others then v_denied := true; end;
    if not v_denied then raise exception 'Child wrote own progress'; end if;
    select uid into v_other_child from public.users where role = 'child' and family_id <> v_family limit 1;
    if v_other_child is not null and exists(select 1 from public.badge_progress where child_id = v_other_child) then
        raise exception 'Child read outside family'; end if;
end;
$$;
reset role;
select 'badge progress verification passed' as result;
rollback;
