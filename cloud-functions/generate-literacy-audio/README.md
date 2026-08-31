# 认字教学音频生成云函数

部署目标：腾讯云 SCF **事件函数** `generate-literacy-audio`，Node.js 18 运行时，执行方法为 `index.main_handler`。它没有 Web/API Gateway 入口：仅能由拥有 `scf:InvokeFunction` 权限的管理端、SCF 控制台或定时触发器执行，孩子端不能调用。

函数按事务抢占 `literacy_tts_assets` 队列，使用腾讯云短文本 `TextToVoice` 合成 MP3。对象上传至公开只读 bucket `literacy-audio` 后，必须通过上传 HTTP 状态、对象 `HEAD` 的 `audio/mpeg` MIME 与非零 `Content-Length` 校验，才会在同一数据库事务回写资产状态、主字 URL 与词/句 JSON 的音频字段。

## 当前音频规格与验收状态

- 正式使用爱小芊：`VoiceType=601009`、`ModelType=1`、`Codec=mp3`、`SampleRate=16000`、`voiceVersion=v1`；情绪为 `neutral`、强度为 `100`。
- 主字仅传入单个汉字，字、词、句均使用 `Speed=-1`；词和句保留教学原文及标点。
- 已完成 20 条样本（含多音字、词和句）及目标 Android 平板试听，播放清晰自然。认字端通过 Media3 缓存优先播放生成音频，失败时回退系统 TTS。
- 已完成首次全量生成：161 条资产、569 个字符，均成功；每日字符硬上限为 10,000。真实环境仍须使用测试孩子和临时教学数据补验“系统收录后归档”及“删除失败后重试”，详见下方测试说明。

## 前置数据库操作

先在目标 Supabase 的 SQL Editor 审查并执行：

```text
supabase/sql/20260806_literacy_tts_generator_rpc.sql
```

它会创建原子抢占、每日字符额度、完成回写和延后处理 RPC；只授予 `service_role` 调用权限。随后还必须审查并执行：

```text
supabase/sql/20260807_literacy_tts_cleanup.sql
```

再执行：

```text
supabase/sql/20260809_literacy_tts_purge_deleted_assets.sql
```

前两个脚本创建“存库后投递删除”事务、删除任务抢占与删除完成回写 RPC，并将资产状态扩展为 `deleting`；第三个脚本使最后一个关联音频删除成功后自动移除已删除资产记录。若目标项目已有默认函数权限，脚本会显式撤销 `anon`、`authenticated` 的执行权。执行后可验证：

```sql
select routine_name, grantee, privilege_type
from information_schema.role_routine_grants
where routine_schema = 'public'
  and routine_name in (
    'enqueue_literacy_tts_assets', 'claim_literacy_tts_assets',
    'reserve_literacy_tts_characters', 'mark_literacy_tts_asset_ready',
    'defer_literacy_tts_asset', 'archive_recognized_character',
    'claim_literacy_tts_assets_for_deletion', 'mark_literacy_tts_asset_deleted',
    'defer_literacy_tts_asset_deletion'
  )
order by routine_name, grantee;
```

## 环境变量

在 SCF 配置中填写以下变量。密钥只保存在 SCF 配置，绝不能写入仓库、Android 或日志。

```text
TENCENT_TTS_SECRET_ID
TENCENT_TTS_SECRET_KEY
TENCENT_TTS_REGION
TENCENT_TTS_VOICE_TYPE=601009
TENCENT_TTS_MODEL_TYPE=1
TENCENT_TTS_CODEC=mp3
TENCENT_TTS_SAMPLE_RATE=16000
TENCENT_TTS_VOICE_VERSION=v1
TENCENT_TTS_DAILY_CHARACTER_LIMIT=10000
SUPABASE_URL
SUPABASE_SERVICE_ROLE_KEY
```

可选变量：`TENCENT_TTS_MAX_ATTEMPTS=3`（每条资产跨批次最多尝试次数，范围 1～10）。当前确认的 `TENCENT_TTS_DAILY_CHARACTER_LIMIT=10000` 是费用硬闸；每次实际腾讯请求（包含可重试的临时失败）都会原子预留字符额度。

## 部署

1. 在此目录执行 `npm install --omit=dev` 与 `npm test`。测试覆盖待生成筛选、原文合成参数、任务去重/并发领取 SQL 契约、腾讯临时错误重试、对象路径、对象删除失败重试，以及“存库后异步删除”的生命周期顺序。
2. 压缩 `index.js`、`lib.js`、`package.json`、`package-lock.json`、`node_modules/`；ZIP 根目录必须直接包含这些项目。
3. 在腾讯云新建 **事件函数** `generate-literacy-audio`，选择 Node.js 18，执行方法填 `index.main_handler`，上传 ZIP 并配置环境变量。
4. 不创建函数 URL，也不绑定 API Gateway。为管理端使用的 CAM 身份仅授予此函数的 `scf:InvokeFunction`；不要授予孩子端身份。定时触发器分别配置 `task`、`recognized` 两条生成任务、一条 `cleanup` 清理任务和一条 `monitor` 监控任务。

当前部署包：`generate-literacy-audio-20260807-test-deploy-monitoring.zip`。

## 调用示例

首次先分别 dry run；dry run 会补齐缺失的资产队列，但不会调用腾讯 TTS、上传对象或回写 URL。

```json
{ "dry_run": true, "source": "task" }
```

```json
{ "dry_run": true, "source": "recognized" }
```

实际生成（默认最多 50 条、并发 3）：

```json
{ "source": "task", "limit": 50, "concurrency": 3, "only_missing_or_invalid": true }
```

只处理一条根任务或手工/导入已认识字时，传入 `record_id`：

```json
{ "source": "task", "record_id": "任务 UUID", "limit": 50 }
```

失败项须显式重试：

```json
{ "source": "task", "retry_failed": true }
```

已处于 `ready` 的资产不会被覆盖；文本、音色或语速变更时，必须提高 `TENCENT_TTS_VOICE_VERSION` 后再生成新对象。每日硬额度耗尽的资产会回到 `pending`，等待下一个自然日，不会被误记为永久失败。

## 已认识字归档后的清理

认字端请求 `archive_recognized_character` 时，评测函数会调用数据库 RPC；它先确保字已幂等写入 `known_characters`，再将该已认识字及（系统来源时）原认字任务的全部资产标记为 `delete_pending`。随后评测函数会在请求链路内逐个删除 Storage 对象、删除资产记录和复习记录；只有全部完成才向客户端返回存库成功。临时删除失败会令本次存库失败，并立即撤销本次新增的字库记录、恢复复习数据及未删除资产的原状态；失败的本次请求不会由后台继续清理，需由孩子再次点击“存库”重新发起。

保留已有的每 5 分钟清理触发器，用于处理旧版本遗留的 `delete_pending` 资产；它不处理上述已回滚的存库请求。事件内容如下；也可由有 `scf:InvokeFunction` 权限的管理端调用：

```json
{ "action": "cleanup", "limit": 50, "concurrency": 3 }
```

清理器对每个 `delete_pending` 资产精确删除 `object_path`；若对象已经不存在则按成功处理。同一已认识字的所有关联对象均删除成功后，数据库会自动物理删除这些 `deleted` 资产记录及对应的 `recognized_characters` 记录。失败项保持 `delete_pending` 并在下一次触发时重试；运行中断超过 15 分钟的 `deleting` 资产也会被安全重领。

再添加一个每天一次的对账触发器。它会递归扫描专用 `literacy-audio` bucket，并删除没有任何非 `deleted` 资产记录的对象，覆盖“数据库已标记 `deleted` 但对象仍存在”和孤儿文件两种情况；发现孤儿或失败时会输出 `literacy_tts_reconcile_alert` 日志，需为该日志配置告警：

```json
{ "action": "reconcile", "limit": 1000, "concurrency": 3 }
```

每次 dry run 还会写入一条 `literacy_tts_dry_run_complete` 日志，其中仅包含来源、候选数、预计字符数和每日额度；当控制台没有显示返回体时，可在函数的“日志查询”中查看该条记录。

## 测试、上线与监控操作手册

### 自动化测试

在本目录运行：

```bash
npm test
```

测试不访问腾讯云、Supabase 或真实音频，适合每次打包前执行。真实环境的归档端到端验收必须使用测试孩子和临时教学数据：先完成一条含字、词、句的认字任务，确认生成对象均为 `ready`；在认字端点击“存入字库”，确认所有关联对象同步删除、`known_characters` 只有一条且 `recognized_characters` 已物理删除。手工/导入的已认识字也按同样步骤单独验收。不要对生产中的学习记录执行此测试。

要验证“部分对象删除失败后重试”，应在测试环境让评测函数的 Storage 删除请求临时失败后，在认字端点击“存入字库”：确认本次新增的 `known_characters` 已被撤销、复习卡仍保留、未删资产恢复原状态且没有 `delete_pending`；恢复 Storage 后，再由孩子再次点击“存入字库”，确认全部资产及复习记录被删除。禁止对生产学习记录执行此测试。

### 定时触发器与告警

在腾讯云 SCF 的 `generate-literacy-audio` 函数中配置以下**定时触发器**（事件内容填“附加信息”）。已有的 `literacy-audio-cleanup-5min` 和 `literacy-audio-reconcile-daily` 应保留并更新为下表对应事件内容，**不要重复创建**；只新增当前不存在的触发器。

| 名称 | 建议频率 | 附加信息 |
| --- | --- | --- |
| `literacy-tts-task` | 每 15 分钟 | `{"source":"task","limit":50,"concurrency":3,"only_missing_or_invalid":true}` |
| `literacy-tts-recognized` | 每 15 分钟 | `{"source":"recognized","limit":50,"concurrency":3,"only_missing_or_invalid":true}` |
| `literacy-audio-cleanup-5min`（已有） | 每 5 分钟 | `{"action":"cleanup","limit":50,"concurrency":3}` |
| `literacy-tts-monitor`（新增） | 每 5 分钟 | `{"action":"monitor"}` |
| `literacy-audio-reconcile-daily`（已有） | 每天 03:20 | `{"action":"reconcile","limit":1000,"concurrency":3}` |

在腾讯云控制台搜索并进入“云监控” → “告警配置” → “告警策略” → “新建”，策略类型选择“云产品监控”，产品选择“云函数 SCF”。选择 `generate-literacy-audio` 所在地域和函数，创建一条基础告警：统计周期 5 分钟，函数错误次数大于 0，连续 1 次即通知。它覆盖运行时异常和监控快照本身失败；函数详情页只用于查看监控数据，不提供该告警策略入口。

随后进入 SCF 日志投递的 CLS 日志主题，启用 JSON 自动解析，并创建分析型告警。以下字段都来自 `literacy_tts_monitor_snapshot`，不会包含密钥或 URL：

| 告警 | 查询条件/阈值 | 周期 |
| --- | --- | --- |
| 资产失败 | `event=literacy_tts_failed` 的日志数大于 0 | 5 分钟 |
| 待生成积压 | `pending_assets >= 20` 或 `failed_assets > 0` | 连续 15 分钟 |
| 删除积压 | `cleanup_pending_assets > 0` 且 `oldest_cleanup_pending_seconds >= 900` | 连续 15 分钟 |
| 字符消耗 | `daily_tts_character_count >= 8000`（警告）、`>= 9500`（严重） | 5 分钟 |
| 对账异常 | `event=literacy_tts_reconcile_alert` 的日志数大于 0 | 每日 |

`literacy-audio` 位于 Supabase Storage，不在腾讯云 SCF 的存储指标中。请在 Supabase Dashboard 的 Storage 用量页对该 bucket 设置 80%（警告）和 90%（严重）容量通知；如果当前套餐没有容量通知能力，则至少设置每周巡检，并在日志告警的通知组中登记负责人。告警接收人应包含值班渠道和产品负责人。
