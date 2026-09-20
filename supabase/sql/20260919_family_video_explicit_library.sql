-- 柠檬视频：由“同步根目录自动发现”迁移至“手工条目 + 显式父子媒体库”。
-- 前置：已执行 20260906_family_video_library.sql 及 all_family_access.sql。
-- 云盘目录本身不会被本迁移创建、移动或改名。

alter table public.video_collections
    add column if not exists parent_id uuid references public.video_collections(id) on delete cascade,
    add column if not exists media_type text not null default 'series'
        check (media_type in ('series', 'movie')),
    add column if not exists drive_folder_path text;

-- 旧同步生成的条目全部保留为顶层剧集，避免升级后丢失已同步的视频。
update public.video_collections
set media_type = 'series'
where media_type is null or media_type not in ('series', 'movie');

create index if not exists video_collections_family_parent_name_idx
    on public.video_collections (family_id, parent_id, name);
create index if not exists video_collections_top_level_name_idx
    on public.video_collections (family_id, name)
    where parent_id is null;

-- 封面只保存于受控公共 bucket，文件路径首段必须是当前家庭 ID。
insert into storage.buckets (id, name, public)
values ('video-covers', 'video-covers', true)
on conflict (id) do update set public = true;

drop policy if exists "video covers family insert" on storage.objects;
drop policy if exists "video covers family update" on storage.objects;
drop policy if exists "video covers family delete" on storage.objects;
drop policy if exists "video covers public read" on storage.objects;

create policy "video covers family insert" on storage.objects for insert to authenticated
with check (
    bucket_id = 'video-covers'
    and (storage.foldername(name))[1] in (
        select family_id::text from public.users where uid = auth.uid()
    )
);

create policy "video covers family update" on storage.objects for update to authenticated
using (
    bucket_id = 'video-covers'
    and (storage.foldername(name))[1] in (
        select family_id::text from public.users where uid = auth.uid()
    )
)
with check (
    bucket_id = 'video-covers'
    and (storage.foldername(name))[1] in (
        select family_id::text from public.users where uid = auth.uid()
    )
);

create policy "video covers family delete" on storage.objects for delete to authenticated
using (
    bucket_id = 'video-covers'
    and (storage.foldername(name))[1] in (
        select family_id::text from public.users where uid = auth.uid()
    )
);

create policy "video covers public read" on storage.objects for select
using (bucket_id = 'video-covers');
