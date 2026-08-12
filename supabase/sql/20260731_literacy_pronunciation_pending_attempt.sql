-- 新版腾讯智聆 Android SDK 在 Pad 开始录音前，需要先由云函数创建评测记录。
-- 本迁移为既有状态约束增加 pending；可在 Supabase SQL Editor 安全执行一次。

alter table public.child_literacy_reading_attempts
    drop constraint if exists child_literacy_reading_attempts_status_check;

alter table public.child_literacy_reading_attempts
    add constraint child_literacy_reading_attempts_status_check
    check (status in ('pending', 'completed', 'failed', 'cancelled'));
