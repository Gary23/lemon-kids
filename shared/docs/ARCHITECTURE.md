# 柠檬小管家 — 跨端架构记录

> 适用对象：AI 开发。本文保留技术选型和演进背景；模块清单、依赖、路由、SQL 和实现状态以源码及各模块 README 为准。原始版本：技术设计 v1.4。

> 阅读顺序：先读根目录 `README.md`，再读目标模块 README；只有涉及共享模型或数据库时再查阅本文。文中“本次会话”“已完成”等叙述均为历史记录，不能作为当前状态依据。

---

## 一、技术选型

### 1.1 整体技术栈

| 层 | 选择 | 版本 | 理由 |
|----|------|------|------|
| 语言 | Kotlin | 2.0+ | Android 官方首选 |
| UI | Jetpack Compose | BOM 2025.x | 声明式 UI，多个 Android 模块复用领域与认证能力 |
| 架构 | MVVM + Repository | - | Google 推荐，适合 Compose |
| DI | Hilt | 2.52+ | 与 Compose Navigation 配合好 |
| 导航 | Compose Navigation | 2.8+ | 类型安全的导航 |
| 本地存储 | Room | 2.6+ | 离线缓存任务 |
| 网络/BaaS | Supabase | 2.6+ | PostgreSQL + Storage + Auth + Realtime |
| 序列化 | kotlinx.serialization | 1.7+ | Supabase 数据模型 JSON 映射 |
| 后台任务 | WorkManager | 2.10+ | 定时采集 App 使用数据 |
| 音频播放 | Media3 ExoPlayer | 1.4+ | TTS 语音朗读 |
| 图表 | Vico | 2.1+ | Compose 原生图表 |
| 浮窗 | WindowManager | SDK | SYSTEM_ALERT_WINDOW |
| 使用统计 | UsageStatsManager | SDK | API 21+ |
| 图片加载 | Coil | 2.7+ | Compose 集成 |
| 动画特效 | Lottie Compose | 6.6+ | 任务完成/积分庆祝动画 |
| 庆祝特效 | Konfetti Compose | 2.1+ | 撒花动效 |

### 1.2 Supabase 依赖

因 Firebase 国内不可用，使用 Supabase 云服务（supabase.com，国内可访问）替代。

```
supabase-kt-bom              # 版本统一管理
supabase-postgrest-kt        # PostgreSQL 查询
supabase-gotrue-kt           # 登录认证（邮箱/手机）
supabase-storage-kt          # 文件存储
supabase-realtime-kt         # WebSocket 实时数据同步
supabase-compose-auth        # Compose 登录 UI
kotlinx-serialization-json   # 数据模型序列化
```

**为什么选 Supabase：**
- 免费版 500MB 数据库 + 1GB 存储，家庭使用完全够
- API 域名在国内可访问（不像 Firebase 被墙）
- PostgreSQL 数据库，支持事务（任务完成 → 积分到账原子操作）
- 自带 Auth + Realtime + Storage，不需要额外集成

### 1.3 最低 SDK 与构建
```
minSdk = 26 (Android 8.0)
targetSdk = 35
compileSdk = 35
Gradle 8.x + Version Catalog (libs.versions.toml)
```

### 1.4 设备适配

| App | 手机 | 平板 | 说明 |
|-----|------|------|------|
| **kid-app** | ✓ | ✓ | 孩子可能用手机或平板，需适配两种设备 |
| **parent-app** | ✓ | ✗ | 家长只用手机操作 |

**适配策略 — Compose 自适应布局：**

使用 `WindowSizeClass` 检测设备类型，根据屏幕宽度自动切换布局：

```kotlin
// Material3 Adaptive 库
implementation("androidx.compose.material3.adaptive:adaptive:1.0.0")
implementation("androidx.compose.material3.adaptive:adaptive-layout:1.0.0")
implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.0.0")
```

| 宽度断点 | WindowSizeClass | 对应设备 | 孩子端布局策略 |
|----------|----------------|----------|---------------|
| < 600dp | Compact | 手机竖屏 | 单列布局，底部导航 |
| 600~840dp | Medium | 小平板/折叠屏 | 双列任务网格，底部导航 |
| > 840dp | Expanded | 大平板横屏 | 列表-详情双窗格，左侧导航栏 |

**孩子端各页面适配行为：**

| 页面 | Compact（手机） | Medium（小平板） | Expanded（大平板） |
|------|:---------:|:---------:|:---------:|
| 任务首页 | 单列卡片列表 | 双列卡片网格 | 左侧任务列表 + 右侧任务详情/确认 |
| 奖励商城 | 双列网格（紧凑） | 三列网格 | 四列网格 |
| 浮窗 | 80x80dp 圆形 | 120x120dp 圆形 | 120x120dp 圆形 |

**家长端：只需要处理 Compact 宽度即可**，不引入自适应逻辑。

**关键实现方式：**

```kotlin
@Composable
fun KidTaskScreen(windowSizeClass: WindowSizeClass) {
    when {
        // 大平板：列表-详情双窗格
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.EXPANDED -> {
            ListDetailPaneScaffold(...)
        }
        // 手机/小平板：单列
        else -> {
            // 根据宽度决定是 1 列还是 2 列网格
            val columns = if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.MEDIUM) 2 else 1
            LazyVerticalGrid(columns = GridCells.Fixed(columns), ...)
        }
    }
}
```

---

## 二、项目结构

```
/lemon-kids
├── gradle/
│   └── libs.versions.toml        # 统一版本管理
├── build.gradle.kts              # 根 build
├── settings.gradle.kts           # 包含 5 个 Gradle 模块
├── shared/                        # :shared 公共模块
│   ├── build.gradle.kts
│   └── src/main/java/com/lemonkids/shared/
│       ├── model/                 # 数据模型
│       │   ├── Task.kt
│       │   ├── PointRecord.kt
│       │   ├── Reward.kt
│       │   ├── AppUsageRecord.kt
│       │   ├── AppLimit.kt
│       │   ├── User.kt
│       │   ├── Family.kt
│       │   └── DeviceStatusLog.kt
│       ├── repository/            # 数据仓库接口
│       │   ├── TaskRepository.kt
│       │   ├── RewardRepository.kt
│       │   ├── AppUsageRepository.kt
│       │   ├── AuthRepository.kt
│       │   ├── FamilyRepository.kt
│       │   └── impl/              # Supabase 实现
│       │       ├── SupabaseTaskRepository.kt
│       │       ├── SupabaseRewardRepository.kt
│       │       ├── SupabaseAppUsageRepository.kt
│       │       ├── SupabaseAuthRepository.kt
│       │       └── SupabaseFamilyRepository.kt
│       ├── di/                    # Hilt 共享模块
│       │   ├── SupabaseModule.kt
│       │   ├── SharedRepositoryModule.kt
│       │   └── SharedModule.kt
│       ├── ui/auth/               # 共享 Auth + 绑定码 UI
│       │   ├── AuthViewModel.kt
│       │   └── BindingCodeScreen.kt
│       └── util/                  # 工具类
│           └── Constants.kt
├── kid-task-app/                  # :kid-task-app 任务端
│   ├── build.gradle.kts
│   └── src/main/java/com/lemonkids/kidtask/
│       ├── KidTaskApp.kt
│       ├── MainActivity.kt
│       ├── navigation/
│       │   └── KidTaskNavGraph.kt   # 绑定码登录 + 4 Tab
│       ├── feature/
│       │   ├── home/              # 今日任务面板
│       │   │   ├── HomeScreen.kt
│       │   │   └── HomeViewModel.kt
│       │   ├── calendar/          # 日历
│       │   │   ├── CalendarScreen.kt
│       │   │   └── CalendarViewModel.kt
│       │   ├── plan/              # 未来任务计划
│       │   │   └── PlanScreen.kt
│       │   ├── reward/            # 奖励商城
│       │   │   ├── RewardScreen.kt
│       │   │   └── RewardViewModel.kt
│       │   └── profile/           # 个人信息 + 切换账号
│       │       ├── ProfileScreen.kt
│       │       └── ProfileViewModel.kt
│       ├── di/                    # KidTaskModule + TtsEntryPoint
│       └── util/                  # KidTtsManager（TTS 语音朗读）
├── kid-monitor-app/               # :kid-monitor-app 监控端
│   ├── build.gradle.kts
│   └── src/main/java/com/lemonkids/kidmonitor/
│       ├── KidMonitorApp.kt
│       ├── MainActivity.kt
│       ├── navigation/
│       │   └── KidMonitorNavGraph.kt
│       ├── feature/
│       │   ├── usage/             # 使用详情页
│       │   │   ├── UsageDetailScreen.kt
│       │   │   ├── AppUsageDetailScreen.kt
│       │   │   └── AppHourlyDetailScreen.kt
│       │   └── profile/           # 个人信息 + 切换账号
│       │       ├── ProfileScreen.kt
│       │       └── ProfileViewModel.kt
│       └── monitor/               # 应用监控服务
│           ├── UsageCollectWorker.kt
│           ├── LimitEnforcementService.kt
│           ├── AppLimitAccessibilityService.kt
│           ├── AppLimitEvaluator.kt
│           ├── KeepAliveWorker.kt
│           ├── BootReceiver.kt
│           └── ...
├── parent-app/                    # :parent-app 家长端
│   ├── build.gradle.kts
│   └── src/main/java/com/lemonkids/parent/
│       ├── LemonParentsApp.kt
│       ├── MainActivity.kt
│       ├── navigation/
│       │   └── ParentNavGraph.kt
│       ├── feature/
│       │   ├── auth/              # 家长登录/注册/创建家庭
│       │   │   ├── ParentLoginScreen.kt
│       │   │   ├── ParentRegisterScreen.kt
│       │   │   └── ParentCreateFamilyScreen.kt
│       │   ├── tasks/             # 任务管理+日历
│       │   │   ├── TasksScreen.kt
│       │   │   ├── TaskEditScreen.kt
│       │   │   └── TasksViewModel.kt
│       │   ├── monitor/           # 使用监管
│       │   │   ├── MonitorScreen.kt
│       │   │   └── MonitorViewModel.kt
│       │   └── profile/           # 我的 + 家庭管理 + 绑定码生成
│       │       ├── ProfileScreen.kt
│       │       └── FamilyManageScreen.kt
│       └── di/
│           └── ParentModule.kt
├── kid-literacy-app/              # :kid-literacy-app 独立识字 UI 原型
├── supabase/                      # 当前 SQL 与运维说明
├── ui/app/                        # Next.js 视觉原型（非 Gradle 模块）
└── README.md                      # AI 开发索引
```

---

## 三、Supabase 配置

### 3.1 PostgreSQL 数据表

```sql
-- 家庭表
CREATE TABLE families (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    invite_code TEXT UNIQUE NOT NULL DEFAULT substring(md5(random()::text), 1, 6),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 用户表（Supabase Auth 的扩展表）
CREATE TABLE users (
    uid UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('child', 'parent')),
    family_id UUID REFERENCES families(id),
    avatar_url TEXT,
    total_points INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 任务表
CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    title TEXT NOT NULL,
    description TEXT DEFAULT '',
    child_id UUID NOT NULL REFERENCES users(uid),
    created_by UUID NOT NULL REFERENCES users(uid),
    status TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'done', 'verified', 'expired', 'rejected')),
    category TEXT NOT NULL DEFAULT 'other'
        CHECK (category IN ('study', 'chore', 'reading', 'exercise', 'other')),
    due_date DATE NOT NULL,
    due_time TEXT,
    reward_points INT DEFAULT 5,
    penalty_points INT DEFAULT 2,
    require_photo BOOLEAN DEFAULT false,
    completed_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_tasks_child_date ON tasks(child_id, due_date);
CREATE INDEX idx_tasks_family ON tasks(family_id);

-- 奖励商品表（必须在 point_records 之前创建，因为有外键引用）
CREATE TABLE rewards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    title TEXT NOT NULL,
    cost INT NOT NULL,
    repeatable BOOLEAN DEFAULT true,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 积分流水表
CREATE TABLE point_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    child_id UUID NOT NULL REFERENCES users(uid),
    amount INT NOT NULL,
    balance INT NOT NULL,
    reason TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('task_complete', 'task_expired', 'reward_redeem', 'manual')),
    related_task_id UUID REFERENCES tasks(id),
    related_reward_id UUID REFERENCES rewards(id),
    timestamp TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_pointrecords_child ON point_records(child_id);

-- 应用使用记录表
CREATE TABLE app_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    child_id UUID NOT NULL REFERENCES users(uid),
    package_name TEXT NOT NULL,
    app_name TEXT NOT NULL,
    duration_seconds BIGINT DEFAULT 0,
    date DATE NOT NULL,
    collected_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_appusage_child_date ON app_usage(child_id, date);

-- 应用限时表
CREATE TABLE app_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    child_id UUID NOT NULL REFERENCES users(uid),
    package_name TEXT NOT NULL,
    app_name TEXT NOT NULL,
    daily_limit_minutes INT DEFAULT 999,
    single_session_minutes INT DEFAULT 0,
    cooldown_minutes INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- 启用 Supabase Realtime 监听 (需要在 Dashboard 中开启 replication)
-- ALTER PUBLICATION supabase_realtime ADD TABLE tasks, rewards, app_limits;
```

### 3.1.2 后续迁移 SQL（v1.x 增量）

```sql
-- 任务结束日期
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS end_date TEXT;

-- 软删除（回收站）
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- 动态分类表
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id),
    name TEXT NOT NULL,
    color TEXT DEFAULT '#4CAF50',
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(family_id, name)
);
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
CREATE POLICY "family_access" ON categories FOR ALL
    USING (family_id IN (SELECT family_id FROM users WHERE uid = auth.uid()));

-- 创建请求不得提交 id、created_at 等数据库生成列；乐观更新使用的临时 ID 仅限本地 UI。

-- 删除旧的 category 枚举约束，允许自定义分类名
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS tasks_category_check;

-- v1.5: app_limits 新增单次时长和冷却间隔
ALTER TABLE app_limits ADD COLUMN IF NOT EXISTS single_session_minutes INT DEFAULT 0;
ALTER TABLE app_limits ADD COLUMN IF NOT EXISTS cooldown_minutes INT DEFAULT 0;
ALTER TABLE app_limits ALTER COLUMN daily_limit_minutes SET DEFAULT 999;

-- v1.6: binding_codes 绑定码表（Supabase 直接创建）
-- 字段: id, code, family_id, child_uid, type, status, device_id, expires_at, created_at
-- generate_binding_code / exchange_binding_code / get_child_binding_codes 三个 RPC 函数
```

### 3.2 Supabase Storage 路径

```
Bucket: avatars  → avatars/{uid}.jpg
```

### 3.3 行级安全策略 (RLS)

Supabase 使用 PostgreSQL RLS 替代 Firebase 安全规则：

```sql
-- 启用所有表的 RLS
ALTER TABLE families ENABLE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_limits ENABLE ROW LEVEL SECURITY;

-- 通用规则: 只能访问自己家庭的数据
CREATE POLICY "users_self_access" ON users
    FOR ALL USING (uid = auth.uid());

CREATE POLICY "families_access" ON families
    FOR ALL USING (id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

CREATE POLICY "family_access" ON tasks
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

CREATE POLICY "family_access" ON point_records
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

-- 奖励：所有人可查看，只有家长可创建/修改
CREATE POLICY "rewards_select_family" ON rewards
    FOR SELECT USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

CREATE POLICY "rewards_insert_parent" ON rewards
    FOR INSERT WITH CHECK (
        (SELECT role FROM users WHERE uid = auth.uid()) = 'parent'
    );

CREATE POLICY "rewards_update_parent" ON rewards
    FOR UPDATE USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid() AND role = 'parent'
    ));

CREATE POLICY "family_access" ON app_usage
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

CREATE POLICY "family_access" ON app_limits
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));
```

### 3.4 Supabase Realtime 配置

在 Supabase Dashboard > Database > Publications 中，找到 `supabase_realtime` publication，勾选以下表：
- `tasks` — 任务变更实时同步
- `rewards` — 奖励变动同步
- `app_limits` — 限时规则同步

客户端通过 Postgrest 的 `stream()` 或 Realtime 频道订阅变更：

```kotlin
// 监听 tasks 表的实时变更
supabase.realtime.channel("tasks")
    .postgresChange(PostgresChangeListener<Task>("tasks"))
    .subscribe()
```

---

## 四、关键实现方案

### 4.0 登录会话恢复

`AuthRepository` 提供本地会话判断和恢复能力：`hasAuthSession()` 用于判断设备上是否仍保存 Supabase 会话，`restoreSession()` 用于启动时等待 Auth 初始化、刷新会话并加载用户资料。`AuthViewModel` 在孩子端启动加载页执行恢复：恢复成功后进入主界面；本地仍有会话但网络或初始化暂时失败时保持加载并持续重试，避免孩子端因短暂网络问题频繁回到登录页；只有本地无有效会话时才进入登录流程。

### 4.1 实时同步机制（Supabase Realtime）

所有数据同步利用 Supabase Realtime（WebSocket），不自己写轮询：

```kotlin
// TaskRepository.kt
fun observeTodayTasks(childId: String, date: String): Flow<List<Task>> = callbackFlow {
    val channel = supabase.realtime.channel("tasks-$childId")
    
    channel.postgresChange<List<Task>>(
        schema = "public",
        table = "tasks",
        filter = "child_id=eq.$childId&due_date=eq.$date"
    ).collect { change ->
        // change.record 包含最新的任务数据
        val freshTasks = supabase.postgrest.from("tasks")
            .select { filter { eq("child_id", childId); eq("due_date", date) } }
            .decodeList<Task>()
        freshTasks.let { trySend(it) }
    }
    
    supabase.realtime.connect()
    channel.subscribe()
    
    awaitClose {
        channel.unsubscribe()
        supabase.realtime.disconnect()
    }
}
```

家长端监听同一个查询即可实现"任务状态秒级同步"。

### 4.2 任务语音朗读 (TTS)

使用 Android 内置 TextToSpeech API，无需额外网络或 Storage：

```
[孩子点击喇叭按钮]
    → TextToSpeech.speak(title + "，，" + description)
    → 优先讯飞引擎女声（小燕/小蓉等）→ 备选系统默认中文
    → pitch 1.18 / speechRate 0.85 模拟童声
    → 朗读完成 → UI 喇叭图标恢复常态
```

实现类：`KidTtsManager.kt`（Hilt @Singleton），通过 `KidTtsEntryPoint` 在 Composable 中获取。

### 4.3 任务完成 → 积分到账（数据库事务）

使用 PostgreSQL Row Level Security + 数据库函数确保一致性。

**方法 1——PostgreSQL 函数（推荐，Supabase Dashboard 中执行）：**

```sql
CREATE OR REPLACE FUNCTION complete_task(p_task_id UUID, p_child_id UUID)
RETURNS void AS $$
DECLARE
    v_task tasks%ROWTYPE;
    v_user users%ROWTYPE;
    v_new_balance INT;
BEGIN
    SELECT * INTO v_task FROM tasks WHERE id = p_task_id FOR UPDATE;
    IF v_task.status != 'pending' THEN
        RAISE EXCEPTION 'Task status is not pending';
    END IF;

    SELECT * INTO v_user FROM users WHERE uid = p_child_id FOR UPDATE;
    v_new_balance := v_user.total_points + v_task.reward_points;

    UPDATE tasks SET status = 'done', completed_at = now()
        WHERE id = p_task_id;
    UPDATE users SET total_points = v_new_balance
        WHERE uid = p_child_id;
    INSERT INTO point_records (family_id, child_id, amount, balance, reason, type, related_task_id)
        VALUES (v_task.family_id, p_child_id, v_task.reward_points,
                v_new_balance, '完成任务：' || v_task.title,
                'task_complete', p_task_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
```

**方法 2——Kotlin 侧调用 RPC（备选）：**

```kotlin
// TaskRepositoryImpl.kt
suspend fun completeTask(taskId: String, childId: String): Result<Unit> = runCatching {
    supabase.postgrest.rpc(
        function = "complete_task",
        parameters = mapOf(
            "p_task_id" to taskId,
            "p_child_id" to childId
        )
    )
}
```

### 4.4 应用使用数据采集 + 限时

**采集端 (WorkManager + UsageStatsManager):**

```kotlin
// UsageCollectWorker.kt — 每15分钟执行
class UsageCollectWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val usageStatsManager = applicationContext
            .getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()

        // 查询最近15分钟的使用统计
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 15 * 60 * 1000,
            now
        )

        // 过滤掉系统应用和自己
        val records = stats
            .filter { it.packageName != applicationContext.packageName }
            .filter { it.totalTimeInForeground > 0 }
            .map { stat ->
                AppUsageRecord(
                    packageName = stat.packageName,
                    appName = getAppName(stat.packageName),
                    durationSeconds = stat.totalTimeInForeground / 1000,
                    date = LocalDate.now().toString(),
                    collectedAt = now
                )
            }

        // 批量写入 Supabase
        supabase.postgrest.from("app_usage").insert(records)

        return Result.success()
    }
}
```

**限时检查逻辑 (AppLimitEvaluator + LimitEnforcementService + AppLimitAccessibilityService):**

孩子端前台服务维护 App 限时规则缓存和本地状态，无障碍服务负责更及时地识别前台应用并在命中限制时返回桌面。限制判定集中在 `AppLimitEvaluator`，支持每日、单次、冷却三种限制：

```kotlin
// checkAndEnforceLimits(foregroundPkg) 每 1 秒兜底调用；无障碍窗口事件会即时触发
fun checkAndEnforceLimits(packageName: String) {
    val appLimit = limitRules[packageName] ?: return  // 无限制

    // 检查冷却期
    if (cooldownActive(packageName)) {
        goHomeAndShowBlockedFeedback("请休息一下", "距下次使用还需等待约 X 分钟")
        return
    }

    // 检查每日总时长
    if (todayUsage >= appLimit.dailyLimitMinutes * 60) {
        goHomeAndShowBlockedFeedback("今日用时已用完", "每日可用 X 分钟")
        return
    }

    // 检查单次时长 (到达后启动冷却)
    if (sessionMinutes >= appLimit.singleSessionMinutes) {
        goHomeAndShowBlockedFeedback("单次时长已到", "单次最多使用 X 分钟")
        endSession() // 结束本段会话，触发冷却倒计时
        return
    }
}
```

单次限制只有在 `single_session_minutes > 0` 且 `cooldown_minutes > 0` 时生效；如果只设置单次时长但冷却间隔为 0，则只执行每日总时长限制。用户回到桌面或切走 App 时结束本段会话：若离开时间达到冷却间隔，下次打开重置单次计时；未达到冷却间隔则继续累加上次单次使用时长。锁屏期间不累计应用使用时长，但离开/冷却间隔时间继续自然流逝。服务或无障碍重新连接时不会清空活跃会话；若受限 App 仍在前台但本次会话状态丢失，显示层会按当前时间恢复会话，避免浮窗退回每日倒计时。

**三层保活机制：**
1. `onTaskRemoved()` → AlarmManager 延时 1 秒重启 Service
2. `KeepAliveWorker`（WorkManager 每 15 分钟）检查并重启
3. `BootReceiver` 监听开机广播自动启动

> **实现说明**：不再依赖把悬浮窗扩成全屏遮罩。华为/国产 ROM 下全屏 overlay 和后台强拉 Activity 不可靠，因此强制限制以无障碍服务 `GLOBAL_ACTION_HOME` 为主，阻挡页/通知作为反馈兜底。若用户在系统最近任务中划掉 kid 端进程，国产 ROM 可能停止前台服务，需开启自启动/后台运行并在最近任务中锁定应用；无障碍权限关闭时只能依靠 UsageStats 轮询和普通悬浮窗提示。

### 4.5 浮窗实现

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- 声明 Service -->
<service
    android:name=".monitor.LimitEnforcementService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

**实际实现** 使用纯 Android View（非 Compose）渲染到 WindowManager：

```
浮窗规格: WRAP_CONTENT x WRAP_CONTENT (自适应内容大小)
位置: 屏幕左上角 (50, 200)
内容: 显示本次/今日剩余可用时间 + 进度条
背景: floating_bg drawable（圆角白底浅阴影）
```

浮窗行为：用于显示受限 App 的剩余可用时间，可拖动。文案会区分 `本次还可用` 和 `今日还可用`：当 `single_session_minutes > 0`、`cooldown_minutes > 0` 且当前存在活跃会话时，固定展示本次剩余时间；否则展示今日剩余时间。剩余时间大于 1 分钟时只显示分钟，1 分钟以内显示秒。

**计时检测**：`LimitEnforcementService` 每 1 秒通过 UsageStats Events 兜底检测前台切换，识别 `MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND / ACTIVITY_RESUMED / ACTIVITY_PAUSED / ACTIVITY_STOPPED`；开启无障碍后，`AppLimitAccessibilityService` 通过 `TYPE_WINDOW_STATE_CHANGED` 提供更及时的前台识别并负责无障碍浮窗显示，普通服务只隐藏自身普通浮窗，不再参与无障碍浮窗的每秒刷新。前台 App 切换、回桌面、锁屏时结束当前会话。

**限制规则**：从 Supabase `app_limits` 表实时读取（30 秒轮询），支持三种限制：
- `daily_limit_minutes`：每日总使用时长上限（默认 999 = 无限制，0 = 禁止）
- `single_session_minutes`：单次连续使用时长上限（默认 0 = 不生效）
- `cooldown_minutes`：单次超时后的冷却间隔（默认 0 = 不生效；单次限制必须和冷却间隔同时设置才生效）

### 4.6 日历视图（家长端）

使用 Compose 自定义日历而不是第三方库（减少依赖）：

```kotlin
// CalendarScreen.kt 核心结构
@Composable
fun CalendarView(
    yearMonth: YearMonth,
    taskDates: Map<LocalDate, List<Task>>,
    onDateClicked: (LocalDate) -> Unit,
    onMonthChanged: (YearMonth) -> Unit
) {
    // 月份标题 + 左右箭头切换
    // 星期标题行: 日一二三四五六
    // 日期网格 6×7（可能包含上月/下月填充日期）
    // 每天 cell 显示任务数量圆点标记
    //   0任务=无标记, 1-2=蓝点, 3-4=橙点, 5+=红点
    // 点击某天 → 弹 BottomSheet 显示当天任务列表
    // 长按某天 → 创建新任务默认选该日期
}
```

---

## 五、父子 App 通信流程（关键）

家长端、任务端和监控端之间**没有直接通信**，业务数据通过 Supabase 中转：

```
孩子端完成操作 → 写 PostgreSQL → Realtime 触发监听 → 家长端收到更新
                                ↘ Realtime Broadcast → 对端实时刷新
```

核心原则：**读操作用 Supabase Realtime 监听，所有变更通过 PostgreSQL → Realtime 实时同步**。

---

## 六、Room 本地缓存设计

用于离线场景，Room 缓存关键数据。Supabase Realtime 本身不带离线持久化，需用 Room 补充：

```kotlin
// 只需一张表
@Entity(tableName = "tasks")
data class TaskCache(
    @PrimaryKey val id: String,
    val title: String,
    val status: String,
    val dueDate: String,
    val rewardPoints: Int,
    val cachedAt: Long
)
```

## 七、开发分期与里程碑

### 第一期（MVP — 已完成）

| 序号 | 任务 | 预估工时 | 状态 |
|------|------|----------|------|
| 1 | 搭建项目脚手架（3 模块 + Hilt + Navigation） | 4h | ✅ |
| 2 | Supabase 配置 + Auth 注册/登录 | 6h | ✅ |
| 3 | 共享数据模型 + Repository 基类 | 8h | ✅ |
| 4 | 任务 CRUD（家长端 + 孩子端查看） | 12h | ✅ |
| 5 | 任务完成 + 积分到账（PostgreSQL 函数） | 4h | ✅ |
| 6 | （已移除） | - | - |
| 7 | （已移除） | - | - |
| 8 | 奖励商城（配置 + 兑换） | 6h | UI 骨架完成 |
| 9 | 应用使用数据采集 | 8h | ✅ |
| 10 | 家长端使用报告页面 | 6h | ✅ |
| 11 | 浮窗服务 | 6h | ✅ |
| 12 | 应用限时设置 + 检查 | 4h | ✅ |
| 13 | 限制强制执行（每日/单次/冷却） | 8h | ✅ 无障碍返回桌面 + 阻挡反馈 |

### 第二期（优化 — 已完成）

- ✅ 日历视图 + 日历区间跨天展示
- ✅ 任务分类系统（动态分类 CRUD）
- ✅ 回收站（软删除 + 还原 + 清空）
- ✅ 连续打卡天数
- ✅ 分类折叠列表 + 按分类排序
- ✅ 日期区间独立任务创建
- ✅ Kid 端积分飞入动画 + 完成庆祝
- ✅ Kid 端"计划"页面（未来任务按日期折叠）
- ✅ 任务撤销功能

### v1.3 增量（本次会话）

- ✅ Kid 端任务列表按时间三组折叠（今日/忘记做的/之后的小任务），默认全部展开
- ✅ Kid 端任务卡片增加描述文字展示
- ✅ Parent 端任务批量删除（管理模式 + 多选 + Checkbox）
- ✅ Kid 端使用详情数据实时化（日/周视图从系统直读 UsageStatsManager）
- ✅ 应用使用数据上传改为先删后插（防重复累加）
- ✅ 柱状图 UI 升级（渐变色 + 入场动画 + 悬浮标签 + 跟随主题色）
- ✅ Kid 端 TabBar 样式优化（悬浮圆角卡片式 + 可爱图标）
- ✅ Parent 端监控页增加刷新按钮 + Tab 切换自动刷新
- ✅ Kid 端"今日各应用时长"列表项可点击 → 单应用逐时柱状图详情
- ✅ 修复 `removeAppLimit` 之前的编译错误引用

### v1.4 增量（本次会话）

- ✅ Kid 端任务卡片 TTS 语音朗读（KidTtsManager — 卡通女声，pitch 1.18 / rate 0.85）
- ✅ 任务卡片左侧彩色装饰条 + 分组标题三色区分（翠绿 / 珊瑚橙 / 淡靛蓝）
- ✅ 确认弹窗文案儿童化（"完成啦？做完才能得到小星星哦～"）
- ✅ 确认弹窗增加 Celebration 庆祝图标
- ✅ TabBar 图标换为可爱风格（Cottage / Redeem / ChildCare）
- ✅ 任务分组文案优化：未完成任务 → "忘记做的任务"，待完成任务 → "之后的小任务"

### v1.5 增量（本次会话）

- ✅ `app_limits` 表新增 `single_session_minutes` 和 `cooldown_minutes` 字段
- ✅ Parent 端限制弹窗增加单次使用时长、冷却间隔设置
- ✅ calendar 日历视图状态圆点优化（已完成全绿/未完成浅橘红）
- ✅ Parent 端监控页增加日期下拉选择器
- ✅ Kid 端「我的」页增加「使用限制」卡片展示
- ✅ Kid 端 `LimitEnforcementService` 前台服务：1 秒前台检测 + 计时 + 规则同步
- ✅ Kid 端 `AppLimitAccessibilityService`：无障碍前台识别 + 命中限制后返回桌面
- ✅ Kid 端剩余时间浮窗：可拖动、每秒刷新、区分“本次还可用/今日还可用”
- ✅ 三层保活（onTaskRemoved AlarmManager / KeepAliveWorker / BootReceiver）
- ✅ 前台检测修复：仅信任 `queryEvents` 避免华为桌面误判切回
- ✅ 限制执行不再依赖全屏 overlay；华为/国产 ROM 以无障碍返回桌面为主

### v1.6 增量（本次会话）

- ✅ 绑定码永久有效；仅在重新生成同类型绑定码或主动删除时失效
- ✅ `generate_binding_code`：task 类型已有活跃码直接返回，不重新生成导致旧码失效
- ✅ `exchange_binding_code`：去掉过期检查逻辑
- ✅ 新增 `get_child_binding_codes` RPC 函数，查询家庭中所有活跃绑定码
- ✅ 客户端 `BindingCodeInfo` 模型 + `getChildBindingCodes` 接口 + 实现
- ✅ 家庭管理页面：孩子卡片下方直接显示已有任务码和监控码

### 第三期（待规划）
- 重复任务模板
- 语音朗读完整实现
- 奖励商城完整实现

---

## 八、Observe 流模式与 Loading 状态管理规范

> ⚠️ **核心规范**：本项目所有数据读取均通过 Repository 层的 `callbackFlow` 轮询实现（5~10 秒间隔），不存在真正的 WebSocket push。ViewModel 中的所有 loading 状态管理必须遵循本节规范。

### 8.1 架构模型

```
[API 写操作]  →  PostgreSQL  →  [下一个轮询周期 5~10s]
                                      ↓
                               callbackFlow.trySend()
                                      ↓
                              ViewModel.collect { }
                                      ↓
                              更新 UI State + 关闭 loading
```

**关键认知**：API 写操作的**成功回调不等于 UI 数据更新**。数据更新发生在 observe 流的下一次 emit。

### 8.2 标准 ViewModel 模式

```kotlin
// ✅ 正确模式
private var hasLoadedOnce = false

private fun observeTasks(childId: String) {
    observeJob?.cancel()
    observeJob = viewModelScope.launch {
        taskRepository.observeChildTasks(childId).collect { tasks ->
            // 首次加载必更新；后续轮询跳过空值（保留旧数据防断网闪现空白）
            val shouldUpdate = !hasLoadedOnce || tasks.isNotEmpty()
            if (shouldUpdate) {
                hasLoadedOnce = true
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tasks = tasks.map { ... }
                )
            }
        }
    }
}

fun deleteTask(taskId: String) {
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)        // ① 开 loading
        taskRepository.deleteTask(taskId).fold(
            onSuccess = {
                // ② ⚠️ 不手动关 loading！等 observe 流下次 emit 时自动关闭
            },
            onFailure = {
                _uiState.value = _uiState.value.copy(isLoading = false) // ③ 失败才手动关
            }
        )
    }
}
```

### 8.3 乐观更新模式（推荐）

适用于需要即时反馈的场景（回收站、分类管理）。**必须同时实现回滚！**

```kotlin
fun restoreTask(taskId: String) {
    viewModelScope.launch {
        val task = _uiState.value.deletedTasks.find { it.id == taskId } ?: return@launch
        // ① 乐观更新：立即从本地列表移除
        _uiState.value = _uiState.value.copy(
            deletedTasks = _uiState.value.deletedTasks.filter { it.id != taskId }
        )
        // ② 调用 API
        taskRepository.restoreTask(taskId).onFailure {
            // ③ 失败回滚：恢复原数据
            _uiState.value = _uiState.value.copy(
                deletedTasks = _uiState.value.deletedTasks + task
            )
        }
    }
}
```

### 8.4 日历/特殊视图

日历数据通过 `getMonthTasks()` 一次性加载，不依赖 observe 流。操作后**必须同步调用刷新**：

```kotlin
fun deleteTask(taskId: String) {
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        taskRepository.deleteTask(taskId).fold(
            onSuccess = {
                if (_uiState.value.viewMode == ViewMode.CALENDAR) {
                    loadMonthData(YearMonth.from(_uiState.value.selectedDate))
                    refreshSelectedDateTasks()
                    _uiState.value = _uiState.value.copy(isLoading = false) // ← 日历数据已同步刷新
                }
                // LIST 模式：不关 loading，等 observe 流
            }
        )
    }
}
```

### 8.5 UI 层 Loading 显示规范

```kotlin
// ❌ 错误：只有 tasks.isEmpty() 时显示 loading
if (uiState.isLoading && uiState.tasks.isEmpty()) {
    CircularProgressIndicator()
} else {
    TaskList(tasks)  // loading=true 但 tasks 有数据时看不到动画
}

// ✅ 正确：任何时候 isLoading 都显示覆盖层
Box(Modifier.fillMaxSize()) {
    TaskList(tasks)
    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()  // 覆盖在列表上方
        }
    }
}
```

### 8.6 Navigation 共享 ViewModel

Navigation Compose 每个 destination 默认独立 scoping。多个页面需要共享同一个 ViewModel 时，必须显式绑定：

```kotlin
// ParentNavGraph.kt
composable("tasks/edit/{taskId}") { backStackEntry ->
    val tasksViewModel: TasksViewModel =
        hiltViewModel(navController.getBackStackEntry(ParentTab.Tasks.route))
    TaskEditScreen(viewModel = tasksViewModel)
}
```

否则编辑页面保存后切换回列表，数据不会更新。

### 8.7 子页面状态管理

"我的"等 Tab 内的子页面（回收站、分类管理等），切换 Tab 后重新点击应回到主页而非子页面：

```kotlin
NavigationBarItem(
    onClick = {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = tab != ParentTab.Profile  // ← "我的"不恢复子页面
        }
    }
)
```

### 8.8 Supabase RPC 调用规范

```kotlin
// ❌ 错误：RPC 不支持 filter {} 语法
postgrest.rpc("complete_task") { filter { eq("p_task_id", taskId) } }

// ✅ 正确：使用 parameters = mapOf()
postgrest.rpc(
    function = "complete_task",
    parameters = mapOf("p_task_id" to taskId, "p_child_id" to childId)
)
```

### 8.9 callbackFlow 必须轮询

```kotlin
// ❌ 错误：flow {} 只 emit 一次
override fun getCurrentPoints(childId: String): Flow<Int> = flow {
    val user = postgrest.from("users").select { ... }.decodeSingleOrNull<User>()
    emit(user?.totalPoints ?: 0)  // ← 只 emit 一次，永远不会更新
}

// ✅ 正确：callbackFlow + 轮询
override fun getCurrentPoints(childId: String): Flow<Int> = callbackFlow {
    suspend fun fetch() {
        val user = postgrest.from("users").select { ... }.decodeSingleOrNull<User>()
        trySend(user?.totalPoints ?: 0)
    }
    fetch()
    while (true) { delay(5000); fetch() }
}
```

### 8.10 常见反模式总结

| 反模式 | 症状 | 根因 |
|--------|------|------|
| API 成功立即关 loading | loading 闪一下消失，数据 5 秒后才更新 | observe 流尚未 emit 新数据 |
| `flow {}` 替代 `callbackFlow {}` | 数据永远不更新 | flow builder 只 emit 一次 |
| loading 条件 ≈ `isLoading && list.isEmpty()` | 已有数据时操作无 loading 动画 | 条件排除了非空列表 |
| 乐观更新无回滚 | 操作失败但 UI 已变，下次进入恢复原状 | 未在 onFailure 中恢复旧数据 |
| Navigation 默认 ViewModel scoping | 编辑后列表不刷新 | 两个页面用了不同 ViewModel 实例 |
| RPC 用 `filter {}` 传参 | 调用失败无响应 | RPC 参数应通过 `parameters` map 传递 |

---

## 九、关键注意事项

1. **Widevine DRM 与 UsageStatsManager**: 需要引导家长在孩子平板上手动开启"使用情况访问权限"（Settings → Security → Usage Access），首次启动有引导页面。

2. **浮窗权限**: SYSTEM_ALERT_WINDOW 需要引导家长手动授权，儿童模式可能阻止，需要检测并跳转。

3. **Supabase URL/Key 配置**: supabaseUrl 和 supabaseKey 硬编码在 SupabaseModule 中，发布前需要加密或从 BuildConfig 读取。

4. **Realtime 连接保持**: Supabase WebSocket 需要心跳维持，断网后自动重连。

5. **RLS 策略订阅**: 需要在 Supabase Dashboard 中手动执行本文档中的 SQL 建表语句和安全策略。
