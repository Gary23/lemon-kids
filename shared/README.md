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
4. Repository 的 `Flow` 观察接口可能包含轮询/Realtime 细节；不要把其生命周期迁到 Composable 中。
5. Supabase 认证会话保存在各应用私有数据中；认字端和任务端还会分别保存已验证的 `task` 绑定码以静默恢复会话。刷新凭证失败或业务请求发现 token 缺失时，由单例 `auth/SessionRecoveryCoordinator` 通知对应应用根层阻断业务并执行刷新或绑定码恢复；恢复成功后必须调用 `markRecovered()` 解除阻断。清除应用数据或主动退出才应要求重新绑定。
6. 更新任务时，`SupabaseTaskRepository` 必须使用显式 `JsonObject` 载荷；不要以含 `List<Int>` 的 `Map<String, Any?>` 提交，Kotlinx Serialization 无法序列化 `Any`。重复系列同步未来任务时不得覆盖各实例自己的 `due_date`。
7. 奖励目录由家长维护，`RewardRepository.getAllRewards` 包含停用项，孩子端观察接口只返回启用项。`is_active` 表示家长启停；一次性奖励是否仍可兑换须由兑换记录和服务端事务判断。孩子端通过奖励快照读取真实余额、月收益与本人兑换记录，并通过 `redeem_reward`、`use_reward_redemption`、`cancel_reward_redemption` RPC 执行兑换、使用和退款。家长端目录迁移见 `../supabase/sql/20260930_parent_reward_catalog.sql`；孩子端事务迁移见 `../supabase/sql/20260930_kid_real_rewards.sql`。
8. `Reward.imagePath` 是可空的家庭私有对象路径，不是公开 URL。`RewardRepository` 创建/更新奖励时写入该路径，图片上传、删除和十分钟签名读取由仓库处理；对象路径须属于奖励家庭。家长端先上传新对象再保存奖励，失败时清理新对象；图片替换或移除成功后清理旧对象。旧奖励可保持空路径；`cover_key` 仍作为旧客户端兼容字段，新版家长端不提供选择，新建写入 `gift`，编辑保留原值。依赖 `../supabase/sql/20261004_reward_images.sql` 的 `rewards.image_path` 与私有 `reward-images` bucket；权限由数据库及 Storage RLS 最终执行。
9. 孩子端成长页经 `GrowthRepository.getOwnSnapshot()` 调用本人只读的 `growth_snapshot()`，模型在 `model/GrowthSnapshot.kt`，实现为 `repository/impl/SupabaseGrowthRepository.kt`。快照汇总任务事实、有效到账星星、奖励使用及家长手动勋章进度；任务领域不自动推进手动专项。数据库脚本在 `../supabase/sql/20261005_kid_growth_snapshot.sql`，家长端进度表及受控写入须先部署。孩子端不能写专项或读取其他孩子数据；读取失败不得伪装为零进度。
10. `TaskRepository.updateTaskDescription(taskId, description)` 只修改指定 `tasks.id` 的每日任务描述，经 `SupabaseTaskRepository` 调用 `update_daily_task_description` RPC。通用任务属性更新与重复系列同步不得写入 `description`，避免覆盖其他日期实例的内容；成功后须刷新任务观察流，日历选中日缓存由家长端刷新。依赖 `../supabase/sql/20261008_parent_daily_task_description.sql`，服务端校验家长身份、家庭、待完成状态和上海时区日期。

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
