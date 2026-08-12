-- 认字点读腾讯 TTS 的独立对象存储。
--
-- 前置条件：已执行 20260806_literacy_tts_assets.sql。
-- 本脚本按当前产品约定将教学音频设为公开可读：对象只包含字、词、句的
-- 合成音频，不包含孩子录音或个人资料。客户端仍没有上传、更新、删除权限；
-- 这些写操作由 SCF 使用 service_role 完成（service_role 会绕过 RLS）。
--
-- 若未来需要改为私有 bucket，不能直接切换 public 字段：必须先实现客户端
-- 运行时短期签名 URL，并停止在主表/JSON 中持久化签名 URL。

do $$
declare
    existing_public boolean;
begin
    select public
      into existing_public
      from storage.buckets
     where id = 'literacy-audio';

    if not found then
        insert into storage.buckets (
            id,
            name,
            public,
            allowed_mime_types
        )
        values (
            'literacy-audio',
            'literacy-audio',
            true,
            array['audio/mpeg']::text[]
        );
    elsif existing_public is distinct from true then
        raise exception
            'Storage bucket "literacy-audio" 已存在但不是公开读取；请先确认产品读取策略，勿直接覆盖配置。';
    end if;
end
$$;

-- 不为 literacy-audio 创建 storage.objects 的 INSERT / UPDATE / DELETE 策略。
-- Storage 的 RLS 默认拒绝客户端写入；service_role 不受此限制，供生成/清理 SCF
-- 精确写入和删除。公开 bucket 的对象下载无需 SELECT 策略。

-- 部署后验证（应返回 public=true 和 audio/mpeg）：
-- select id, public, allowed_mime_types
-- from storage.buckets where id = 'literacy-audio';
--
-- 客户端角色不应有针对本 bucket 的写策略（应返回 0 行）：
-- select policyname, cmd, roles, qual, with_check
-- from pg_policies
-- where schemaname = 'storage'
--   and tablename = 'objects'
--   and cmd in ('INSERT', 'UPDATE', 'DELETE')
--   and (qual ilike '%literacy-audio%' or with_check ilike '%literacy-audio%');
