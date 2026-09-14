-- 已执行 20260914_alarm_voice_music.sql 的环境需要单独扩展音乐白名单。
ALTER TABLE alarms DROP CONSTRAINT IF EXISTS alarms_background_music_id_check;
ALTER TABLE alarms ADD CONSTRAINT alarms_background_music_id_check
    CHECK (background_music_id IN ('gentle_bell_v1', 'seaside_sunrise_v1'));
