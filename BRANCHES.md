# 分支说明

`main` 与 `product` 是长期保留分支。功能分支分为“当前开发分支”和“历史分支”：历史分支仅保留提交记录，不再承接后续开发。

| 分支 | 用途 | 当前状态 |
| --- | --- | --- |
| `main` | 稳定主线与基础版本。 | 已推送。 |
| `product` | 产品集成与发布分支。 | 已合并认字端 v2.0（跨 Pad 朗读进度与当天任务快照）、任务端桌面卡片与任务首页、家长端任务分类包管理及创建修复、任务首页分类稳定与日历紧凑分组优化，以及认字音频任务 Supabase 容错；发布标签见下表。 |

## 当前开发分支

| 分支 | 用途 | 当前状态 |
| --- | --- | --- |
| `feature/family-video-app` | 家庭动画应用及 123 云盘安全同步。 | 开发中。 |
| `feature/lemon-alarm-monitor` | 监控端 App：家长端远程建立、同步、可靠触发与闭环追踪 Pad 端闹钟。 | 开发中。 |

## 历史分支

以下分支以后不再用于开发；保留用于追溯历史提交。

| 分支 | 历史用途 / 状态 |
| --- | --- |
| `feature/kid-literacy-app` | 认字音频任务 Supabase 容错，已合并至 `product`。 |
| `feature/kid-task-category-stability` | 任务首页分类首屏稳定、分类配色调整及日历紧凑分组优化，已合并至 `product`。 |
| `feature/kid-task-desktop-widget` | 任务端桌面卡片、绑定码会话与任务分类管理，已合并至 `product`。 |
| `feature/known-character-review-stars` | 已认识字复习满星布局与相关文档整理。 |
| `feature/literacy-character-three-reads` | 认字任务日程提醒与自动通过相关开发。 |
| `feature/literacy-phonetic-reading-evaluation` | 词句逐字拼音评测。 |
| `feature/reading-progress-cloud-sync` | 跨 Pad 共享当天认字任务快照，已合并至 `product`。 |
| `fix/known-character-save-removal` | 已认识字保存与移除优化。 |

## `product` 发布标签

| 标签 | 指向提交 | 说明 |
| --- | --- | --- |
| `认字端appv1.0` | `e72154a` | 合并已提交的认字评测改造后的产品基线。 |
| `认字端appv1.1` | `f4886a5` | 合并认字端文档整理与复习优化后的产品版本。 |
| `r认字端v1.2` | `7ee7cfb` | 合并认字端首页字形、列表布局与缓存优化后的产品版本。 |
| `任务端+家长端v1.0` | `b5367f1` | 合并任务端任务首页、桌面小部件与家长端任务管理改动后的产品版本。 |
| `认字端v1.1` | `e2f1aa3` | 合并分类任务包、任务创建及其 RPC 参数序列化修复后的产品版本。 |
| `认字端v2.0` | 本次 `product` 更新提交 | 合并跨 Pad 朗读进度同步与当天待认识任务共享快照后的认字端版本。 |
