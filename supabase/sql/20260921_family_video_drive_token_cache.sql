-- 柠檬视频：跨 Edge 冷启动复用 123 云盘的短期应用级 access token。
-- 前置：已执行 20260906_family_video_library.sql；本脚本须经人工审查后在目标项目 SQL Editor 执行。
-- 安全边界：这是用户明确接受的取舍。token 仅由 Edge Function 的 service_role 读写，
-- 不向 anon/authenticated 授权，也不创建任何 RLS 策略。

begin;

create table if not exists public.video_drive_token_cache (
    provider text primary key check (provider = '123pan'),
    access_token text not null,
    expires_at timestamptz not null,
    updated_at timestamptz not null default now()
);

alter table public.video_drive_token_cache enable row level security;
revoke all on table public.video_drive_token_cache from anon, authenticated;

comment on table public.video_drive_token_cache is
    '仅 family-video-drive Edge Function 以 service_role 持有的短期 123 应用级 access token；严禁客户端访问、日志输出或备份导出。';

commit;

-- 执行后核验：应只有 123pan 一条或零条记录；rowsecurity 为 true；策略数为 0。
select provider, expires_at, updated_at
from public.video_drive_token_cache;

select c.relname, c.relrowsecurity
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relname = 'video_drive_token_cache';

select policyname
from pg_policies
where schemaname = 'public' and tablename = 'video_drive_token_cache';

-- 回滚（仅在先部署不依赖本表的函数版本后执行；会删除缓存 token，不影响媒体库）：
-- drop table if exists public.video_drive_token_cache;
