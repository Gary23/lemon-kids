# 认字口语评测云函数

部署目标：腾讯云 SCF **Web 函数** `evaluate-reading`。Web 函数通过
`scf_bootstrap` 启动 HTTP 服务，因此控制台没有“执行方法”配置是正常的。

## 环境变量

函数配置中必须包含以下变量（不要使用 `TENCENTCLOUD_*` 前缀，SCF 会拒绝）：

```text
SUPABASE_URL
SUPABASE_SERVICE_ROLE_KEY
LITERACY_STS_SECRET_ID
LITERACY_STS_SECRET_KEY
LITERACY_TENCENT_APP_ID
LITERACY_TENCENT_REGION
DEEPSEEK_API_KEY
```

`LITERACY_TENCENT_REGION` 应填写有效腾讯云地域，例如函数部署在北京时填写 `ap-beijing`。
`DEEPSEEK_API_KEY` 是 DeepSeek 平台创建的 API Key，只配置在 SCF 环境变量中，绝不能写入 APK 或提交到仓库。识字生成固定调用官方当前模型名 `deepseek-v4-flash`，不接受环境变量切换模型。识字接口显式关闭思考模式：该模式不会切换或关闭模型，而是避免短 JSON 请求将全部 `max_tokens: 1600` 消耗在 `reasoning_content`、没有最终 `content`。首次会批量生成；词语含有字库外汉字时，合规项会保留，问题字随后单独重试 9 次（含首次最多 10 次）。第 10 次仍无法生成纯字库词语时，会原样保留 DeepSeek 最后一版词语，但前提是该句子至多含 2 个字库外汉字。整次生成的 DeepSeek 时间预算为 65 秒，防止多字逐个重试时触发 SCF 的 90 秒硬超时。思维链绝不作为生成结果或写入日志。每次 DeepSeek 请求最多等待 20 秒；Supabase 鉴权和数据读取最多等待 15 秒。

可选变量 `LITERACY_AUDIO_GENERATOR_FUNCTION` 用于覆盖音频事件函数名称，默认
`generate-literacy-audio`。`LITERACY_STS_SECRET_ID` / `LITERACY_STS_SECRET_KEY` 对应的
CAM 用户（不是 `evaluate-reading` 的执行角色）还必须仅对该目标函数授予 `scf:InvokeFunction`。
提交智能识字内容后，评测服务会以
异步事件分别投递本次新建任务；接口只等待事件被腾讯云受理，不等待音频生成完成。投递异常
不会回滚已保存的识字任务，现有定时生成任务仍会兜底处理。

## 部署

1. 进入此目录后运行 `npm install --omit=dev`。
2. 运行 `chmod +x scf_bootstrap`，确保启动文件有可执行权限。
3. 将 `index.js`、`server.js`、`scf_bootstrap`、`package.json`、`package-lock.json`、`node_modules` 压缩为 ZIP（ZIP 根目录必须直接包含这些项）。
4. 在现有 Web 函数的“代码”页上传 ZIP，并保存发布到 `$LATEST`。
5. 访问函数 URL 时仍保持“开放”；函数内部会强制校验 `Authorization: Bearer <Supabase access token>`。

本次部署包为 `evaluate-reading-web-20260813-phonetic-reading-evaluation.zip`。部署前需先执行 `supabase/sql/20260804_recognized_characters.sql`、`supabase/sql/20260805_literacy_help_request_sources.sql`、`supabase/sql/20260806_literacy_help_request_clicked_character.sql`、`supabase/sql/20260807_literacy_tts_cleanup.sql` 与 `supabase/sql/20260813_literacy_example_pinyins.sql`，并为评测函数使用的 CAM 身份增加目标 `generate-literacy-audio` 函数的 `scf:InvokeFunction` 权限。对已经自动收录且主字音频为空的历史数据，额外执行一次 `supabase/sql/20260811_backfill_recognized_character_audio.sql`。

部署后的验收：在认字端完成一次“智能添加识字”并提交；`evaluate-reading` 日志不应出现
`AccessDenied` 或 `UnauthorizedOperation`，`generate-literacy-audio` 应随即收到异步调用，待其执行
完成后确认 Supabase 中该识字任务的字、词、句音频地址已回写，最后在认字页长按验证可播放。

## 当前接口契约

所有请求为 `POST` JSON，并必须带上述 Bearer Token。

Android 进入认字页时先调用一次 `issue_credentials` 领取 STS；凭证有效期为
30 分钟，客户端在剩余不足 5 分钟时刷新，因此同一认字页内最多约每 25 分钟
领取一次。字、词、句均复用该凭证，但每一次录音仍是独立的腾讯实时评测。

```json
{
  "action": "issue_credentials"
}
```

`issue_session` 保留给旧版客户端兼容使用；新版客户端不再按每个字、词、句分别
调用它。

认字端“我的 → 智能添加识字”先调用 `preview_literacy_tasks`：输入只允许汉字，去重后每次最多 12 个。函数会查询当前孩子的 `known_characters`（字库），但字库已有字仍会发送给 DeepSeek 生成字词句，并在预览中逐组标记；已有待认识任务的同字才会跳过。函数固定使用 DeepSeek `deepseek-v4-flash`，以字库加本次输入字为允许字集。每个目标字会提示 DeepSeek 生成 1～3 个词语，提供 1 个即可，不要求凑满 3 个；词语应由至少两个汉字组成，句子应为可朗读的短句，不能只返回目标字本身。每个词、句还会同步生成按汉字位置对应的无声调拼音，例如“组长”为 `["zu","zhang"]`；预览页允许人工修改词句及拼音。词数、词长与句长均为质量建议；但每个词语和句子必须包含对应目标字，这是客户端与服务端都会校验的硬性要求。词语会先按纯字库规则逐字重试，累计第 10 次仍越界时保留 DeepSeek 返回的完整词语；句子允许最多 2 个字库外汉字。绝不会删除原词句中的越界字。预览完成日志会记录每组的词数、单字词数和句子长度等质量统计，不记录完整词句。预览不写库；页面中的字不可编辑，词、句和拼音可编辑，每组均可删除。长按词、句中的任意字时，云函数仍会实时查询 `known_characters`：在字库内才写入帮助请求表，字库外字直接返回 `skipped`，不留记录。

家长确认后，客户端调用 `save_literacy_tasks` 提交原输入字和未删除的 `items`。服务端会重新读取最新字库用于标记和字词句校验；字库已有字仍可写入 `child_literacy_characters`，删除的整组不会提交或写入；已有同字任务会跳过，不覆盖。成功写入后，会立即以异步事件定向触发每条新任务的音频生成；客户端不会等待 MP3 合成完成，原定时扫描不受影响。

```json
{
  "action": "preview_literacy_tasks",
  "characters": "春夏秋冬"
}
```

```json
{
  "action": "save_literacy_tasks",
  "characters": "春夏秋冬",
  "items": [{"character":"春","words":[{"text":"春天","pinyins":["chun","tian"]}],"sentence":{"text":"春天来了我们一起看花","pinyins":["chun","tian","lai","le","wo","men","yi","qi","kan","hua"]}}]
}
```

```json
{
  "action": "issue_session",
  "literacyCharacterId": "字任务 UUID",
  "targetType": "character",
  "contentSource": "task"
}
```

`targetType` 还可以是 `word` 或 `sentence`；句子模式额外需要 `sentenceText`（它只能匹配所选数据源中已有句子）。服务端始终从 Supabase 的实际教学内容读取文字。

`contentSource` 默认为 `task`，从认字任务读取内容；设为 `recognized` 时，`literacyCharacterId` 为 `recognized_characters.id`，服务端从该独立已认识字记录读取字、词、句。两类来源都可正常领取评测会话；求助记录的归属由新迁移支持两种来源，是否写入则由被长按字的字库状态决定。

词组逐词评测时，客户端会额外携带 `wordText`，例如 `"白霜"`。该字段只能精确匹配当前识字任务数据库内已有的一项词语；云函数会只为这一个词生成 `REF_TEXT`。这样三个词会使用三个独立的腾讯会话，不会再把连续朗读音频交给同一次评测。

每次朗读的对错、星星及“已读/未读”只按天保存在孩子端本地，应用次日启动会清理。未学习任务中主字读对 3 次、每个词读对 2 次、每个句子读对 1 次；全部达标后客户端调用 `complete_literacy_character`，并首次写入 `child_literacy_characters.learned_at`。若本次学习中曾长按**主字**播放音频，云函数会复制主字及其音频元数据、字词句，幂等写入 `recognized_characters`；若未长按主字，则直接幂等写入 `known_characters`。词、句中的长按不影响该判断。已认识字复习仅展示字、词：列表按 `recognized_at` 从近到远取最近 24 个，前 8 个主字读 3 次、中间 8 个读 2 次、后 8 个读 1 次；每个词始终读 1 次，不调用此完成接口或更新数据表。

```json
{
  "action": "complete_literacy_character",
  "literacyCharacterId": "字任务 UUID",
  "hasCharacterAudioPointRead": false
}
```

`hasCharacterAudioPointRead` 仅表示主字是否被长按点读；传 `true` 时收录到已认识字表，传 `false` 时直接写入字库。旧版客户端未传此字段时，为兼容既有发布版本，云函数仍按 `true` 处理。该动作依赖 `supabase/sql/20260804_recognized_characters.sql` 已执行。

已认识字列表的“存库”调用会先将这条记录写入 `known_characters`（同字重复操作不会重复写入），再投递关联教学音频的异步删除任务。系统转入记录会同时覆盖原认字任务的字、词、句音频；手工/导入记录仅覆盖自身。认字端会立即从当前列表移除该卡片，而 `recognized_characters` 记录会在全部对象删除成功后才物理删除。Storage 暂时失败只会使任务保持待重试，绝不会撤销存库：

```json
{
  "action": "archive_recognized_character",
  "recognizedCharacterId": "已认识字 UUID"
}
```

长按词句中的某个汉字、请求系统读出正确发音时，客户端调用：

```json
{
  "action": "record_help_request",
  "literacyCharacterId": "字任务 UUID",
  "targetType": "sentence",
  "sentenceText": "已有句子全文",
  "character": "行",
  "characterIndex": 2
}
```

云函数会重新从数据库确认该字任务、朗读内容和字符位置，再写入
`child_literacy_character_help_requests`，但并非每次长按都会写入：无论从待认识还是已认识列表进入，也无论长按主字、词或句，只有该被长按的字已存在于 `known_characters`（字库）时才记录；字库外汉字只朗读，不留下记录。记录会同时保存完整词/句、被长按的汉字和它在内容中的位置；同一内容点到不同位置会分别保留，客户端可在“帮助过的内容”中精确高亮当时点击的字。部署前需执行 `supabase/sql/20260806_literacy_help_request_clicked_character.sql`；如需在客户端删除单条记录，还需执行 `supabase/sql/20260810_literacy_help_request_delete.sql`。

## 评测模式

主字评测继续以腾讯 `TEXT_MODE=0` 传递汉字原文。新保存的词、句会使用 `TEXT_MODE=1` 的发音描述块指定每个字的读音；数据库只保存无声调拼音，客户端为满足腾讯格式临时补调号，并关闭 `F_TDET`，所以声调（包括轻声）不参与对错判定。没有 `pinyins` 的历史词句继续使用 `TEXT_MODE=0`，不会改变既有任务的评测行为；评测结果不落库。
