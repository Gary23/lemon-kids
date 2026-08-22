# `:shared`：跨端领域与基础设施

> 供 AI 修改所有 Android 业务前阅读。此模块是 `parent-app`、`kid-task-app`、`kid-monitor-app`、`kid-literacy-app` 的共同依赖。

## 职责与入口

- 领域模型：`src/main/java/com/lemonkids/shared/model/`。
- 业务接口：`repository/*Repository.kt`；应用层依赖接口，不直接拼 Supabase 请求。
- Supabase 实现：`repository/impl/Supabase*Repository.kt`。
- 依赖绑定：`di/SharedRepositoryModule.kt`；新增 Repository 时必须新增接口、实现和 Hilt 绑定。
- 认证/绑定码共享 UI：`ui/auth/AuthViewModel.kt`、`BindingCodeScreen.kt`。
- Supabase Client：`di/SupabaseModule.kt`。

## 不可破坏的边界

1. `model` 是 Android 三端的字段契约；改字段前同步检查 SQL 表、序列化字段、所有 Repository 与页面。
2. 任务完成与奖励兑换分别通过 `complete_task`、`redeem_reward` RPC 保持积分事务性；不要在 UI 层直接增减积分。
3. 绑定码只有 `task` 与 `monitor` 两种类型。任务码可复用；监控码涉及设备占用与强制重绑，具体协议在 `AuthRepository.exchangeBindingCode`。
4. `callbackFlow` 观察接口可能包含轮询/Realtime 细节；不要把其生命周期迁到 Composable 中。
5. Supabase 认证会话保存在各应用私有数据中；认字端还会保存已验证的 `task` 绑定码以静默恢复会话。刷新凭证失败或业务请求发现 token 缺失时，由单例 `auth/SessionRecoveryCoordinator` 通知认字端根层阻断业务并执行刷新或绑定码恢复；恢复成功后必须调用 `markRecovered()` 解除阻断。清除应用数据或主动退出才应要求重新绑定。

## 配置与安全

`SupabaseModule.kt` 与 `SupabaseAuthRepository.kt` 当前包含项目 URL 和 anon key。它们不是 service-role 密钥，但新代码不得提交任何管理员密钥、账号或个人环境配置。若改为注入式配置，必须同时覆盖所有 Android 应用的构建配置。

## 验证

```bash
./gradlew :shared:assembleDebug
./gradlew :parent-app:assembleDebug :kid-task-app:assembleDebug :kid-monitor-app:assembleDebug
```

## 跨端资料

- [产品与领域规则](docs/PRODUCT-SPEC.md)
- [架构记录](docs/ARCHITECTURE.md)
- [跨端 UI 参考](docs/UI-SPEC.md)
- [数据库与 RPC](../supabase/README.md)
