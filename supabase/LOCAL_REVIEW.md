# 家长端本地数据库部署核对

## 目标与前置条件

目标为独立 `lemon-kids-local` 服务，数据库容器固定为 `supabase_db_lemon-kids-local`，API 端口为 `54421`。通用本地项目和生产服务不在迁移范围。服务项目位于 `/Volumes/pan/lemon-kids-local-supabase`，运行环境位于 `/Volumes/LemonKidsContainer`。

部署脚本使用 `/Volumes/LemonKidsContainer/bin/docker`，设置 `DOCKER_HOST=unix:///Volumes/LemonKidsContainer/colima/default/docker.sock` 和 `DOCKER_CONFIG=/Volumes/LemonKidsContainer/docker-config`。需要有权访问该 socket。

原库必须已有 `families`、`users`、`binding_codes`、基础 `tasks` 和本地家长注册入口。`20261022_local_core_baseline.sql` 与 `20261022_local_parent_onboarding.sql` 仅覆盖认证/家庭基础能力，不足以初始化完整业务库。业务升级不重置数据库、不复制线上数据、不插默认业务记录。

## 升级顺序与数据保护

1. `tests/apply_local_parent_business.py` 默认只读，检查原表、来源引用、重复活跃模板任务、pg_cron 及角色权限。
2. `tests/verify_local_parent_business.py` 在独立临时 PostgreSQL、Auth 和 PostgREST 中复制验证现有库，验证重复迁移、原数据保留及权限；临时 API 使用 loopback 54524/54521。
3. 自动测试通过并取得部署确认后，使用 `tests/apply_local_parent_business.py --apply`。入口在同一事务按管理员扩展前置、普通 postgres 业务基线、管理员 Storage 策略顺序执行三份 `20261029_local_parent_business_*.sql`。
4. 核对 `/tmp/lemon-parent-business-deployment.json`，再使用现有 `.local` 家长端包人工验收。

事务内锁定旧四表并比较全部原字段，任何改写都会回滚；提交后再次检查数量与哈希。回执记录 SQL SHA256，不含密钥。成功应用后若需修正，使用兼容增量迁移，避免通过删表回退损失新增业务数据。

不要批量执行历史 SQL。`20260906_category_task_bundles.sql`、`20260901_reset_tasks_and_child_points.sql` 含清空数据操作；旧 `init.sql` 的用户自写及 Storage 策略也不适合作为当前权限基线。本地业务基线仅整合经过权限核对的定义，不运行历史清表语句。

## 当前支持与权限

- 首页任务与来源创建、分类/任务库及原子关联、每日描述、成长修正、取消/恢复和受限清理；原有任务和账号保持不变。
- 用时会话、明细与汇总，积分/奖励兑换事务、勋章进度及审计，设备日志、应用统计和限制。没有上报或业务记录时显示真实空列表。
- public 业务表启用 RLS，家庭成员读取范围与家长写入权限分别校验，匿名不可访问新增管理表；跨家庭关联和越权写入被拒绝。
- 私有 `reward-images` bucket 使用家庭路径隔离；仅家庭家长可新增，禁止覆盖及删除被奖励引用的图片。
- `task-duration-prune-hourly` 保持单条启用的用时清理调度。

家长端首页及“我的”核心菜单已通过本地手机人工验收。共享结构的兼容性测试不替代孩子端等其他应用的人工验收。外部腾讯云 SCF、认字音频、远程闹钟、视频及 123 云盘服务没有因本迁移自动接通。

## 验证边界

隔离环境覆盖真实 Auth 密码登录/JWT、PostgREST/RPC、两家庭隔离、家长/孩子/匿名权限、分类与来源任务、计时与积分、勋章冲突、兑换幂等和退款、设备统计、私有存储策略，以及数据保护失败回滚。Storage 通过数据库事务模拟 API 上下文验证元数据策略，没有上传真实图片文件。

应用后的只读核验覆盖原四表字段与数量保留、18 张 public 表的 RLS、私有 bucket、清理调度及实际 HTTP 接口结构解析。手机验收通过不扩大为所有外部功能和其他应用已验收。过程及部署结果见 [本地业务流程](../docs/20261029-supabase-parent-local-business/执行.md)。
