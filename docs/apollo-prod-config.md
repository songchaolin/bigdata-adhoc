# Apollo 生产配置参数清单 · bigdata-adhoc

> 用途：server / executor 两个服务在 Apollo 配置中心配置生产环境的参考清单。
> 最后更新：2026-09-03　|　对应代码：`AdhocServerConfig` / `AdhocExecutorConfig` / `AdhocCommonConfig` / `AdhocMetadataConfig`

---

## 0. 配置机制（先读这个）

- **参数定义**：业务参数以 `ConfigItem.of(key, defaultValue, 描述, 调参效果)` 声明为 `public static final` 常量。`key` 即 Apollo 属性名，`defaultValue` 是代码默认。
- **取值优先级**：`ConfigHolder.get(item)` = `env.getProperty(key, type, defaultValue)` → **Apollo 优先 → 本地 yml → 代码默认值**。每次调用读活值，Apollo auto-update 后下次调用即生效，**无需重启**。
- **类型由默认值字面量推断**：带 `L`（如 `5000L`）→ `Long`；不带（如 `20`）→ `Integer`；`true` → `Boolean`；字符串带引号。Apollo 里 Long/Integer 项填数字即可，Apollo 会按类型转换。
- **本地 yml 几乎全注释**：`application.yml` 仅 `apollo.bootstrap` 段激活，其余为模板示例。**真实框架配置（datasource / 端口 / logging / mybatis-plus）必须配在 Apollo**，不在仓库里。
- **引擎/元数据多实例参数无默认值、无回退**：`adhoc.engine.{ENGINE}.{instance}.*` 和 `adhoc.metadata.*.{instance}.{url,user,password}` 未配会直接抛异常（`ADHOC_ENGINE_INSTANCE_NOT_FOUND` / `ADHOC_METADATA_NOT_CONFIGURED`），不静默回退。

---

## 1. Apollo 接入信息

`app.id` 与 `apollo.meta` 在 `META-INF/app.properties`（Apollo 客户端约定，不在 Apollo namespace 里）。生产需在 **Apollo 后台先建好这两个 App**。

| 项 | server 应用                                     | executor 应用         |
|---|-----------------------------------------------|---------------------|
| **app.id** | `bigdata-adhoc-server`                        | `bigdata-adhoc-executor` |
| **apollo.meta** | `http://configserver:8080`                    | `http://xxxx:8080`  |
| **namespace** | `application`（默认，未显式配 `apollo.bootstrap.namespaces`） | `application`（默认）   |
| **env** | 通过 JVM `-Denv=PRO` 或环境变量 `APOLLO_META` 指定     | 同左                  |

> 生产注意：
> - `apollo.meta=http://xxx:8080` 依赖内网 DNS `configserver` 解析。若生产 DNS 不通，改为实际 Apollo 配置中心地址（如 `http://<apollo-host>:8080`）。改 `META-INF/app.properties` 后需重新打包。
> - 无 `bootstrap.yml`、无 `application-prod.yml` 环境配置文件，环境差异全部由 Apollo 接管。
> - 两个 App 的 `application` namespace 各自独立。**共享参数（`adhoc.oss.*` / `adhoc.result.*` / `adhoc.status.*`）需在两个 App 的 namespace 各配一份，且 OSS 相关参数必须保持一致**（否则结果/日志落到不同 OSS 空间）。如想避免重复，可配 `apollo.bootstrap.namespaces=application,adhoc-common` 把共享参数放公共 namespace，但需改 `app.properties`，按需取舍。

---

## 2. 必配项速查（无默认值或环境相关，Apollo 必须填）

| 必配项 | 所属 App | 说明                                                                                              |
|---|---|-------------------------------------------------------------------------------------------------|
| `spring.datasource.url` / `.username` / `.password` | server + executor | 指向元数据库（MySQL，默认库名 `adhoc`）；password 建议用 Apollo 加密值                                                     |
| `server.port` | server | HTTP 端口（默认 8080，生产显式配）                                                                          |
| `grpc.server.port` | server(默认9090) / executor(默认9091) | gRPC 端口，生产显式配                                                                                   |
| `grpc.client.adhoc-executor.address` | server | `static://<executor-host>:9091`，server→executor gRPC                                            |
| `adhoc.engine.KYUUBI.{instance}.endpoints/user/password` | executor | Kyuubi 引擎连接，无默认                                                                                 |
| `adhoc.engine.STARROCKS.{instance}.endpoints/user/password` | executor | StarRocks 引擎连接，无默认                                                                              |
| `adhoc.metadata.hive-metastore.{instance}.url/user/password` | server | Hive metastore MySQL（元数据补全），无默认                                                                 |
| `adhoc.metadata.starrocks.{instance}.url/user/password` | server | StarRocks 元数据查询，无默认                                                                             |
| `adhoc.storage.aliyun.*`（endpoint/bucket/access-key/secret-key） | server + executor | 用阿里云 OSS 时必配（`adhoc.storage.type=aliyun`），两个 App 参数须一致 |
| `adhoc.dashboard.access-token` | server | 大盘/运维接口访问令牌（默认空=不启用鉴权）。生产**必填**长随机串防裸奔，泄露改值秒级生效                                                 |
| `adhoc.engine.{ENGINE}.auth-mode` | executor | 引擎鉴权模式（默认 `FIXED`）。**生产置 `USER`**：SR 用户名=访问用户工号+实例统一密码，Kyuubi 工号直连无密码无代理；改回 `FIXED` 恢复统一账号，无需重启 |

---

## 3. Server 应用配置（app.id = `bigdata-adhoc-server`）

### 3.1 框架 / 基础设施（仓库 yml 未含，必须配）

| Key | 示例值 / 说明 | 生产建议 |
|---|---|---|
| `spring.application.name` | `bigdata-adhoc-server` | 必配（logback springProperty 依赖） |
| `server.servlet.context-path` | `/adhoc` | 可选，HTTP 前缀（默认无前缀） |
| `server.port` | `8080` | 显式配，避免多实例端口冲突 |
| `grpc.server.port` | `9090` | 显式配 |
| `spring.datasource.url` | `jdbc:mysql://<mysql-host>:3306/adhoc?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai` | 指向元数据库（默认库名 `adhoc`） |
| `spring.datasource.username` | `<生产账号>` | — |
| `spring.datasource.password` | `<加密值>` | 用 Apollo 加密 |
| `spring.datasource.driver-class-name` | `com.mysql.cj.jdbc.Driver` | — |
| `grpc.client.adhoc-executor.address` | `static://<executor-host>:9091` | 多 executor 用 `static://h1:9091,h2:9091` |
| `grpc.client.adhoc-executor.negotiation-type` | `plaintext` | 内网明文；上 TLS 改 `tls` |
| `mybatis-plus.mapper-locations` | `classpath*:mapper/**/*.xml` | — |
| `eureka.client.enabled` | `false` | 按是否接注册中心 |
| `logging.level.io.gitee.songchaolin.adhoc` | `INFO` | 生产 INFO，排查临时开 DEBUG |
| `logging.level.com.ctrip.framework.apollo` | `WARN` | 启动期 DEBUG 可关 |

### 3.2 限流 `adhoc.limit.*`

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.limit.max-tasks-per-job` | Integer | 20 | 单 Job SQL 段数上限（按 `;` 拆，含 SET/USE）。提交时校验 | 保持 20；长脚本需求大再调 |
| `adhoc.limit.max-pending-jobs-per-user` | Integer | 1000 | 单用户 PENDING Job 数。提交时校验 | 建议调小到 50~100，防单用户占满全局队列（默认与 global 相同=单用户可占满） |
| `adhoc.limit.max-pending-jobs-global` | Integer | 1000 | 全局 PENDING 总数。提交时校验 | 按集群规模设 500~2000 |
| `adhoc.limit.max-running-jobs-global` | Integer | 5000 | 全局 RUNNING 总数。调度时校验（满了不领） | 按引擎承载设，保护引擎 |
| `adhoc.limit.max-running-jobs-per-user` | Integer | 3000 | 单用户 RUNNING 数 | 建议调小（如 50~200），用户并发公平 |
| `adhoc.limit.max-running-jobs-per-server` | Integer | 300000 | 单 server processing（DISPATCHING+RUNNING）数 | 默认过大，单机实际跑不到；按单机容量调小（如 10000） |

### 3.3 server 实例与心跳 `adhoc.server.*`

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.server.heartbeat-interval-ms` | Long | 5000 | server 心跳间隔(ms) | 保持 5000 |
| `adhoc.server.instance-id` | String | `""`(空) | 实例标识（空则取 host:httpPort） | 多实例建议显式设（如 `server-prod-01`）便于追溯 |
| `adhoc.server.version` | String | `1.0.0` | 版本 | 灰度/排查用，按发布版本设 |

### 3.4 调度 `adhoc.schedule.*`

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.schedule.interval-ms` | Long | 2000 | QueueWorker 扫 PENDING 抢占间隔(ms) | 保持 2000；DB 压力大可调到 3000~5000 |
| `adhoc.schedule.recent-window-hours` | Integer | 6 | 调度回溯窗口：只扫最近 N 小时内提交的 PENDING | 保持 6；老 job 靠 reconcile 兜底 |
| `adhoc.schedule.event-trigger-enabled` | Boolean | true | 是否启用事件驱动派发（submit 后立即 triggerDispatch） | **生产必须 true**（更及时）；仅测试用 false |
| `adhoc.schedule.ping-blacklist-ms` | Long | 10000 | ping 失败 executor 的短期熔断时长(ms) | 保持 10000 |

### 3.5 健康检查 / 宕机补偿 / 状态对账 / 清理

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.healthcheck.interval-ms` | Long | 10000 | 健康检查（标 DOWN）扫描间隔 | 保持 10000 |
| `adhoc.compensation.interval-ms` | Long | 10000 | 宕机补偿扫描间隔 | 保持 10000 |
| `adhoc.reconcile.interval-ms` | Long | 300000 | 状态对账扫描间隔(ms) | 保持 300000（5min 兜底） |
| `adhoc.reconcile.stuck-threshold-minutes` | Integer | 30 | Job 卡住判定阈值(分钟)，超时未变标 FAILED | 保持 30 |
| `adhoc.reconcile.absolute-stuck-threshold-minutes` | Integer | 60 | Job 绝对卡死阈值(分钟)，超此强制终态 | 保持 60 |
| `adhoc.cleanup.interval-ms` | Long | 3600000 | DOWN 实例物理清理间隔(ms) | 保持 3600000（1h） |

### 3.6 日志收集 `adhoc.log.*`（server 侧）

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.log.collect-interval-ms` | Long | 2000 | JobLogCollector 拉 executor 日志间隔 | 保持 2000 |
| `adhoc.log.complete-grace-ms` | Long | 30000 | 终态 Job 缺 COMPLETE 标识时判 complete 的兜底等待(ms) | 保持 30000；**须 > executor 的 `adhoc.log.reconcile-scan-interval-ms`(10s)**，否则漏读终态页 |

### 3.7 JVM 指标采样 `adhoc.jvm-metric.*`（server 侧单点清理）

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.jvm-metric.retention-hours` | Integer | 168 | JVM 采样历史保留(小时)，超期物理删除 | 保持 168（7d，对齐大盘 7d 时间窗） |
| `adhoc.jvm-metric.cleanup-interval-ms` | Long | 300000 | 采样历史清理扫描间隔 | 保持 300000 |

### 3.8 Dashboard 访问鉴权 `adhoc.dashboard.*`（server 侧）

指标大盘（`/dashboard/**`）与运维接口（`/api/metrics/**`）的访问令牌闸。

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.dashboard.access-token` | String | （空） | dashboard/metrics 访问令牌（空=不启用鉴权；非空=请求须带 `X-Dashboard-Token` 头或 URL `?token=` 参数匹配） | **生产必填**。任意非空字符串即可（无长度下限/格式校验），建议较长的随机串防猜测；空=裸奔。泄露后改值秒级生效无需重启。本地开发留空免登录 |

### 3.9 管理员 `adhoc.admin.*`（server 侧）

读类接口（`/api/job/detail`、`/job/status`、`/job/progress`、`/api/task/detail`、`/task/result`、`/job/result`、`/task/log`、`/job/log`）与取消接口（`/api/job/cancel`）归属校验：**本人或管理员放行**，其余返 `ADHOC_JOB_FORBIDDEN`。dashboard metric 带 token 访问、gRPC/SDK 调用视为系统级（`accessUserId=null`）直接放行。

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.admin.user-ids` | String | （空） | 管理员工号列表（逗号分隔，对应 `user_id` 字段即网关 userCode） | 填运维人员工号，如 `01948706,02012345`；空=无管理员，仅本人可读自己的 Job。改值秒级生效 |

### 3.10 共享参数（同时配在 server + executor 两个 App，OSS 须一致）

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.oss.project-name` | String | `adhoc` | OSS 项目空间 | 按生产 OSS 命名空间填；改它=换空间，历史文件留旧空间 |
| `adhoc.oss.result-life` | String | `DAY_30_DELETE` | 结果文件生命周期 | 按存储成本/保留需求调（如 `DAY_90_DELETE`） |
| `adhoc.oss.log-life` | String | `DAY_30_DELETE` | 日志文件生命周期 | 同上 |
| `adhoc.oss.req-user-code` | String | `adhoc-executor` | OSS 请求 user code（影响 OSS 侧归属/审计） | 按生产 OSS 账号设 |
| `adhoc.oss.log-flush-interval-ms` | Integer | 2000 | 日志刷 OSS 间隔(ms) | 保持 2000 |
| `adhoc.oss.log-flush-batch-lines` | Integer | 100 | 日志刷批行数 | 保持 100 |
| `adhoc.result.reuse-ttl-seconds` | Integer | 300 | 结果复用 TTL（秒），同用户 5min 内重复 SQL 直接复用 | 保持 300；对数据新鲜度要求高调小 |
| `adhoc.status.interval-ms` | Long | 60000 | 状态播报间隔(ms)：Job 计数/processing/executor 状态/限流阈值 | 保持 60000 |

### 3.11 元数据模块 `adhoc.metadata.*`（server 侧，adhoc-metadata 模块）

> 元数据模块直连 Hive metastore MySQL 和 StarRocks 查库/表/列信息（server import，ComponentScan 装配）。**实例名须与 executor 侧 `adhoc.engine.*` 的实例名对齐**（同逻辑集群同名）。

#### 3.11.1 默认实例与池（引擎级回退）

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.metadata.hive-metastore.default_instance` | String | `kyuubi-01` | Hive metastore 默认实例名 | 与 `adhoc.engine.KYUUBI.default_instance` 对齐 |
| `adhoc.metadata.hive-metastore.pool-size` | Integer | 4 | Hive metastore Druid 池大小（引擎级回退） | 补全高频可调到 8~16 |
| `adhoc.metadata.starrocks.default_instance` | String | `starrocks-01` | StarRocks 元数据默认实例名 | 与 `adhoc.engine.STARROCKS.default_instance` 对齐 |
| `adhoc.metadata.starrocks.pool-size` | Integer | 4 | StarRocks 元数据 Druid 池大小 | 按需调 |

#### 3.11.2 每实例连接（无默认，必配；实例名作 key 中段）

| 动态 Key | 必填 | 说明 |
|---|---|---|
| `adhoc.metadata.hive-metastore.{instance}.url` | 是 | Hive metastore MySQL JDBC（如 `jdbc:mysql://<hms-host>:3306/hive?...`） |
| `adhoc.metadata.hive-metastore.{instance}.user` | 是 | 账号 |
| `adhoc.metadata.hive-metastore.{instance}.password` | 是 | 密码（Apollo 加密） |
| `adhoc.metadata.hive-metastore.{instance}.pool-size` | 否 | 覆盖引擎级 pool-size |
| `adhoc.metadata.starrocks.{instance}.url` | 是 | StarRocks MySQL JDBC（如 `jdbc:mysql://<sr-fe-host>:9030?...`） |
| `adhoc.metadata.starrocks.{instance}.user` | 是 | 账号 |
| `adhoc.metadata.starrocks.{instance}.password` | 是 | 密码（Apollo 加密） |
| `adhoc.metadata.starrocks.{instance}.pool-size` | 否 | 覆盖引擎级 |

#### 3.11.3 缓存 TTL `adhoc.metadata.cache.*`

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.metadata.cache.db-ttl-min` | Long | 30 | 库列表缓存 TTL(分钟) | 库变更少，保持 30 |
| `adhoc.metadata.cache.table-ttl-min` | Long | 10 | 表列表缓存 TTL(分钟) | 表变更相对频，保持 10 |
| `adhoc.metadata.cache.column-ttl-min` | Long | 30 | 列列表缓存 TTL(分钟) | 列稳定，保持 30 |

---

## 4. Executor 应用配置（app.id = `bigdata-adhoc-executor`）

### 4.1 框架 / 基础设施

| Key | 示例值 / 说明 | 生产建议 |
|---|---|---|
| `spring.application.name` | `bigdata-adhoc-executor` | 必配 |
| `server.servlet.context-path` | `/adhoc` | 可选，HTTP 前缀（默认无前缀） |
| `grpc.server.port` | `9091` | 显式配 |
| `spring.datasource.url` | `jdbc:mysql://<mysql-host>:3306/adhoc?...` | 指向元数据库（与 server 同库） |
| `spring.datasource.username` / `.password` | `<生产账号>` / `<加密值>` | password 用 Apollo 加密 |
| `spring.datasource.driver-class-name` | `com.mysql.cj.jdbc.Driver` | — |
| `mybatis-plus.mapper-locations` | `classpath*:mapper/**/*.xml` | — |
| `eureka.client.enabled` | `false` | 按需 |
| `logging.level.io.gitee.songchaolin.adhoc` | `INFO` | 生产 INFO |

> 不要使用遗留的 `adhoc.kyuubi.url`（已废弃，现走每实例 endpoints）和 `adhoc.executor.local-base-dir`（仅测试）。

### 4.2 executor 心跳与执行 `adhoc.heartbeat.*` / `adhoc.executor.*`

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.heartbeat.interval-ms` | Long | 5000 | executor 心跳上报间隔 | 保持 5000 |
| `adhoc.executor.result-limit` | Integer | 1000000 | 单 task 结果行数硬上限（SQL rewrite LIMIT + 行 cap） | 按内存/OSS 调；防 OOM 可调小 |
| `adhoc.executor.server-log-interval-ms` | Long | 2000 | Kyuubi operation log 轮询间隔 | 保持 2000 |
| `adhoc.executor.engine-types` | String | `KYUUBI,STARROCKS` | executor 支持的引擎（逗号分隔） | 按部署引擎子集配 |
| `adhoc.executor.instance-id` | String | `""`(空) | 实例标识（空则取 host:grpcPort） | 多实例建议显式设 |
| `adhoc.executor.version` | String | `1.0.0` | 版本 | 按发布版本设 |
| `adhoc.executor.max-concurrent-tasks` | Integer | 10000 | 同时执行 Job 数（Semaphore 强制，满则 reject） | 按单节点资源调；默认偏大，实际按引擎并发设 |
| `adhoc.executor.query-timeout-sec` | Integer | 3600 | 单 task 查询超时秒（JDBC setQueryTimeout 兜底，0=不限制） | 保持 3600；StarRocks 生效，Kyuubi 待支持 |

### 4.3 引擎默认实例名 `adhoc.engine.{ENGINE}.default_instance`

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.engine.KYUUBI.default_instance` | String | `kyuubi-01` | Kyuubi 默认实例名 | 与下方实例配置的 key 中段一致 |
| `adhoc.engine.STARROCKS.default_instance` | String | `starrocks-01` | StarRocks 默认实例名 | 同上 |

### 4.4 引擎鉴权模式 `adhoc.engine.{ENGINE}.auth-mode`（引擎级开关）

> 决定引擎连接用**统一账号**还是**访问用户工号直连**，引擎级（一次改动切整个引擎所有实例），活读，Apollo 改后下次 resolve 即生效、**无需重启**。默认 `FIXED`，非生产环境不配也是 FIXED，行为与历史一致。

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.engine.KYUUBI.auth-mode` | String | `FIXED` | Kyuubi 鉴权模式 | 生产置 `USER`（工号直连无密码无代理）；切回 `FIXED` 恢复统一账号+proxy |
| `adhoc.engine.STARROCKS.auth-mode` | String | `FIXED` | StarRocks 鉴权模式 | 生产置 `USER`（用户名=工号，密码仍取每实例 password）；切回 `FIXED` 恢复统一账号 |

**两种模式行为**：

| 模式 | Kyuubi | StarRocks |
|---|---|---|
| `FIXED`（默认） | 统一账号 user/password 直连 + `hive.server2.proxy.user`=job userId 代理 | 实例统一账号 user/password 直连 |
| `USER`（生产） | 用户名=访问用户工号，**无密码、无代理**（直连） | 用户名=访问用户工号，密码仍取每实例 `password`（统一密码） |

- `USER` 模式忽略每实例 `user`（Kyuubi 连 `password` 也忽略）；StarRocks 的 `password` 仍生效（各用户共用同一统一密码）。
- `USER` 模式下 job 缺 userId（工号）会快速失败抛 `ADHOC_USER_CONTEXT_MISSING`，不静默用 `Unknown` 兜底。
- 切换是一键的：`USER` ↔ `FIXED`，无字段迁移。元数据补全模块不受此开关影响（仍走自己的 metastore 直连账号）。

### 4.5 引擎多实例连接（无默认，必配；实例名作 key 中段）

> 每实例独立账号密码，**无引擎级回退、无代码默认凭据**，未配 endpoints 抛 `ADHOC_ENGINE_INSTANCE_NOT_FOUND`。JDBC scheme 固定：Kyuubi=`jdbc:hive2://`，StarRocks=`jdbc:mysql://`（非配置项）。proxyUser：FIXED 模式 Kyuubi=job 的 userId（空兜底 `Unknown`），USER 模式=null；StarRocks 始终=null。新增实例无需重启（首次 resolve 懒注册）。鉴权模式见 §4.4。

| 动态 Key | 引擎 | 必填 | 说明 |
|---|---|---|---|
| `adhoc.engine.KYUUBI.{instance}.endpoints` | KYUUBI | 是 | host:port 列表（逗号分隔，HA 共用同一组账号） |
| `adhoc.engine.KYUUBI.{instance}.user` | KYUUBI | 是 | JDBC 用户 |
| `adhoc.engine.KYUUBI.{instance}.password` | KYUUBI | 是 | JDBC 密码（Apollo 加密） |
| `adhoc.engine.STARROCKS.{instance}.endpoints` | STARROCKS | 是 | host:port 列表 |
| `adhoc.engine.STARROCKS.{instance}.user` | STARROCKS | 是 | 账号 |
| `adhoc.engine.STARROCKS.{instance}.password` | STARROCKS | 是 | 密码（Apollo 加密） |
| `adhoc.engine.STARROCKS.{instance}.database` | STARROCKS | 否 | 默认库（配了追加，不配不带） |
| `adhoc.engine.STARROCKS.{instance}.params` | STARROCKS | 否 | 连接参数（如 `useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai`） |

**示例（单实例 kyuubi-01 / starrocks-01）**：
```
adhoc.engine.KYUUBI.kyuubi-01.endpoints = xxxx:10009
adhoc.engine.KYUUBI.kyuubi-01.user = <kyuubi-user>
adhoc.engine.KYUUBI.kyuubi-01.password = <加密值>

adhoc.engine.STARROCKS.starrocks-01.endpoints = xxxx:9030
adhoc.engine.STARROCKS.starrocks-01.user = <sr-user>
adhoc.engine.STARROCKS.starrocks-01.password = <加密值>
adhoc.engine.STARROCKS.starrocks-01.params = useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai
```

### 4.6 executor 日志对账 `adhoc.log.*`（executor 侧）

| Key | 类型 | 默认值 | 说明 | 生产建议 |
|---|---|---|---|---|
| `adhoc.log.reconcile-scan-interval-ms` | Long | 10000 | LogBuffer 兜底对账扫描间隔 | 保持 10000；**须 < server 的 `adhoc.log.complete-grace-ms`(30s)** |
| `adhoc.log.finalize-delay-ms` | Long | 10000 | LogBuffer 终态补全延迟 | 保持 10000 |

### 4.7 共享参数（同 server 第 3.10 节，OSS 须一致）

> `adhoc.oss.*` / `adhoc.result.reuse-ttl-seconds` / `adhoc.status.interval-ms` 在 executor App 的 namespace 配一份，值与 server App 保持一致。详见 §3.10。

---

## 5. 生产调参建议（汇总）

1. **限流默认值偏宽松**：`max-pending-jobs-per-user` 和 `max-running-jobs-per-user` 默认（1000/3000）过大，单用户可压满集群。生产建议 per-user 远小于 global（如 per-user 50~100、global 500~2000），保证多用户公平。`max-running-jobs-per-server=300000` 默认过大，按单机容量调小到 1 万级。
2. **事件派发必须开**：`adhoc.schedule.event-trigger-enabled=true`（生产），否则纯轮询有 2s 延迟且 submit 后不及时。
3. **日志时序约束**：`adhoc.log.complete-grace-ms`(server, 30s) **必须大于** `adhoc.log.reconcile-scan-interval-ms`(executor, 10s)，否则 executor 兜底写终态标识前 server 提前判 complete，客户端漏读终态页。
4. **JVM 保留期与大盘窗口对齐**：`adhoc.jvm-metric.retention-hours=168`（7d）对齐大盘 7d 时间窗；调小则大盘 7d 窗口超期无数据。
5. **OSS 共享参数两 App 须一致**：`adhoc.oss.project-name` / `result-life` / `log-life` / `req-user-code` 在 server 和 executor 两个 namespace 必须相同，否则结果/日志落到不同 OSS 空间。
6. **密码加密**：`spring.datasource.password`、`adhoc.engine.*.password`、`adhoc.metadata.*.password` 均建议用 Apollo 的加密值功能，不在明文配置。
7. **实例名两侧对齐**：executor 的 `adhoc.engine.{ENGINE}.{instance}` 实例名 与 server 的 `adhoc.metadata.*.{instance}` 实例名须对齐（同逻辑集群同名），否则元数据补全查不到对应连接。
8. **apollo.meta DNS**：`http://xxxx:8080` 依赖内网 DNS，生产若不通需改 `META-INF/app.properties` 为实际 Apollo 地址并重新打包。
9. **大盘访问令牌**：`adhoc.dashboard.access-token` 生产**必填**。任意非空字符串即可（代码无长度/格式校验，仅做相等比对），建议较长的随机串防猜测，启用 `/dashboard/**` + `/api/metrics/**` 鉴权（默认空=裸奔）。浏览器首次访问 `https://host/dashboard/?token=xxx`，前端写 localStorage 后自动清 URL 并改用 `X-Dashboard-Token` 头。泄露后 Apollo 改值即秒级生效、无需重启。
10. **管理员工号**：`adhoc.admin.user-ids` 填运维人员工号（逗号分隔，对应 `user_id` 即网关 userCode），读类接口（job/detail、status、progress、task/detail、result、log）与取消接口（job/cancel）按"本人或管理员"放行，非本人非管理员返 `ADHOC_JOB_FORBIDDEN`。dashboard 带 token 访问、gRPC/SDK 调用视为系统级直接放行（不校验归属）。改值秒级生效。

---

## 6. 排查与注意事项

- **类型不匹配**：Apollo 里把 Long 项填成字符串或带单位（如 `5000ms`）会转换失败回退默认值。Long/Integer 项只填数字。
- **改 `app.properties` 需重新打包**：`app.id` / `apollo.meta` 在 `META-INF/app.properties`，改后重新构建 jar 才生效（不在 Apollo 热更新范围）。
- **`/api/config` 自检**：server 暴露 `GET /api/config`（`ConfigController`），返回当前生效的 ConfigItem 清单（key+默认+当前值），配完 Apollo 后可调用核对。`StartupConfigLogger` 也在启动日志打印生效配置。
- **无 `@RefreshScope` / 无 `@Value` 业务参数**：所有业务参数走 `ConfigHolder` 读活值，Apollo auto-update 即时生效。仅 4 个框架端口用 `@Value`（`server.port`/`grpc.server.port`，改需重启）。
- **多实例动态 key 不在 ConfigItem 清单**：引擎/元数据每实例参数是 `ConfigHolder.getString("adhoc.engine.{ENGINE}.{instance}.{field}", null)` 动态读，不在 `/api/config` 列出，需手动在 Apollo 配齐。

---

**附录：ConfigItem 完整集（4 个类）**
- `adhoc-common/.../config/AdhocCommonConfig.java` — 共享（oss/result/status），8 项
- `adhoc-server/.../config/AdhocServerConfig.java` — server 业务（limit/server/schedule/healthcheck/compensation/reconcile/cleanup/log/jvm-metric），22 项
- `adhoc-executor/.../config/AdhocExecutorConfig.java` — executor 业务（heartbeat/executor/engine-default/log），12 项
- `adhoc-metadata/.../config/AdhocMetadataConfig.java` — 元数据（hive-metastore/starrocks/cache），7 项 + 每实例动态 key

> 框架级端口 `@Value`：`server.port`(server 默认8080)、`grpc.server.port`(server 默认9090 / executor 默认9091)。
