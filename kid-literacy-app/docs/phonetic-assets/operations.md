# 音素资产部署与验收

## 已部署状态（2026-08-24）

- 云函数部署包为 `evaluate-reading-web-20260823-phonetic-lifecycle-atomic.zip`。
- 已执行 `20260823_literacy_phonetic_assets.sql` 与 `20260823_literacy_phonetic_asset_lifecycle_atomic.sql`；前者建立资产队列与 RPC，后者将资产迁移、清理纳入完成和存库的原子 RPC。
- 已删除业务 `pinyins`、本地 `RefText` 拼装、统一补一声及 `F_TDET=false`；`prepare_evaluation` 只从 `ready` 资产返回 `TEXT_MODE=1` 的 `wordList`。资产未就绪时返回“正在准备发音”，不得回退 `TEXT_MODE=0`。
- 历史 563 条资产已受控回填完成（`ready=563`、`failed=0`）；历史词句 JSON 的 `pinyins` 已物理清理。
- 已实现待认识字音素详情及保存、资产迁移/清理 RPC 合约测试，并核验云函数部署包根目录结构。

## 仍需确认

- 确认定时事件 `literacy-phonetic-backfill-30min` 会每 30 分钟触发 `generate-literacy-phonetics`；当前队列为空时手动调用应返回 `claimed=0`。
- 在真机观察腾讯 `4001`、`4111`、`4113` 日志，并完成下列场景验收。

## 真机验收场景

- 新增“组”后，预览接口不返回拼音；词句保存后自动创建并生成资产。
- “组长”生成 `zu3 zhang3`，将“长”读作 `chang` 时不通过；只改变声调时不因声调判错。
- 人工保存合法数字拼音后，定时任务不覆盖它；轻声不生成 `0`、`5` 或伪造一声。
- 待认识转已认识后仍能按指定读音评测；待认识或已认识内容存入字库后，关联资产均被清理。

## 运维入口

迁移、函数依赖、事件函数与当前部署包以 [评测云函数说明](../../../cloud-functions/evaluate-reading/README.md) 为准。数据库人工核验见 [Supabase 运维说明](../../../supabase/README.md)。
