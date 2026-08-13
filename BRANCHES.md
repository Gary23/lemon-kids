# 开发分支台账

本文件记录当前仍需关注的开发分支，避免将并行功能混淆。功能完成并合入 `main` 后，将对应条目移至提交历史即可。

| 分支 | 开发内容 | 当前状态 | 合并注意事项 |
| --- | --- | --- | --- |
| `main` | 稳定主线；当前线上基线。 | 已推送 | 只合并完成验证的功能分支。 |
| `feature/literacy-character-three-reads` | 仅认字页：主字连续朗读三次填满三颗星；点击“我读完了”后评测，不再要求反复点击开始/结束；词和句不改。 | 开发中，改动尚未提交 | 与拼音评测分支都改动认字端主页面，合并前需人工解决同文件冲突。 |
| `feature/literacy-phonetic-reading-evaluation` | 词、句逐字无声调拼音：DeepSeek 生成、人工可编辑、入库；腾讯评测指定读音且不检测声调，历史无拼音数据兼容原模式。 | 已提交并推送，提交 `a91ba03` | 合并前先在 Supabase 执行 `supabase/sql/20260813_literacy_example_pinyins.sql`，并部署对应评测函数 ZIP。 |

## 推荐合并顺序

1. 完成并验证 `feature/literacy-character-three-reads`，提交后先合入 `main`。
2. 再合并 `feature/literacy-phonetic-reading-evaluation`，处理 `LemonLiteracyApp.kt` 的冲突并重新构建认字端。
3. 部署前按各分支 README 与 SQL 文件执行数据库迁移、云函数部署和真机验收。
