# 认字音素后台生成函数

部署目标：腾讯云 SCF **事件函数** `generate-literacy-phonetics`，Node.js 18，执行方法为
`index.main_handler`。它没有 Web/API Gateway 入口，只接受 SCF 定时触发器或拥有
`scf:InvokeFunction` 权限的管理端调用。

新建待认识任务时，`evaluate-reading` 仍会即时生成音素；此函数只以低频定时扫描兜底，处理
`pending` 和等待 5 分钟后可重试的 `failed` 资产。它不会读取儿童登录凭证，也不需要
`LITERACY_PHONETIC_BACKFILL_KEY`。

## 环境变量

| 变量 | 说明 |
| --- | --- |
| `SUPABASE_URL` | Supabase 项目 URL |
| `SUPABASE_SERVICE_ROLE_KEY` | 仅保存在 SCF 配置中的 service-role 密钥 |

## 部署

1. 在本目录运行 `npm install --omit=dev` 与 `npm test`。
2. 打包时确保 ZIP 根目录直接包含 `index.js`、`package.json`、`package-lock.json` 和生产依赖
   `node_modules/`。
3. 在腾讯云 SCF 创建事件函数 `generate-literacy-phonetics`，选择 Node.js 18，处理方法填写
   `index.main_handler`，内存设为 256 MB，超时设为 30 秒。
4. 上传部署包，配置上述两个环境变量。不要创建函数 URL 或 API Gateway。
5. 新建定时触发器 `literacy-phonetic-backfill-30min`：频率选择**每 30 分钟**，附加信息填写
   `{"action":"generate","limit":50}`。

定时器一次最多处理 50 条。正常新增会即时处理，因此 30 分钟频率仅用于网络异常、函数超时等
兜底；不需要在触发器附加信息中保存任何密钥。

当前部署包为 `generate-literacy-phonetics-20260823-scheduled-backfill.zip`。

2026-08-24 已完成一次生产环境手动测试，返回 `claimed=0`、`ready=0`、`failed=0`，说明函数的
Supabase service-role 权限与音素队列调用正常；仍应确认定时触发器至少自动执行一次。

## 验收与监控

运行一次测试调用或等待定时器执行后，在 Supabase 查询：

```sql
select status, count(*) from literacy_phonetic_assets group by status order by status;
```

观察函数日志中的 `音素资产定时回填完成`。若持续存在 `failed`，请在认字端音素详情中修正，或根据
`last_error` 修复无法被词典解析的文本；同一资产最多自动尝试三次。
