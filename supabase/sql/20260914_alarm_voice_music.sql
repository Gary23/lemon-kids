-- 远程闹钟首期“语音 + 音乐”配置。
-- 先执行本迁移，再发布会写入这些字段的家长端；文本语音的云端缓存表另行迁移，
-- 本版本 Pad 会以本地 TTS 保障离线播放。

ALTER TABLE alarms
    ADD COLUMN IF NOT EXISTS background_music_id TEXT NOT NULL DEFAULT 'gentle_bell_v1',
    ADD COLUMN IF NOT EXISTS voice_enabled BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS voice_text TEXT NOT NULL DEFAULT '';

ALTER TABLE alarms DROP CONSTRAINT IF EXISTS alarms_background_music_id_check;
ALTER TABLE alarms ADD CONSTRAINT alarms_background_music_id_check
    CHECK (background_music_id IN ('gentle_bell_v1', 'seaside_sunrise_v1'));
ALTER TABLE alarms DROP CONSTRAINT IF EXISTS alarms_voice_text_length_check;
ALTER TABLE alarms ADD CONSTRAINT alarms_voice_text_length_check
    CHECK (char_length(voice_text) <= 200);

-- 兼容既有闹钟：下一次编辑前也可获得与客户端相同的标题 + 提醒内容播报文本。
UPDATE alarms
SET voice_text = left(trim(concat_ws('。', nullif(trim(title), ''), nullif(trim(message), ''))), 200)
WHERE voice_text = '';

-- 音乐/语音配置变化同样是一个新 revision，必须重置 Pad 投递状态。
DROP TRIGGER IF EXISTS trg_sync_alarm_delivery ON alarms;
CREATE TRIGGER trg_sync_alarm_delivery
AFTER INSERT OR UPDATE OF revision, target_device_id, enabled, trigger_at, title, message,
    background_music_id, voice_enabled, voice_text, requires_confirmation ON alarms
FOR EACH ROW EXECUTE FUNCTION sync_alarm_delivery();
