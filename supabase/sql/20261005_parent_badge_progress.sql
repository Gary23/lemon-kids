-- 家长记录专项勋章进度。先部署本文件，再安装新版家长端。
begin;

create table if not exists public.badge_progress (
    child_id uuid not null references public.users(uid) on delete cascade,
    family_id uuid not null references public.families(id) on delete cascade,
    badge_key text not null,
    segment text not null default '',
    value integer not null default 0 check (value >= 0),
    version integer not null default 1 check (version > 0),
    updated_at timestamptz not null default now(),
    updated_by uuid references public.users(uid) on delete set null,
    primary key (child_id, badge_key, segment),
    constraint badge_progress_key_check check (badge_key in (
        'recognition','dictation','reading','idioms','poems','writing',
        'english_words','english_reading','dino_english',
        'calculation','thinking_seed','math_explaining','life_skills'
    )),
    constraint badge_progress_segment_check check (
        (badge_key = 'english_reading' and segment ~ '^[A-Z]$') or
        (badge_key <> 'english_reading' and segment = '')
    ),
    constraint badge_progress_limit_check check (
        (badge_key in ('recognition','dictation') and value <= 3000) or
        (badge_key = 'idioms' and value <= 256) or
        (badge_key = 'poems' and value <= 155) or
        (badge_key = 'english_words' and value <= 1800) or
        (badge_key = 'dino_english' and value <= 437) or
        (badge_key = 'thinking_seed' and value <= 211) or
        (badge_key = 'english_reading' and value <= case segment
            when 'A' then 50 when 'B' then 50 when 'C' then 52 when 'D' then 59
            when 'E' then 55 when 'F' then 63 when 'G' then 77 when 'H' then 78
            when 'I' then 91 when 'J' then 101 when 'K' then 107 when 'L' then 128
            when 'M' then 134 when 'N' then 144 when 'O' then 117 when 'P' then 99
            when 'Q' then 98 when 'R' then 89 when 'S' then 54 when 'T' then 52
            when 'U' then 55 when 'V' then 58 when 'W' then 58 when 'X' then 59
            when 'Y' then 59 when 'Z' then 62 end) or
        (badge_key in ('reading','writing','calculation','math_explaining','life_skills'))
    )
);
create index if not exists badge_progress_family_child_idx on public.badge_progress(family_id,child_id);

create table if not exists public.badge_progress_audit (
    id uuid primary key default gen_random_uuid(),
    child_id uuid not null references public.users(uid) on delete cascade,
    family_id uuid not null references public.families(id) on delete cascade,
    badge_key text not null,
    segment text not null default '',
    old_value integer not null,
    new_value integer not null,
    changed_by uuid references public.users(uid) on delete set null,
    changed_at timestamptz not null default now()
);
create index if not exists badge_progress_audit_child_time_idx on public.badge_progress_audit(child_id,changed_at desc);

alter table public.badge_progress enable row level security;
alter table public.badge_progress_audit enable row level security;
drop policy if exists badge_progress_family_read on public.badge_progress;
create policy badge_progress_family_read on public.badge_progress for select to authenticated
    using (exists(select 1 from public.users u where u.uid = auth.uid() and u.family_id = badge_progress.family_id
        and (u.role = 'parent' or (u.role = 'child' and u.uid = badge_progress.child_id))));
drop policy if exists badge_progress_audit_family_read on public.badge_progress_audit;
create policy badge_progress_audit_family_read on public.badge_progress_audit for select to authenticated
    using (exists(select 1 from public.users u where u.uid = auth.uid() and u.family_id = badge_progress_audit.family_id
        and (u.role = 'parent' or (u.role = 'child' and u.uid = badge_progress_audit.child_id))));
revoke all on public.badge_progress, public.badge_progress_audit from anon, authenticated;
grant select on public.badge_progress, public.badge_progress_audit to authenticated;

create or replace function public.set_badge_progress(
    p_child_id uuid, p_badge_key text, p_segment text, p_value integer, p_expected_version integer
) returns public.badge_progress language plpgsql security definer set search_path = public as $$
declare
    v_family_id uuid;
    v_old public.badge_progress%rowtype;
    v_new public.badge_progress%rowtype;
begin
    select family_id into v_family_id from public.users where uid = auth.uid() and role = 'parent';
    if v_family_id is null or not exists(select 1 from public.users
        where uid = p_child_id and family_id = v_family_id and role = 'child') then
        raise exception 'Child is outside current parent family';
    end if;
    if p_value is null or p_value < 0 or p_expected_version is null or p_expected_version < 0 then
        raise exception 'Invalid value or version';
    end if;
    -- 表约束同样校验 key、segment、上限；函数先给明确的客户端错误。
    if p_badge_key not in ('recognition','dictation','reading','idioms','poems','writing',
        'english_words','english_reading','dino_english','calculation','thinking_seed',
        'math_explaining','life_skills') or p_segment is null or
       (p_badge_key = 'english_reading' and p_segment !~ '^[A-Z]$') or
       (p_badge_key <> 'english_reading' and p_segment <> '') then
        raise exception 'Invalid badge key or segment';
    end if;
    -- 同一孩子的并发首次写入也要串行；版本冲突由下方检查返回。
    perform pg_advisory_xact_lock(hashtextextended(p_child_id::text || ':' || p_badge_key || ':' || p_segment, 0));
    select * into v_old from public.badge_progress
        where child_id = p_child_id and badge_key = p_badge_key and segment = p_segment for update;
    if found then
        if v_old.version <> p_expected_version then raise exception 'Badge progress version conflict'; end if;
        if v_old.value = p_value then return v_old; end if;
        update public.badge_progress set value = p_value, version = version + 1,
            updated_at = now(), updated_by = auth.uid()
        where child_id = p_child_id and badge_key = p_badge_key and segment = p_segment
        returning * into v_new;
    else
        if p_expected_version <> 0 then raise exception 'Badge progress version conflict'; end if;
        insert into public.badge_progress(child_id,family_id,badge_key,segment,value,version,updated_by)
        values(p_child_id,v_family_id,p_badge_key,p_segment,p_value,1,auth.uid()) returning * into v_new;
    end if;
    insert into public.badge_progress_audit(child_id,family_id,badge_key,segment,old_value,new_value,changed_by)
    values(p_child_id,v_family_id,p_badge_key,p_segment,coalesce(v_old.value,0),p_value,auth.uid());
    return v_new;
end;
$$;
revoke all on function public.set_badge_progress(uuid,text,text,integer,integer) from public, anon;
grant execute on function public.set_badge_progress(uuid,text,text,integer,integer) to authenticated;
commit;
