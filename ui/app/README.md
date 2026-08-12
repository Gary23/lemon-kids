# `ui/app`：柠檬任务视觉原型

> 这是独立的 Next.js 原型，不参与根 Gradle 工程，也不等同于 Android 生产实现。

## 用途与入口

- 页面入口：`app/page.tsx`；通过本地 state 切换任务、日历、奖励、我的。
- 组件：`components/lemon/`；样式与主题：`app/globals.css`。
- 包管理器：pnpm（锁文件为 `pnpm-lock.yaml`）。

## AI 约束

原型可用于确认视觉与交互意图，但 Android 页面、路由和数据状态必须以 `kid-task-app` 源码为准。不要因为原型存在而在 Android 中引入 React、Web API 或其数据结构。

## 验证

```bash
pnpm install
pnpm lint
pnpm build
```

