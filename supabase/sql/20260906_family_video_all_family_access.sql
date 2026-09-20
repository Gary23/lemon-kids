-- 家庭动画 App 不区分家长/孩子权限：同一家庭任意已登录成员均可读写媒体库。
-- 前置：已执行 20260906_family_video_library.sql。

drop policy if exists "video connections parent access" on public.video_drive_connections;
drop policy if exists "video categories parent access" on public.video_categories;
drop policy if exists "video collections parent access" on public.video_collections;
drop policy if exists "video media parent access" on public.video_media;
drop policy if exists "video playback parent access" on public.video_playback_records;
drop policy if exists "video sync logs parent access" on public.video_sync_logs;

create policy "video connections family access" on public.video_drive_connections for all
using (family_id in (select family_id from public.users where uid = auth.uid()))
with check (family_id in (select family_id from public.users where uid = auth.uid()));

create policy "video categories family access" on public.video_categories for all
using (family_id in (select family_id from public.users where uid = auth.uid()))
with check (family_id in (select family_id from public.users where uid = auth.uid()));

create policy "video collections family access" on public.video_collections for all
using (family_id in (select family_id from public.users where uid = auth.uid()))
with check (family_id in (select family_id from public.users where uid = auth.uid()));

create policy "video media family access" on public.video_media for all
using (exists (select 1 from public.video_collections c join public.users u on u.family_id = c.family_id where c.id = collection_id and u.uid = auth.uid()))
with check (exists (select 1 from public.video_collections c join public.users u on u.family_id = c.family_id where c.id = collection_id and u.uid = auth.uid()));

create policy "video playback family access" on public.video_playback_records for all
using (exists (select 1 from public.video_media m join public.video_collections c on c.id = m.collection_id join public.users u on u.family_id = c.family_id where m.id = media_id and u.uid = auth.uid()))
with check (exists (select 1 from public.video_media m join public.video_collections c on c.id = m.collection_id join public.users u on u.family_id = c.family_id where m.id = media_id and u.uid = auth.uid()));

create policy "video sync logs family access" on public.video_sync_logs for all
using (family_id in (select family_id from public.users where uid = auth.uid()))
with check (family_id in (select family_id from public.users where uid = auth.uid()));
