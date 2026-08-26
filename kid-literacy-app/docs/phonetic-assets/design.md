# 音素资产设计与数据规则

## 腾讯评测协议

词、句使用腾讯 `TEXT_MODE=1` 与 `wordList` 锁定发音；主字仍由客户端按剩余朗读次数构造 `TEXT_MODE=0` 参考文本。服务端从已就绪资产组装词句参数，客户端不得拼接 `RefText`、统一补一声或传递 `F_TDET=false`。

数字拼音必须带 1 至 4 调，例如：

```json
{"wordList":[{"word":"组","pron":[["zu3"]]},{"word":"长","pron":[["zhang3"]]}]}
```

`zhang3` 是正确参考标注，不代表评分检测声调；未启用 `F_TDET` 时，声调不参与正误判断。腾讯不支持指定轻声：轻声汉字的 `pron` 必须省略，由腾讯内置词典处理。

## 数据模型

`literacy_phonetic_assets` 同时保存异步队列与已完成资产。唯一键为 `(content_source, literacy_character_id, item_type, item_index)`。

| 字段 | 说明 |
| --- | --- |
| `content_source` | `pending` 或 `recognized` |
| `literacy_character_id` | 待认识或已认识字记录 ID |
| `item_type` / `item_index` | `word`、`sentence` 及其在内容数组的固定索引 |
| `item_text` | 创建时的原文；服务端组装前须与主表比对 |
| `phoneme_tokens` | 与汉字一一对应的数字拼音；轻声或未指定字为 `null` |
| `status` | `pending`、`processing`、`ready`、`failed` |
| `attempt_count` / `last_error` | 重试和故障定位 |
| `generator_version` | 词典及生成规则版本 |

资产保存技术音素而非完整 `RefText`，因此腾讯协议结构升级时可重新组装，不必重写人工维护的数据。

## 生成和人工维护

保存待认识内容时，服务端在同一事务中写入正文与每个词、句的 `pending` 资产，随后立即生成；`generate-literacy-phonetics` 每 30 分钟领取剩余 `pending` 或可重试的 `failed` 资产作为兜底。

生成必须按完整词句使用词组拼音词典，例如“组长”生成 `zu3 zhang3`、“长城”生成 `chang2 cheng2`，不能按单字推断。生成器须校验汉字数和音素数对应、非轻声音素符合 `^[a-zv]+[1-4]$`。词典无结果、长度不符或文本不支持时标记为 `failed`，不得写入半成品；确定性失败不得无限重试。

人工编辑只更新目标资产的 `phoneme_tokens` 并置为 `ready`。保存前校验顺序、数量与格式；有效 `ready` 资产不会被批处理覆盖，无须额外区分 `generated` 与 `manual` 来源。

## 生命周期

| 事件 | 资产处理 |
| --- | --- |
| 保存待认识词句 | 创建 `pending` 资产并触发生成 |
| 生成成功或人工保存 | 写入有效音素并置为 `ready` |
| 待认识转已认识 | 同一事务改为 `recognized`，保留资产 |
| 已认识字复习 | 继续使用保留资产进行指定读音评测 |
| 待认识或已认识内容存入字库 | 删除该字及其词句资产 |
| 删除待认识或已认识内容 | 删除关联资产 |
