-- 家庭动画 App 媒体库：执行前确认 users、families 已由 init.sql 创建。
-- 仅同步云盘目录和元数据；授权令牌、云盘密码和临时播放链接禁止写入本库。

create table if not exists public.video_drive_connections (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null unique references public.families(id) on delete cascade,
    provider text not null default '123pan' check (provider in ('123pan')),
    drive_account_hint text,
    sync_root_folder_id text,
    sync_root_path text,
    authorization_status text not null default 'disconnected'
        check (authorization_status in ('disconnected', 'connected', 'expired', 'error')),
    last_synced_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.video_categories (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null references public.families(id) on delete cascade,
    name text not null check (char_length(btrim(name)) > 0),
    sort_order integer not null default 0,
    is_builtin boolean not null default false,
    created_at timestamptz not null default now(),
    unique (family_id, name)
);

create table if not exists public.video_collections (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null references public.families(id) on delete cascade,
    drive_folder_id text not null,
    name text not null,
    cover_url text,
    category_id uuid references public.video_categories(id) on delete set null,
    sync_status text not null default 'ready' check (sync_status in ('ready', 'unavailable', 'error')),
    last_synced_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (family_id, drive_folder_id)
);

create table if not exists public.video_media (
    id uuid primary key default gen_random_uuid(),
    collection_id uuid not null references public.video_collections(id) on delete cascade,
    drive_file_id text not null,
    name text not null,
    path text,
    size_bytes bigint,
    duration_seconds bigint,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (collection_id, drive_file_id)
);

create table if not exists public.video_playback_records (
    media_id uuid primary key references public.video_media(id) on delete cascade,
    progress_seconds bigint not null default 0 check (progress_seconds >= 0),
    duration_seconds bigint not null default 0 check (duration_seconds >= 0),
    is_completed boolean not null default false,
    last_played_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.video_sync_logs (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null references public.families(id) on delete cascade,
    added_count integer not null default 0,
    updated_count integer not null default 0,
    unavailable_count integer not null default 0,
    error_message text,
    created_at timestamptz not null default now()
);

create index if not exists video_collections_family_category_idx on public.video_collections (family_id, category_id);
create index if not exists video_media_collection_sort_idx on public.video_media (collection_id, sort_order);
create index if not exists video_sync_logs_family_created_idx on public.video_sync_logs (family_id, created_at desc);

alter table public.video_drive_connections enable row level security;
alter table public.video_categories enable row level security;
alter table public.video_collections enable row level security;
alter table public.video_media enable row level security;
alter table public.video_playback_records enable row level security;
alter table public.video_sync_logs enable row level security;

-- 家庭媒体仅家长账号可管理；孩子端未来若接入，另建只读策略。
drop policy if exists "video connections parent access" on public.video_drive_connections;
create policy "video connections parent access" on public.video_drive_connections for all
using (family_id in (select family_id from public.users where uid = auth.uid() and role = 'parent'))
with check (family_id in (select family_id from public.users where uid = auth.uid() and role = 'parent'));

drop policy if exists "video categories parent access" on public.video_categories;
create policy "video categories parent access" on public.video_categories for all
using (family_id in (select family_id from public.users where uid = auth.uid() and role = 'parent'))
with check (family_id in (select family_id from public.users where uid = auth.uid() and role = 'parent'));

drop policy if exists "video collections parent access" on public.video_collections;
create policy "video collections parent access" on public.video_collections for all
using (family_id in (select family_id from public.users where uid = auth.uid() and role = 'parent'))
with check (family_id in (select family_id from public.users where uid = auth.uid() and role = 'parent'));

drop policy if exists "video media parent access" on public.video_media;
create policy "video media parent access" on public.video_media for all
using (exists (select 1 from public.video_collections c join public.users u on u.family_id = c.family_id where c.id = collection_id and u.uid = auth.uid() and u.role = 'parent'))
with check (exists (select 1 from public.video_collections c join public.users u on u.family_id = c.family_id where c.id = collection_id and u.uid = auth.uid() and u.role = 'parent'));

drop policy if exists "video playback parent access" on public.video_playback_records;
create policy "video playback parent access" on public.video_playback_records for all
using (exists (select 1 from public.video_media m join public.video_collections c on c.id = m.collection_id join public.users u on u.family_id = c.family_id where m.id = media_id and u.uid = auth.uid() and u.role = 'parent'))
with check (exists (select 1 from public.video_media m join public.video_collections c on c.id = m.collection_id join public.users u on u.family_id = c.family_id where m.id = media_id and u.uid = auth.uid() and u.role = 'parent'));

drop policy if exists "video sync logs parent access" on public.video_sync_logs;
create policy "video sync logs parent access" on public.video_sync_logs for all
using (family_id in (select family_id from public.users where uid = auth.uid() and role = 'parent'))
with check (family_id in (select family_id from public.users where uid = auth.uid() and role = 'parent'));
