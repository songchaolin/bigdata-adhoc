# 配置参考

> 定位：**机制说明 + 常用项速查**。完整生产清单（含每项生产建议、Apollo 加密等）见 [apollo-prod-config.md](apollo-prod-config.md)，两者不重复维护，冲突时以代码默认值为准。

## 配置机制

### 优先级

```
Apollo  >  环境变量  >  本地 application.yml  >  代码默认值
```

- 业务参数统一以 `ConfigItem.of(key, defaultValue, 描述)` 声明，经 `ConfigHolder` **每次读活值**：Apollo 改动后秒级生效，**无需重启**。
- 类型由默认值字面量推断：`5000L` → Long，`20` → Integer，`true` → Boolean。Apollo 里数字项只填数字（填 `5000ms` 这类带单位的会转换失败回退默认值）。
- 仅 **4 个框架端口类配置**走 `@Value`，修改需重启：`server.port`（server 8080 / executor 8081）、`grpc.server.port`（server 9090 / executor 9091）。
- `app.id` / `apollo.meta` 在 `META-INF/app.properties`，修改需重新打包。

### 自检

- `GET /api/config`：返回当前生效的 ConfigItem 清单（key / 当前值 / 默认值 / 描述），敏感值（password）脱敏。
- 启动日志（`StartupConfigLogger`）会打印生效配置。
- 注意：引擎/元数据的**每实例动态 key 不在** `/api/config` 清单里（`ConfigHolder.getString` 动态读），需在 Apollo 手动配齐。

## 两个 App 的必配项（无默认值或环境相关）

| 配置 | App | 说明 |
|------|-----|------|
| `spring.datasource.url` / `.username` / `.password` | server + executor | 元数据库连接，密码建议 Apollo 加密 |
| `adhoc.engine.KYUUBI.{instance}.endpoints` / `.user` / `.password` | executor | Kyuubi 引擎连接，**无默认，未配抛 `ADHOC_ENGINE_INSTANCE_NOT_FOUND`** |
| `adhoc.engine.STARROCKS.{instance}.endpoints` / `.user` / `.password` | executor | StarRocks 引擎连接，同上 |
| `adhoc.metadata.hive-metastore.{instance}.url` / `.user` / `.password` | server | Hive metastore（元数据补全），**无默认，未配抛 `ADHOC_METADATA_NOT_CONFIGURED`** |
| `adhoc.metadata.starrocks.{instance}.url` / `.user` / `.password` | server | StarRocks 元数据查询，同上 |
| `adhoc.dashboard.access-token` | server | 大盘令牌，**生产必配**（空 = 不启用鉴权） |
| `adhoc.admin.user-ids` | server | 管理员用户 ID（逗号分隔，读/取消接口归属校验放行） |

多实例 key 中的 `{instance}` 是实例名（如 `kyuubi-01`），须与 `adhoc.engine.{ENGINE}.default_instance` / `adhoc.metadata.*.default_instance` 及提交时 `engineInstance` 参数对齐；**executor 侧引擎实例名与 server 侧元数据实例名必须同名**（同一逻辑集群）。

## 常用配置速查

### 限流 `adhoc.limit.*`（server）

| Key | 默认值 | 说明 |
|-----|--------|------|
| `adhoc.limit.max-tasks-per-job` | 20 | 单 Job SQL 段数上限 |
| `adhoc.limit.max-pending-jobs-per-user` | 1000 | 单用户 PENDING Job 数 |
| `adhoc.limit.max-pending-jobs-global` | 1000 | 全局 PENDING 总数 |
| `adhoc.limit.max-running-jobs-global` | 5000 | 全局 RUNNING 总数 |
| `adhoc.limit.max-running-jobs-per-user` | 3000 | 单用户 RUNNING 数 |
| `adhoc.limit.max-running-jobs-per-server` | 300000 | 单 server 承接数 |

> 生产建议：per-user 限额调小（50~100），远小于 global，保证多用户公平；默认值偏宽松。

### 调度 `adhoc.schedule.*`（server）

| Key | 默认值 | 说明 |
|-----|--------|------|
| `adhoc.schedule.interval-ms` | 2000 | PENDING 扫描抢占间隔(ms) |
| `adhoc.schedule.recent-window-hours` | 6 | 只扫最近 N 小时内提交的 PENDING（老 Job 靠 reconcile 兜底） |
| `adhoc.schedule.event-trigger-enabled` | true | 事件驱动派发（提交后立即触发）。**生产必须 true** |
| `adhoc.schedule.ping-blacklist-ms` | 10000 | ping 失败 executor 短期熔断时长(ms) |

### 实例与心跳

| Key | App | 默认值 | 说明 |
|-----|-----|--------|------|
| `adhoc.server.heartbeat-interval-ms` | server | 5000 | server 心跳间隔(ms) |
| `adhoc.server.instance-id` | server | 空(取 host:port) | 多实例建议显式设 |
| `adhoc.heartbeat.interval-ms` | executor | 5000 | executor 心跳间隔(ms) |
| `adhoc.executor.instance-id` | executor | 空(取 host:port) | 同上 |
| `adhoc.executor.engine-types` | executor | `KYUUBI,STARROCKS` | 本实例支持的引擎子集 |

### 执行器 `adhoc.executor.*`（executor）

| Key | 默认值 | 说明 |
|-----|--------|------|
| `adhoc.executor.max-concurrent-tasks` | 10000 | 同时执行 Job 数上限（Semaphore，满则 reject） |
| `adhoc.executor.result-limit` | 1000000 | 单 Task 结果行数硬上限 |
| `adhoc.executor.query-timeout-sec` | 3600 | 单 Task 查询超时秒（0=不限制；StarRocks 生效，Kyuubi 待支持） |
| `adhoc.executor.server-log-interval-ms` | 2000 | Kyuubi operation log 轮询间隔(ms) |

### 引擎连接 `adhoc.engine.*`（executor）

| Key | 默认值 | 说明 |
|-----|--------|------|
| `adhoc.engine.{ENGINE}.default_instance` | `kyuubi-01` / `starrocks-01` | 未指定 instance 时用 |
| `adhoc.engine.{ENGINE}.auth-mode` | `FIXED` | `FIXED`=统一账号 / `USER`=按提交人工号直连（生产推荐，Kyuubi 无密码直连、StarRocks 工号+统一密码） |
| `adhoc.engine.{ENGINE}.{instance}.endpoints` | 无 | host:port 列表（逗号分隔，HA） |
| `adhoc.engine.{ENGINE}.{instance}.user` / `.password` | 无 | 每实例独立账号（USER 模式下 Kyuubi 忽略） |
| `adhoc.engine.STARROCKS.{instance}.database` / `.params` | 空 | 默认库 / JDBC 连接参数 |

### 元数据补全 `adhoc.metadata.*`（server）

| Key | 默认值 | 说明 |
|-----|--------|------|
| `adhoc.metadata.hive-metastore.default_instance` / `.pool-size` | `kyuubi-01` / 4 | 默认实例名 / Druid 池大小 |
| `adhoc.metadata.starrocks.default_instance` / `.pool-size` | `starrocks-01` / 4 | 同上 |
| `adhoc.metadata.{source}.{instance}.url` / `.user` / `.password` | 无 | 每实例连接（source = hive-metastore / starrocks），必配 |
| `adhoc.metadata.cache.db-ttl-min` | 30 | 库列表缓存 TTL(分钟) |
| `adhoc.metadata.cache.table-ttl-min` | 10 | 表列表缓存 TTL(分钟) |
| `adhoc.metadata.cache.column-ttl-min` | 30 | 列列表缓存 TTL(分钟) |

### 日志收集 `adhoc.log.*`

| Key | App | 默认值 | 说明 |
|-----|-----|--------|------|
| `adhoc.log.collect-interval-ms` | server | 2000 | 拉 executor 日志间隔(ms) |
| `adhoc.log.complete-grace-ms` | server | 30000 | 终态 Job 判 complete 兜底等待。**必须 > executor 的 `reconcile-scan-interval-ms`** |
| `adhoc.log.reconcile-scan-interval-ms` | executor | 10000 | LogBuffer 兜底对账扫描间隔 |
| `adhoc.log.finalize-delay-ms` | executor | 10000 | LogBuffer 终态补全延迟(ms) |

### 健康检查 / 补偿 / 对账（server）

| Key | 默认值 | 说明 |
|-----|--------|------|
| `adhoc.healthcheck.interval-ms` | 10000 | 健康检查（标 DOWN）扫描间隔 |
| `adhoc.compensation.interval-ms` | 10000 | 宕机补偿扫描间隔 |
| `adhoc.reconcile.interval-ms` | 300000 | 状态对账扫描间隔(ms) |
| `adhoc.reconcile.stuck-threshold-minutes` | 30 | Job 卡住判定阈值(分钟) |
| `adhoc.reconcile.absolute-stuck-threshold-minutes` | 60 | Job 绝对卡死阈值(分钟) |
| `adhoc.cleanup.interval-ms` | 3600000 | DOWN 实例物理清理间隔(ms) |

### JVM 指标（server）

| Key | 默认值 | 说明 |
|-----|--------|------|
| `adhoc.jvm-metric.retention-hours` | 168 | 采样历史保留(小时)，对齐大盘 7d 时间窗 |
| `adhoc.jvm-metric.cleanup-interval-ms` | 300000 | 采样清理扫描间隔 |

### 鉴权（server）

| Key | 默认值 | 说明 |
|-----|--------|------|
| `adhoc.dashboard.access-token` | 空 | 大盘访问令牌（空=不启用）；请求带 `X-Dashboard-Token` 头或 `?token=` 参数 |
| `adhoc.admin.user-ids` | 空 | 管理员 ID 逗号分隔，读/取消接口按"本人或管理员"放行 |

### 存储与共享参数

存储类型见 [deployment.md - 存储配置](deployment.md#存储配置)。以下共享参数**须同时配在 server 与 executor 两个 App 且值一致**：

| Key | 默认值 | 说明 |
|-----|--------|------|
| `adhoc.storage.type` | local | local / aliyun |
| `adhoc.storage.local.base-dir` | `./data/storage` | local 模式落盘目录 |
| `adhoc.storage.aliyun.endpoint` / `.bucket` / `.access-key` / `.secret-key` | 无 | OSS 连接（aliyun 模式） |
| `adhoc.oss.project-name` | `adhoc` | OSS 项目空间 |
| `adhoc.oss.result-life` / `adhoc.oss.log-life` | `DAY_30_DELETE` | 结果/日志文件生命周期 |
| `adhoc.oss.log-flush-interval-ms` / `.log-flush-batch-lines` | 2000 / 100 | 日志刷 OSS 间隔 / 批行数 |
| `adhoc.result.reuse-ttl-seconds` | 300 | 结果复用 TTL：同用户同 SQL 在窗口内直接复用 |
| `adhoc.status.interval-ms` | 60000 | 状态播报间隔(ms) |

## 生产调参红线（摘要）

1. 日志时序约束：`complete-grace-ms`(30s) **>** `reconcile-scan-interval-ms`(10s)，否则漏读终态日志页。
2. OSS 共享参数两 App 必须一致，否则结果/日志落不同空间。
3. 实例名两侧对齐：executor 引擎实例名 = server 元数据实例名。
4. 密码类（datasource / engine / metadata）用 Apollo 加密值。
5. JVM 保留期调小时注意大盘 7d 窗口会缺数据。

完整建议与排查注意事项见 [apollo-prod-config.md](apollo-prod-config.md)。
