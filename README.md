# LemonKids：AI 开发索引

> 本文件是进入仓库后的唯一顶层文档入口。先定位改动所属模块，再阅读该模块的 README；**运行中的代码优先于文档中的规划**。

## 项目地图

| 目录 | Gradle 模块 / 用途 | AI 开发入口 |
| --- | --- | --- |
| `parent-app/` | `:parent-app`，家长端 | [模块说明](parent-app/README.md) |
| `kid-task-app/` | `:kid-task-app`，孩子任务端 | [模块说明](kid-task-app/README.md) |
| `kid-monitor-app/` | `:kid-monitor-app`，孩子设备监控端 | [模块说明](kid-monitor-app/README.md) |
| `kid-literacy-app/` | `:kid-literacy-app`，独立识字原型 | [模块说明](kid-literacy-app/README.md) |
| `shared/` | `:shared`，领域模型、仓库、DI 与共享认证 UI | [模块说明](shared/README.md) |
| `supabase/` | 数据库与 RPC 运维资料 | [说明](supabase/README.md) |
| `ui/app/` | Next.js 视觉原型，不参与 Android 构建 | [说明](ui/app/README.md) |

## AI 工作规则

1. 修改跨端数据、认证、Repository 或 RPC 前，先读 `shared/README.md` 和 `supabase/README.md`；不要在应用模块复制模型或网络逻辑。
2. 文档中的“已实现”必须能在源码中找到；“规划/待接入”不是实现依据。页面、路由、权限以对应模块源码为准。
3. 变更数据库表、RLS、RPC 或绑定码协议时，必须同时评估三个 Android 应用和 `shared` 的调用链。
4. Android 构建从根目录运行：`./gradlew :<module>:assembleDebug`。改动后至少构建受影响模块。

## 跨模块规范

- [产品与领域规则](shared/docs/PRODUCT-SPEC.md)：任务、奖励、监控等跨端业务规则。
- [架构记录](shared/docs/ARCHITECTURE.md)：技术选型和历史设计；须与代码核对。
- [跨端 UI 参考](shared/docs/UI-SPEC.md)：视觉与交互参考；不替代实际 Compose 页面。

## 开发分支

- [开发分支台账](BRANCHES.md)：当前各开发分支的功能范围、状态与建议合并顺序。
