# Supabase：数据库与 RPC 运维说明

> 本目录保存当前项目已使用的 SQL 脚本。它们是部署历史资料，**不是已验证的、可从空库直接顺序执行的迁移链**。

## 脚本地图

| 文件 | 用途 | 前置条件 |
| --- | --- | --- |
| `sql/init.sql` | 初始业务表、RLS、Storage 策略、任务/奖励 RPC | 空项目或兼容的既有 schema |
| `sql/patch.sql` | 邀请码、RLS、限时字段、设备日志等增量 | 已有初始表；重复执行约束语句前需检查状态 |
| `sql/functions-fix.sql` | 重新创建任务/奖励 RPC 的历史修复 | 已有相关表；会覆盖函数定义 |
| `sql/binding-codes-patch.sql` | 绑定码 RPC 修复 | `binding_codes` 表及相关 RPC 已由既有环境创建 |
| `sql/20260901_task_templates.sql` | 创建家庭任务模板表和 RLS 策略 | 已有 `families`、`users` 表；家长端任务管理上线前执行 |
| `sql/20260904_task_history_and_cancellation.sql` | 将任务删除改为受控取消；保留历史任务，并提供完成/撤销完成的原子 RPC | 已有任务、用户、积分流水及 `20260812_task_calendar_core.sql` 的任务状态/RPC |
| `sql/20260904_remove_task_rejection.sql` | 移除已完成任务的家长驳回 RPC；保留既有驳回历史及积分流水 | 已执行创建 `reject_task` 的旧脚本 |
| `sql/20260901_reset_tasks_and_child_points.sql` | 清空全部任务、孩子积分及其积分流水 | 破坏性维护脚本；执行前确认目标环境与备份 |
| `sql/20260731_literacy_pronunciation_evaluation.sql` | 已废弃的旧版认字评测建表脚本 | 勿执行；已由 20260802 清理迁移替代 |
| `sql/20260731_literacy_pronunciation_batch_upgrade.sql` | 已废弃的旧版评测表升级脚本 | 勿执行 |
| `sql/20260731_literacy_pronunciation_pending_attempt.sql` | 已废弃的旧版评测状态升级脚本 | 勿执行 |
| `sql/20260801_literacy_help_requests.sql` | 孩子请求朗读内容的记录表 | 已有 `child_literacy_characters` |
| `sql/20260801_literacy_help_content.sql` | 将旧版按字求助记录迁移为按词/句记录并去重 | 已执行求助记录表脚本 |
| `sql/20260802_remove_literacy_pinyin.sql` | 删除认字模块的拼音字段与逐字拼音表 | 已切换腾讯内置词典评测 |
| `sql/20260802_simplify_literacy_schema.sql` | 删除重复词句表、旧评测历史表和求助表冗余字段 | 已执行拼音清理脚本；会删除旧评测历史 |
| `sql/20260804_literacy_learning_items.sql` | 新建字、词、句独立学习项记录表，并增加整字首次学习时间 | 三星学习状态上线前执行 |
| `sql/20260804_recognized_characters.sql` | 新建独立的已认识字表，供首页已认识分组使用 | 已有孩子登录与认字模块 |
| `sql/20260806_literacy_help_request_clicked_character.sql` | 求助记录保存被长按的具体字和位置 | 已执行 `20260805_literacy_help_request_sources.sql` |
| `sql/20260806_literacy_tts_assets.sql` | 为字、词、句添加点读音频元数据，并创建 TTS 生成/清理资产队列表 | 已执行 `20260804` 认字学习项与已认识字表迁移 |
| `sql/20260806_literacy_tts_storage.sql` | 创建公开只读的 `literacy-audio` bucket；客户端没有写策略，生成/清理 SCF 以 `service_role` 写入 | 已执行 TTS 资产表迁移；确认教学音频可公开读取 |
| `sql/20260806_literacy_tts_storage_paths.sql` | 强制 TTS 资产按版本、根任务/已认识字、类别和顺序保存，禁止全局文本哈希路径 | 已执行 TTS 资产表与 Storage bucket 脚本 |
| `sql/20260806_literacy_tts_generator_rpc.sql` | 为音频生成 SCF 提供原子抢占、日字符硬限、对象校验后回写和延后处理 RPC | 已执行 TTS 资产、Storage 与对象路径约束脚本 |
| `sql/20260807_literacy_tts_cleanup.sql` | 已认识字存库后的原子投递、可重试对象删除和最终移除已认识记录 RPC | 已执行 TTS 资产、生成器 RPC 与对象路径约束脚本 |
| `sql/20260809_literacy_tts_purge_deleted_assets.sql` | 清理成功后自动物理删除已删除资产记录 | 已执行 `20260807_literacy_tts_cleanup.sql` |
| `sql/20260811_backfill_recognized_character_audio.sql` | 回填旧版自动收录时遗漏的主字音频元数据 | 已执行 TTS 音频元数据迁移；仅需对受影响历史数据执行一次 |
| `sql/20260823_literacy_phonetic_assets.sql` | 创建词句腾讯数字拼音资产队列、领取/完成/失败 RPC，并回填历史待认识/已认识内容 | 已执行认字任务与已认识字迁移及本迁移；不向客户端授予资产权限 |
| `sql/20260823_literacy_phonetic_asset_lifecycle_atomic.sql` | 将待认识完成、已认识存库与音素资产迁移/清理合并为原子 RPC | 已执行 `20260823_literacy_phonetic_assets.sql`、`20260807_literacy_tts_cleanup.sql` 及本迁移 |
| `sql/20260823_literacy_purge_legacy_example_pinyins.sql` | 从待认识、已认识词句 JSON 中物理删除已废弃的 `pinyins` 键 | 已在旧客户端淘汰、音素资产回填完成后执行；脚本末尾四项计数均为 0 |

## AI 必须遵守的规则

1. 不要把上述脚本当作自动部署流水线，也不要凭猜测在生产库执行。
2. `binding-codes-patch.sql` 不创建 `binding_codes` 表，而 Android 端调用 `generate_binding_code`、`exchange_binding_code`、`get_child_binding_codes`。空环境部署前必须先补齐并审查该表与全部 RPC 的基线迁移。
3. 表、RLS、RPC 的任何变动必须同时检查 `shared/repository/impl/`、三个 Android 应用和对应的绑定码/积分流程。
4. 不要在客户端、SQL 或文档提交 service-role 密钥；RLS 变更必须以最小权限为目标。

## 当前人工操作

- 在 Supabase Dashboard 创建 `voices`、`photos`、`avatars` Storage bucket 后，才可应用 `init.sql` 中的 Storage 策略。
- 在目标项目的 SQL Editor 执行 `sql/20260806_literacy_tts_storage.sql`，创建 `literacy-audio` bucket。该脚本只在 bucket 不存在时创建；同名 bucket 若不是公开读取会中止，避免静默放宽权限。执行后运行脚本末尾两段查询，确认 bucket 配置正确且没有客户端写策略。
- 随后执行 `sql/20260806_literacy_tts_storage_paths.sql`，使 `literacy_tts_assets.object_path` 只能保存约定的相对路径。该约束是后续生成/清理 SCF 的删除隔离保护，不会写入或删除现有对象。
- 部署 `cloud-functions/generate-literacy-audio/` 前，审查并执行 `sql/20260806_literacy_tts_generator_rpc.sql`。该脚本仅给 `service_role` 授予生成器 RPC 权限；不要给 anon、authenticated 或客户端增加资产表/Storage 写权限。
- 已在目标 Supabase 审查并执行 `sql/20260807_literacy_tts_cleanup.sql` 和 `sql/20260809_literacy_tts_purge_deleted_assets.sql`。为 `generate-literacy-audio` 配置每 5 分钟一次的 `{ "action": "cleanup", "limit": 50, "concurrency": 3 }` 事件触发器，以及每天一次的 `{ "action": "reconcile", "limit": 1000, "concurrency": 3 }` 对账触发器。清理器按资产精确删除对象，并在关联对象全部删除成功后自动物理删除资产记录；对账器会删除专用 bucket 中没有非 `deleted` 资产记录的孤儿对象，并输出可告警日志。
- Realtime replication 的启用仍需在 Dashboard 核对；以脚本注释和实际控制台状态为准。
- 上线任务历史保留规则前，在目标项目的 SQL Editor 审查并执行 `sql/20260904_task_history_and_cancellation.sql`。执行后，删除只会取消上海时区当天及之后的待完成任务；回收站只可物理清理没有完成或积分历史的已取消任务。
- 移除任务驳回能力前，在目标项目的 SQL Editor 审查并执行 `sql/20260904_remove_task_rejection.sql`。该脚本会删除 `reject_task` RPC，但不会删除既有的已驳回任务和积分流水。
- 已执行初版求助表脚本的环境，先执行 `sql/20260801_literacy_help_content.sql`，再依次执行两个 `20260802` 认字迁移、`sql/20260804_literacy_learning_items.sql`、`sql/20260804_recognized_characters.sql`、`sql/20260805_literacy_help_request_sources.sql`、`sql/20260806_literacy_help_request_clicked_character.sql` 和 `sql/20260806_literacy_tts_assets.sql`；完成后不要再执行旧评测或旧建表脚本。

## 后续迁移规范

新增数据库变更时，应新增编号迁移文件、说明依赖/回滚/验证查询，并在临时 Supabase 项目完成从空库验证后再标记为可自动执行。完成基线整理前，请继续把本目录脚本视为人工审查对象。

认字业务数据规则见 [学习与数据规则](../kid-literacy-app/docs/LEARNING-RULES.md)，评测云函数边界见 [评测云函数说明](../cloud-functions/evaluate-reading/README.md)，音频资产与清理运维见 [音频生成云函数说明](../cloud-functions/generate-literacy-audio/README.md)。
