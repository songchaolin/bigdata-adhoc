# FAQ 与术语表

常见问题速查。错误码全集见[接口文档 - 错误码](api/README.md#错误码)。

## FAQ

### 提交报"缺少用户身份信息"（ADHOC_USER_CONTEXT_MISSING）怎么办？

用户侧接口（`/api/**`）要求请求头 `X-Adhoc-User-Id`，该头由网关 / 反向代理注入（见[部署指南 - nginx 网关](deployment.md#nginx-网关与用户头注入)）。直接裸调端口会报此错。用 SDK 则自动携带，无需处理。

### 报"无权访问该 Job"（ADHOC_JOB_FORBIDDEN）？

读类与取消接口有归属校验：只有 Job 的**提交人本人**或**管理员**（配置 `adhoc.admin.user-ids`）可访问。换成本人 userId 调用，或联系管理员将自己加入管理员列表（Apollo 活读，改值秒级生效）。

### 取消 Job 是怎么生效的？为什么有时要等几秒？

取消走三重机制：

1. server 收到取消请求后置 `cancel_requested=1` 标志位；
2. executor **段间检查**：每个 Task 开始前检查该标志，发现即跳过剩余 Task 并标记 CANCELED；
3. **段内中断**：正在执行的 Task 由引擎执行器直接中断——Kyuubi 关闭连接底层 transport（OS 级 socket 中断，冷启动阻塞期也能打断），StarRocks 调用 `Statement.cancel()`。

所以如果 Job 正在跑某一段，取消通常立即生效；如果卡在引擎冷启动，Kyuubi 的 transport 中断也能打断。取消后 Job 最终状态为 `CANCELED`。

### 日志轮询什么时候可以停止？

按 `/api/job/log`、`/api/task/log` 返回的 `hasMore` / `complete` 判断：

| hasMore | complete | 动作 |
|---------|----------|------|
| `true` | - | 继续翻页（带 offset） |
| `false` | `false` | 日志未完整（Job 未终态），继续轮询 |
| `false` | `true` | 日志完整，停止轮询 |

详见[日志接口文档](api/log.md)。

### 报"结果数据已丢失"（ADHOC_RESULT_LOST）？

结果文件有保留期（默认 30 天，`adhoc.result.retention-days`），过期的结果会被 TTL 清理（local 存储自扫描清理 / OSS 走桶生命周期规则）。超期后重新提交 SQL 即可。另外 5 分钟内同用户重提交完全相同的 SQL 会命中**结果复用**（`sql_hash + userId + TTL 300s`），直接返回上次结果，不重跑引擎。

### 提交报"Job 提交数量超过限制"（ADHOC_JOB_LIMIT_EXCEEDED）？

两种可能：单次提交的 SQL 段数超过上限（`adhoc.limit.max-tasks-per-job`，按 `;` 拆分计数，含 SET/USE），或 PENDING 状态的 Job 数量触达限流（per-user / 全局配额，`adhoc.limit.*` 配置组）。前者拆小 SQL 分批提交，后者等待已有 Job 出队或联系管理员调整限流配置。

### 报"检测到危险 SQL 语句，已被拦截"（ADHOC_SQL_DANGEROUS_STATEMENT）？

提交时做危险语句检查（代码注入 / 文件系统操作 / 权限操作 / 系统管理 / 库级数据销毁类），命中即整单拒绝，拦截明细写入 Job 日志。移除危险语句后重新提交。注意这是安全拦截，与 SQL 治理的"风险标记"（全表扫描、危险函数等，仅标记不阻断）不是一回事。

### 报"当前引擎不支持该 SQL 语句类型"（ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED）？

STARROCKS 引擎只放行查询类语句（DQL / CTAS / SESSION_CONFIG / AUX），DDL / DML / DCL 在提交前置校验即拦截。需要执行 DDL/DML（建表、INSERT 等）请使用 KYUUBI 引擎。

### 报"引擎实例不存在或未配置"（ADHOC_ENGINE_INSTANCE_NOT_FOUND）？

提交时指定的 `engineInstance` 在配置中没有对应条目（或该引擎类型下无任何实例）。实例名须与 executor 侧 `adhoc.engine.{ENGINE}.{instance}` 配置的 key 完全一致。不传 `engineInstance` 则使用该引擎的默认实例。

### 报"结果尚未完整生成"（ADHOC_RESULT_INCOMPLETE）？

结果读取要求 Task 已进入终态且结果文件写入完成。Job 还在 RUNNING 时查结果会报此错，先轮询 `getJobStatus` 到终态再取结果。

### 查询超时（ADHOC_QUERY_TIMEOUT）？

executor 对查询有超时保护：`adhoc.executor.query-timeout-sec`（默认 3600 秒，0 = 不限制），超时后 Task 标记 FAILED，`failReasonCategory=QUERY_TIMEOUT`。超长查询需联系管理员调大该配置或优化 SQL。

### Job 一直卡在某个状态怎么办？

正常链路中 DISPATCHING/RUNNING 有兜底机制：executor 心跳超时（30s）判定 DOWN 后，其上 RUNNING 的 Task 会被标记 FAILED 并重算 Job 状态；多 server 部署下还有对账（reconcile）扫描兜底孤儿 Job。若确实长期不动，可到监控大盘（`/dashboard`）用 SQL 控制台查询元数据库排障，常用 SQL 见 `sql/troubleshooting-queries.sql`。

### 访问 /dashboard 或 /api/metrics 报 401？

大盘开启了固定令牌鉴权：请求需带 `X-Dashboard-Token` 头（或 URL `?token=`），值与配置 `adhoc.dashboard.access-token` 一致。配置为空则不启用鉴权。通过 nginx 网关访问时还需将大盘路径加入网关白名单（见[部署指南](deployment.md)）。

## 术语表

| 术语 | 说明 |
|------|------|
| Job | 一次 SQL 提交的完整执行单元，可能包含多个 Task |
| Task（段） | Job 按 `;` 拆分后的单段可执行 SQL，顺序执行 |
| prefix SQL | SET / USE 类会话配置语句，executor 拆分时合并到下一个 Task 的前缀执行，不独立成 Task |
| sqlType | SQL 语句分类：DQL / DDL_CREATE / DDL_ALTER / DDL_DROP / DML_INSERT / DML_MODIFY / CTAS / SESSION_CONFIG / AUX（SHOW/DESCRIBE/EXPLAIN）/ DCL / UNKNOWN |
| failStage | Task 失败时所处的阶段：DISPATCH / SPLIT / EXECUTING / FETCHING / WRITING / OSS_UPLOAD |
| failReasonCategory | 失败原因归类：ENGINE_ERROR / QUERY_TIMEOUT / SKIPPED_DUE_TO_PRIOR_FAILURE（前序失败跳过）等 |
| COMPLETE 标识 | Job 日志末尾的完整性标记行，日志收集链路据此判定 `complete=true` |
| 归属校验 | 读类/取消接口仅允许 Job 提交人本人或管理员访问（`adhoc.admin.user-ids`） |
| 结果复用 | 5 分钟内同用户重提交相同 SQL（`sql_hash` 一致）直接复用上次结果文件，不重跑引擎 |
| engineType | 执行引擎类型：KYUUBI（Spark SQL，Hive JDBC 协议）/ STARROCKS（MySQL 协议） |
| engineInstance | 逻辑引擎集群实例名（如 kyuubi-01），每实例独立账号与 endpoint 列表，提交时可选指定 |
| executor 实例 | 实际执行 SQL 的 adhoc-executor 进程，通过心跳（5s）注册与存活维护 |
| Apollo 活读 | 业务配置经配置中心 Apollo 下发，修改秒级生效无需重启；优先级 Apollo > 环境变量 > 本地 yml > 代码默认值 |
| 状态枚举 | Job：PENDING → DISPATCHING → RUNNING → SUCCESS / PARTIAL_FAILED / FAILED / CANCELED；Task：PENDING / RUNNING / SUCCESS / FAILED / CANCELED |
