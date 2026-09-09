# 模块 6：可观测性

> 本模块负责阶段耗时分析、监控指标、集群健康看板、错误码体系、资源采集、日志规范、Trace 链路、告警规则，保障平台运维可观测。

## 1. 概述

### 1.1 模块职责

- **阶段耗时分析**：基于 task 表时间戳定位瓶颈
- **监控指标**：server/executor 两套 Prometheus 指标集
- **集群健康看板**：REST API 暴露实例状态 + 运维操作
- **关键监控看板**：executor 健康、结果存储、Task 执行、HA 对账四类看板
- **错误码体系**：统一汇总各模块错误码
- **资源采集**：scan_rows/scan_bytes 加到 task 表，EngineResourceCollector SPI
- **日志规范**：日志统一在 executor，MDC + client_request_id 串联
- **Trace 链路**：client_request_id / trace_id 贯穿 SDK -> server -> executor -> 引擎
- **告警规则**：关键指标阈值分级告警
- **Grafana 大盘**（二期）

### 1.2 设计原则

- **无事件流水表**：task 表的 status/stage/fail_stage + 时间戳已够追溯，不建 adhoc_query_event
- **无资源统计表**：耗时从时间戳算，scan_rows/scan_bytes 加到 task 表，不建 adhoc_query_resource
- **label 基数可控**：按 user / engine_instance / instance / engine_type / sql_type / storage_type 维度 label，禁止 jobId/taskId/SQL 原文做 label
- **指标职责清晰**：server 指标只反映前置校验 + 调度 + 读路径 + HA；executor 指标反映拆分 + 执行 + 存储 + WAL/心跳对账
- **日志统一在 executor**：server 不产生 Job/Task 日志文件，只写实例运行日志

## 2. 阶段耗时分析

### 2.1 task 表时间戳

| 时间戳 | 阶段 | 写入方 |
|---|---|---|
| enqueue_time | task 入队（status=PENDING） | executor（拆分后创建 Task） |
| dequeue_time | executor 顺序调度抢占 | executor |
| parse_finish_time | SQL 语法解析完成 | server（前置校验阶段，记录到 Job 后随 dispatch 下发） |
| govern_finish_time | 治理完成（限流 + 段数 + engine 校验） | server |
| dispatch_time | 下发 executor（dispatchJob RPC） | server |
| start_time | executor 确认开始（session 创建后） | executor |
| fetch_start_time | 引擎执行完成，开始拉取结果 | executor |
| write_start_time | 拉取完成，开始序列化+上传存储 | executor |
| finish_time | 存储上传完成 / 终态 | executor |

### 2.2 耗时计算公式

```
排队耗时   = dequeue_time       - enqueue_time
解析耗时   = parse_finish_time  - dequeue_time（server 端 SQL 语法解析）
治理耗时   = govern_finish_time - parse_finish_time（限流 + 段数 + engine 校验）
调度耗时   = dispatch_time      - govern_finish_time（选 executor + dispatchJob）
等待确认   = start_time         - dispatch_time（executor 创建 session + 拆分 Job）
引擎执行   = fetch_start_time   - start_time
拉取结果   = write_start_time   - fetch_start_time
序列化+上传 = finish_time        - write_start_time（结果序列化 + 存储上传）
总耗时     = finish_time        - job.submit_time
```

### 2.3 失败阶段定位

失败时通过 `fail_stage` 字段记录失败阶段：

| fail_stage | 含义 |
|---|---|
| SQL_PARSE | g4 解析失败（SQL 语法错误） |
| PARSING | 解析阶段失败（非语法问题） |
| GOVERNING | 治理关卡失败（限流超限 / 段数超限 / engine_type 非法 / engine_params 非法） |
| SCHEDULING | 调度失败（无可用 executor） |
| RUNNING | 执行阶段失败（引擎报错，含引擎侧权限拒绝） |
| FETCHING | 拉取结果失败 |
| WRITING | 结果序列化或上传存储失败 |

权限拒绝统一作为 ENGINE_ERROR 处理（引擎层权限拦截）。

## 3. 监控指标

### 3.1 server 指标

**任务投递**：

```
adhoc_job_submit_total                         counter，Job 提交次数
adhoc_job_submit_failed_total                  counter，Job 提交失败次数（前置校验未通过）
adhoc_job_active{user}                         gauge，当前活跃 Job 数
adhoc_job_active_limit_exceeded_total          counter，Job 级并发超限拒绝次数
adhoc_task_submit_total                        counter，Task 提交次数（executor 拆分后回写）
adhoc_task_active{user}                        gauge，当前运行中 Task 数
adhoc_task_running_limit_exceeded_total        counter，Task 级并发超限回退次数
```

**队列与调度**：

```
adhoc_queue_waiting                            gauge，当前排队 Job 数
adhoc_queue_pick_total                         counter，Job 抢占次数
adhoc_parse_duration_seconds                   histogram，SQL 语法解析耗时
adhoc_governance_duration_seconds              histogram，治理阶段耗时（限流 + 段数 + engine 校验）
adhoc_schedule_duration_seconds                histogram，调度阶段耗时（选 executor + dispatchJob）
adhoc_task_cancel_total                        counter，Job/Task 取消次数
adhoc_task_timeout_total                       counter，超时次数
```

**SQL 解析与治理**：

```
adhoc_sql_type_total{sql_type}                         counter，按 SQL 类型计数（QUERY/SESSION_CONFIG 等）
adhoc_sql_syntax_error_total                           counter，SQL 语法错误次数
adhoc_job_no_executable_sql_total                      counter，Job 全是 SET/USE 拒绝次数
adhoc_engine_type_invalid_total                        counter，engine_type 非法拒绝次数
adhoc_engine_params_invalid_total                      counter，engine_params 非法拒绝次数
```

**结果读取与路由**：

```
adhoc_task_result_reused_total{user}             counter，结果复用命中次数
adhoc_result_read_duration_seconds               histogram，结果读取耗时（存储直读 + 分页）
adhoc_result_storage_read_total                   counter，server 直读存储次数
adhoc_result_storage_cleanup_total                counter，存储 TTL 过期清理文件数
```

**实例与高可用**：

```
adhoc_server_instance_up{instance}                       gauge，server 实例存活 0/1
adhoc_server_active_jobs{instance}                       gauge，本 server 活跃 Job 数
adhoc_server_heartbeat_total{instance}                   counter，server 心跳次数
adhoc_server_heartbeat_failed_total{instance}            counter，server 心跳写库失败次数
adhoc_server_heartbeat_timeout_detected_total            counter，HA 线程检测到其他 server 心跳超时次数
adhoc_server_job_switch_total                            counter，PENDING Job 切换 processing_server_instance 次数
adhoc_cluster_health_check_total                         counter，健康检查扫描次数
adhoc_cluster_instance_down_detected_total{type=server/executor}  counter，发现 DOWN 次数
adhoc_cluster_task_compensate_total{type=server/executor}        counter，宕机补偿 Task 数
adhoc_instance_cleanup_total{type=server/executor}               counter，过期实例清理数
adhoc_instance_down_pending_cleanup{type}                        gauge，待清理 DOWN 实例数
adhoc_executor_drain_total                               counter，优雅下线触发次数
```

### 3.2 executor 指标

**Task 执行**：

```
adhoc_executor_running_queries{instance}                 gauge，运行中 Task 数
adhoc_executor_accepted_total{instance}                  counter，接受的 Job 数
adhoc_executor_rejected_total{instance}                  counter，拒绝的 Job 数（队列满 / 资源不足）
adhoc_executor_engine_query_duration_seconds{engine_type}  histogram，引擎执行耗时
adhoc_executor_fetch_duration_seconds                    histogram，拉取结果耗时
adhoc_executor_storage_upload_duration_ms                histogram，上传存储耗时（含结果序列化）
adhoc_executor_cancel_total{instance}                    counter，取消次数
adhoc_executor_timeout_total{instance}                   counter，超时次数
```

**资源与水位**：

```
adhoc_engine_endpoint_up{engine_instance,endpoint}       gauge，引擎 endpoint 存活 0/1
adhoc_executor_instance_up{instance}                     gauge，executor 存活 0/1
adhoc_executor_load_score{instance}                      gauge，综合负载评分（含 disk_usage）
adhoc_executor_current_running{instance}                 gauge，运行中 Task 数
adhoc_executor_current_queued{instance}                  gauge，本地队列等待数
adhoc_executor_cpu_usage_pct{instance}                   gauge，JVM CPU
adhoc_executor_system_cpu_usage_pct{instance}            gauge，系统整体 CPU
adhoc_executor_memory_usage_pct{instance}                gauge，JVM 堆使用率
adhoc_executor_disk_usage_pct{instance}                  gauge，磁盘使用率（心跳上报，load_score 打分依据）
adhoc_executor_last_gc_pause_ms{instance}                gauge，最近 GC 暂停
```

**存储上传**：

```
adhoc_executor_storage_upload_success_total{instance}        counter，存储上传成功次数
adhoc_executor_storage_upload_failed_total{instance}         counter，存储上传失败次数
adhoc_executor_storage_upload_success_rate{instance}        gauge，上传成功率 = success / (success + failed)
adhoc_executor_storage_upload_retry_total{instance}        counter，上传重试总次数
adhoc_executor_upload_status{instance,status=PENDING/SUCCESS/FAILED}  gauge，按状态分布的 Task 数
```

**WAL 与心跳对账**：

```
adhoc_executor_wal_backlog{instance}                     gauge，本地 WAL 堆积文件数
adhoc_executor_wal_write_total{instance}                 counter，L4 写本地 WAL 次数
adhoc_executor_wal_commit_success_total{instance}        counter，WAL 提交成功次数
adhoc_executor_wal_commit_failed_total{instance}         counter，WAL 提交失败次数
adhoc_executor_l3_forward_total{instance}                counter，L3 接口转发次数（reportTaskStatus RPC）
adhoc_executor_l3_forward_failed_total{instance}         counter，L3 接口转发失败次数
adhoc_executor_heartbeat_total{instance}                 counter，心跳次数
adhoc_executor_heartbeat_failed_total{instance}          counter，心跳失败次数
adhoc_executor_false_down_total{instance}                counter，假 DOWN 次数（被标记 DOWN 后又恢复 UP）
adhoc_heartbeat_reconcile_task_lost_total                counter，server 通过心跳对账发现 TASK_LOST 次数
adhoc_heartbeat_reconcile_status_fix_total               counter，心跳对账修正 Task 状态次数（executor 写库失败兜底）
```

**结果存储分布**：

```
adhoc_task_storage_type_total{storage_type=NONE/PERSISTENT}  counter，按 storage_type 统计 Task 数
adhoc_task_result_status_total{result_status=WRITING/COMPLETE/INCOMPLETE}  counter，按 result_status 统计 Task 数
adhoc_task_fail_reason_total{fail_reason_category}  counter，按失败原因分类统计 Task 数
```

### 3.3 label 约束

**禁止**将 jobId、taskId、SQL 原文作为 Prometheus label：

- label 基数爆炸（每个 Task 一个 label，Prometheus 内存撑爆）
- SQL 原文有敏感信息

按 user / engine_instance / instance / engine_type / sql_type / storage_type / result_status / fail_reason_category 维度 label 是安全的（基数可控）。

## 4. 集群健康看板

### 4.1 看板 API

```
GET /api/adhoc/cluster/health
```

返回：

```json
{
  "server": {"UP": 3, "DOWN": 0, "UNKNOWN": 0},
  "executor": {"UP": 5, "DOWN": 1, "UNKNOWN": 0},
  "serverInstances": [
    {"instance": "server-1:8080", "status": "UP", "accepting": 1, "activeJobs": 2, "lastHeartbeatTime": "2026-07-14 10:00:05"}
  ],
  "executorInstances": [
    {
      "instance": "executor-1:9090",
      "status": "UP",
      "accepting": 1,
      "running": 8,
      "queued": 2,
      "loadScore": 45.5,
      "cpuUsage": 32.1,
      "memUsage": 58.2,
      "diskUsage": 67.0,
      "walBacklog": 0,
      "lastHeartbeatTime": "2026-07-14 10:00:05"
    }
  ]
}
```

### 4.2 运维操作 API

```
POST /api/adhoc/cluster/{type}/{instance}/drain    优雅下线（accepting=0）
POST /api/adhoc/cluster/{type}/{instance}/restore  恢复上线（accepting=1）
```

## 5. 关键监控看板

### 5.1 executor 健康看板

| 面板 | 指标 | 说明 |
|---|---|---|
| executor 状态分布 | `adhoc_executor_instance_up` | UP/DOWN 实例数 |
| 心跳延迟 | `NOW() - heartbeat_time` | 按实例分组，预警 10s 以上 |
| CPU 使用率 | `adhoc_executor_cpu_usage_pct` | 按实例分组 |
| 内存使用率 | `adhoc_executor_memory_usage_pct` | 按实例分组 |
| 磁盘使用率 | `adhoc_executor_disk_usage_pct` | 按实例分组，阈值线 80% / 90% |
| RUNNING Task 数 / 最大并发 | `adhoc_executor_current_running` | 与配置的 max_concurrent_tasks 对比 |
| WAL 堆积数 | `adhoc_executor_wal_backlog` | 按实例分组，阈值线 100 |
| 假 DOWN 次数 | `adhoc_executor_false_down_total` | 累计计数，频繁出现需调高心跳超时阈值 |

### 5.2 结果存储看板

| 面板 | 指标 | 说明 |
|---|---|---|
| storage_type 分布饼图 | `adhoc_task_storage_type_total` | NONE/PERSISTENT 占比 |
| result_status 分布 | `adhoc_task_result_status_total` | WRITING/COMPLETE/INCOMPLETE 占比 |
| 上传成功率趋势 | `adhoc_executor_storage_upload_success_rate` | 时间序列，阈值线 95% |
| 上传失败 Task 列表 | `result_summary where oss_upload_status=FAILED` | 含 task_id / error 详情 / instance |
| 上传重试次数 | `adhoc_executor_storage_upload_retry_total` | 累计计数，反映存储抖动情况 |
| TTL 过期清理次数 | `adhoc_result_storage_cleanup_total` | 反映存储长期清理节奏 |

### 5.3 Task 执行看板

| 面板 | 指标 | 说明 |
|---|---|---|
| Task 状态分布 | `adhoc_query_task.status` | PENDING/RUNNING/SUCCESS/FAILED 占比 |
| fail_reason_category 分布 | `adhoc_task_fail_reason_total` | ENGINE_ERROR/EXECUTOR_CRASHED/TASK_LOST/SKIPPED_DUE_TO_PRIOR_FAILURE/SKIPPED_DUE_TO_SESSION_LOSS 等 |
| Task 执行耗时 P50/P90/P99 | `adhoc_executor_engine_query_duration_seconds` | histogram_quantile |
| Job 状态分布 | `adhoc_query_job.status` | PENDING/DISPATCHING/RUNNING/SUCCESS/PARTIAL_FAILED/FAILED/CANCELED 占比 |
| 各阶段耗时分布 | 时间戳计算公式 | 解析 / 治理 / 调度 / 等待 / 引擎执行 / 拉取 / 写入 P50/P90 |
| 取消次数 | `adhoc_task_cancel_total` | 累计计数 |

### 5.4 HA 对账看板

| 面板 | 指标 | 说明 |
|---|---|---|
| 心跳对账 TASK_LOST 次数趋势 | `adhoc_heartbeat_reconcile_task_lost_total` | 时间序列，5min 内 > 10 次告警 |
| 心跳对账状态修正次数 | `adhoc_heartbeat_reconcile_status_fix_total` | executor 写库失败后由对账兜底修正的次数 |
| L3 接口转发次数趋势 | `adhoc_executor_l3_forward_total` | 时间序列，反映 executor -> server 状态转发频率 |
| L4 WAL 写入次数趋势 | `adhoc_executor_wal_write_total` | 时间序列，反映 DB 不可用频率 |
| WAL 提交成功率 | `adhoc_executor_wal_commit_success_total / (success + failed)` | 反映 WAL 后台重试效果 |
| server 宕机 Job 切换次数 | `adhoc_server_job_switch_total` | PENDING Job 切换 processing_server_instance 次数 |
| server 心跳超时检测次数 | `adhoc_server_heartbeat_timeout_detected_total` | 反映 server HA 线程活跃度 |
| 假 DOWN 次数 | `adhoc_executor_false_down_total` | 频繁出现需调高心跳超时阈值（30s -> 45s） |

## 6. 错误码体系

### 6.1 错误码分类

| 前缀 | 分类 | 示例 |
|---|---|---|
| `ADHOC_PARAM_*` | 参数校验 | `ADHOC_PARAM_INVALID`、`ADHOC_PARAM_USER_REQUIRED` |
| `ADHOC_QUEUE_*` | 排队控制 | `ADHOC_QUEUE_FULL`、`ADHOC_QUEUE_USER_LIMIT_EXCEEDED` |
| `ADHOC_JOB_*` | Job 级控制 | `ADHOC_JOB_TOO_MANY_TASKS`、`ADHOC_JOB_LIMIT_EXCEEDED`、`ADHOC_JOB_NO_EXECUTABLE_SQL` |
| `ADHOC_SQL_*` | SQL 治理 | `ADHOC_SQL_SYNTAX_ERROR`、`ADHOC_SQL_TYPE_NOT_ALLOWED`、`ADHOC_TABLE_NOT_QUALIFIED` |
| `ADHOC_ENGINE_*` | 引擎参数 | `ADHOC_ENGINE_TYPE_REQUIRED`、`ADHOC_ENGINE_TYPE_INVALID`、`ADHOC_ENGINE_PARAMS_INVALID`、`ADHOC_ENGINE_PARAM_NOT_ALLOWED`、`ADHOC_ENGINE_PARAM_OUT_OF_RANGE`、`ADHOC_ENGINE_PARAM_VALUE_INVALID`、`ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED` |
| `ADHOC_RESOURCE_*` | 资源 | `ADHOC_RESOURCE_LIMIT_EXCEEDED` |
| `ADHOC_SCHEDULE_*` | 调度 | `ADHOC_SCHEDULE_NO_EXEC_AVAILABLE` |
| `ADHOC_EXECUTOR_*` | 执行 | `ADHOC_EXECUTOR_CRASHED`、`ADHOC_SESSION_LOST` |
| `ADHOC_RESULT_*` | 结果 | `ADHOC_RESULT_NO_RESULT`、`ADHOC_RESULT_INCOMPLETE`、`ADHOC_RESULT_UPLOAD_PENDING`、`ADHOC_RESULT_UPLOAD_FAILED` |
| `ADHOC_LOG_*` | 日志 | `ADHOC_LOG_INCOMPLETE` |
| `ADHOC_SERVER_*` | server | `ADHOC_SERVER_CRASHED` |

### 6.2 错误码使用

- **REST API 返回**：`{code: "ADHOC_XXX", message: "...", detail: "..."}`
- **Task 失败回写**：`adhoc_query_task.error_code` + `error_message`
- **engine 报错（含引擎侧权限拒绝）**：统一标记 `fail_reason_category = ENGINE_ERROR`，`error_message` 为引擎报错详情

### 6.3 fail_reason_category vs error_code

| 字段 | 用途 | 取值 |
|---|---|---|
| `fail_reason_category` | executor 回写，server 据此判定 HA 补偿 / 跳过策略 | 11 类（ENGINE_ERROR / NETWORK_ERROR / FETCH_ERROR / WRITE_ERROR / EXECUTOR_UNAVAILABLE / EXECUTOR_CRASHED / TASK_LOST / SKIPPED_DUE_TO_PRIOR_FAILURE / SKIPPED_DUE_TO_SESSION_LOSS / TIMEOUT / UNKNOWN） |
| `error_code` | 详细错误码，REST 返回 + 任务表记录 | `ADHOC_*` 系列 |

`fail_reason_category` 是粗分类（HA 决策用），`error_code` 是细分类（排障用）。

| fail_reason_category | 触发场景 | HA 处理 |
|---|---|---|
| ENGINE_ERROR | 引擎报错（含权限拒绝、SQL 语义错误、表不存在等） | session 模式跳过后续 Task（SKIPPED_DUE_TO_PRIOR_FAILURE） |
| EXECUTOR_CRASHED | executor 心跳超时被标记 DOWN | 该 executor 上 RUNNING Task 全部 FAILED，PENDING Task 标记 SKIPPED_DUE_TO_SESSION_LOSS |
| TASK_LOST | 心跳对账发现 DB=RUNNING 但心跳里没有该 Task | 标记 FAILED |
| SKIPPED_DUE_TO_PRIOR_FAILURE | session 模式下前 Task FAILED，后续 Task 跳过 | Job 聚合为 PARTIAL_FAILED |
| SKIPPED_DUE_TO_SESSION_LOSS | executor DOWN，该 Job 的 PENDING Task 跳过 | Job 聚合为 PARTIAL_FAILED 或 FAILED |
| NETWORK_ERROR | 网络异常 | 重试 |
| FETCH_ERROR | 拉取结果失败 | 标记 FAILED |
| WRITE_ERROR | 结果序列化或上传存储失败 | 标记 FAILED |
| EXECUTOR_UNAVAILABLE | 无可用 executor | 等待 |
| TIMEOUT | 超时 | 标记 FAILED |
| UNKNOWN | 未知异常 | 标记 FAILED |

## 7. 资源采集

### 7.1 task 表新增字段

```sql
ALTER TABLE adhoc_query_task
  ADD COLUMN scan_rows BIGINT DEFAULT NULL COMMENT '引擎扫描行数（一期可空）',
  ADD COLUMN scan_bytes BIGINT DEFAULT NULL COMMENT '引擎扫描字节数（一期可空）';
```

耗时字段不单独存，从时间戳算。

### 7.2 EngineResourceCollector SPI

```java
public interface EngineResourceCollector {
    EngineType engineType();
    ResourceMetrics collect(Connection jdbcConn, String executionId);
}

public class ResourceMetrics {
    private Long scanRows;
    private Long scanBytes;
    private Map<String, String> extraCounters;  // 引擎特有 counter
}
```

**实现**：

- `KyuubiResourceCollector`：解析 Spark sessionMetrics
- `StarRocksResourceCollector`：解析 profile（二期补齐，一期可暂不采集）

scan_rows/scan_bytes 采集不到留 NULL，不阻断流程。

### 7.3 采集时机

executor 在 stage=FETCHING 完成后、上传存储前调用 ResourceCollector，结果由 executor 直写 DB（更新 task 表 scan_rows/scan_bytes）。若直写失败，走 L1-L4 + 心跳对账兜底（见模块 5）。

## 8. 日志规范

### 8.1 日志与结果存储位置

结果和日志统一存对象存储（StorageClient SPI），executor 不写本地文件：

```
存储（result/ 与 log/ 前缀）
  ├── result/{jobId}/{taskId}/result.part-0    ← Task 结果（executor 一次性 uploadResult）
  ├── log/{jobId}/{taskId}/task.log            ← Task 日志（定期 flush 快照 + 终态 flush）
  └── log/{jobId}/job.log                      ← Job 日志（终态一次性 uploadLog）
```

存储对象 TTL 由后端实现控制（local 自扫描 / aliyun 桶生命周期规则），见模块 4 §14。

### 8.2 日志文件内容

| 文件 | 产生方 | 内容 |
|---|---|---|
| job.log | executor | session 创建、Job 拆分、Task 调度顺序、Task 状态变更、session 关闭 |
| task.log | executor | 引擎连接、SQL 执行、结果拉取、引擎报错 |

**server 端不产生 Job/Task 日志文件**：前置校验失败直接返回错误给前端，不创建 Job 记录。server 只写实例运行日志（前置校验失败、Job 调度、HA 补偿、读路径（结果存储直读 / 日志 FetchLog 转发）等）。

### 8.3 日志格式

纯文本，每行一条：

```
2026-07-14 10:00:05.123 [INFO] [executor-1] Connecting to Kyuubi jdbc:kyuubi://...
2026-07-14 10:00:10.456 [INFO] [executor-1] Executing SQL: SELECT * FROM ...
2026-07-14 10:00:30.789 [WARN] [executor-1] Slow query detected, elapsed=20s
2026-07-14 10:00:35.012 [INFO] [executor-1] Fetched 1000 rows
```

格式：`{yyyy-MM-dd HH:mm:ss.SSS} [{level}] [{thread}] {message}`

### 8.4 MDC 标记

每个 Task 处理线程注入 MDC：

```java
MDC.put("clientRequestId", job.getClientRequestId());
MDC.put("traceId", traceId);
MDC.put("taskId", task.getQueryId());
MDC.put("jobId", task.getJobId());
MDC.put("userId", task.getUserId());
MDC.put("executorInstance", executorInstanceId);
```

日志输出格式包含 clientRequestId / taskId，便于按 Task 串联全链路日志。

### 8.5 实例运行日志

- **server**：按实例落 `logs/adhoc-server.log`，按天滚动。内容：前置校验失败、Job 调度、HA 补偿、读路径（结果存储直读 / 日志 FetchLog 转发）、心跳检测等
- **executor**：按实例落 `logs/adhoc-executor.log`，按天滚动。内容：dispatchJob 接收、session 管理、WAL 写入、心跳等运行事件

### 8.6 实时读取

executor 内存 buffer（`List<String>` per Task，按行）+ `ReentrantLock`，gRPC `FetchLog` 读 buffer 返回（从 offset 行起），实时性 ~100ms。定期（2s / 100 行阈值）把 buffer 全量序列化 -> `storageClient.uploadLog` 覆盖 logKey 快照；Task 终态最后 flush 一次完整日志。executor 宕机时 server 读存储快照（丢最后 ≤2s，不全丢）。

### 8.7 日志级别

- ERROR：终态失败、异常未恢复
- WARN：降级触发、重试、超时、上传失败、WAL 写入
- INFO：状态迁移、关键事件（提交 / 完成 / 取消 / session 创建 / session 关闭）
- DEBUG：详细调试（解析结果、治理决策、调度选择、引擎返回）

## 9. Trace 链路

### 9.1 链路 ID

| ID | 作用 | 生成方 | 贯穿范围 |
|---|---|---|---|
| client_request_id | 用户请求唯一标识 | SDK（UUID） | SDK -> server -> executor -> 引擎 |
| trace_id | 跨服务调用追踪 | server（接收请求时生成，若 SDK 未传） | server -> executor -> 引擎 |

**client_request_id 是主链路 ID**，贯穿全流程，写入 task 表（通过 job.client_request_id 传递到 executor）。

### 9.2 Job 生命周期事件

```
submit -> validate -> dispatch -> split -> execute -> fetch -> write -> upload -> finish
```

| 事件 | 触发方 | 记录位置 |
|---|---|---|
| submit | SDK | server 实例日志 + adhoc_query_job.submit_time |
| validate | server | server 实例日志（前置校验） |
| dispatch | server | server 实例日志 + adhoc_query_job.dispatch_time |
| split | executor | executor job.log + adhoc_query_task 创建 |
| execute | executor | executor task.log + adhoc_query_task.start_time |
| fetch | executor | executor task.log + adhoc_query_task.fetch_start_time |
| write | executor | executor task.log + adhoc_query_task.write_start_time |
| upload | executor | executor task.log + result_summary.oss_upload_status |
| finish | executor | executor job.log + adhoc_query_task.finish_time |

### 9.3 链路查询

按 client_request_id 查询：

1. server 实例日志（grep client_request_id）- 前置校验、调度
2. executor job.log（grep client_request_id）- session、拆分、状态变更
3. executor task.log（grep client_request_id）- 引擎执行、结果拉取
4. DB（adhoc_query_job / adhoc_query_task where client_request_id=?）- 状态 + 时间戳

## 10. 告警规则

### 10.1 关键告警

| 告警名 | 条件 | 级别 | 说明 |
|---|---|---|---|
| ExecutorDown | `adhoc_executor_instance_up == 0` for 30s | P1 | executor DOWN，触发 HA 补偿 |
| ServerDown | `adhoc_server_instance_up == 0` for 15s | P1 | server DOWN，触发 Job 切换 |
| ExecutorDiskUsageCritical | `adhoc_executor_disk_usage_pct > 90` for 1m | P1 | 磁盘水位高，可能影响 WAL 写入与 executor 运行 |
| ExecutorDiskUsageWarning | `adhoc_executor_disk_usage_pct > 80` for 5m | P2 | 磁盘水位预警 |
| StorageUploadSuccessRateLow | `adhoc_executor_storage_upload_success_rate < 0.95` for 5m | P2 | 上传成功率低 |
| WalBacklogHigh | `adhoc_executor_wal_backlog > 100` | P2 | WAL 堆积，DB 长时间不可达 |
| HeartbeatReconcileTaskLostSpike | `rate(adhoc_heartbeat_reconcile_task_lost_total[5m]) > 10` | P2 | 心跳对账 TASK_LOST 频繁，executor 异常退出多 |
| ResultIncompleteSpike | `rate(adhoc_task_result_status_total{result_status="INCOMPLETE"}[5m]) > 5` | P2 | 结果不完整增多，executor DOWN 期间未写完 |
| HeartbeatFailSpike | `rate(adhoc_executor_heartbeat_failed_total[5m]) > 1` | P2 | 心跳失败激增 |
| QueueBacklog | `adhoc_queue_waiting > 1000` for 5m | P2 | 排队积压 |
| FalseDownSpike | `rate(adhoc_executor_false_down_total[5m]) > 1` | P3 | 假 DOWN 频繁，建议调高心跳超时阈值 |
| TaskFailRateHigh | `rate(adhoc_task_fail_reason_total[5m]) / rate(adhoc_task_submit_total[5m]) > 0.3` | P2 | 5 分钟内失败率 > 30% |

### 10.2 告警通知

对接告警系统（邮件/IM），具体通道由运维配置。P1 告警触发 IM + 电话，P2 告警触发 IM，P3 告警触发邮件。

## 11. 与其他模块的接口

- **模块 1**：task 表的时间戳 + fail_stage + scan_rows/scan_bytes 作为可观测数据源；executor 拆分 + 顺序执行的调度事件由本模块采集
- **模块 2**：SQL 解析、限流、段数检查、engine_type/params 校验的指标由本模块采集
- **模块 3**：engine_type 选择（KYUUBI/STARROCKS）的指标由本模块采集
- **模块 4**：结果存储的 storage_type 分布、上传成功率、存储清理的指标由本模块采集
- **模块 5**：心跳、宕机、补偿、对账、WAL、L3 转发、Job 切换的指标由本模块采集

## 12. 验收标准

1. task 表时间戳完整记录，耗时公式可算各段耗时
2. fail_stage 记录失败阶段（7 种），定位问题环节
3. fail_reason_category 记录 11 类粗分类（含 TASK_LOST / SKIPPED_DUE_TO_PRIOR_FAILURE / SKIPPED_DUE_TO_SESSION_LOSS）
4. server/executor 两套 Prometheus 指标，label 基数可控（无 jobId/taskId/SQL 原文）
5. server 指标不含鉴权、server 端结果写入、engine_attempt 相关
6. executor 指标包含磁盘水位、上传成功率、WAL 堆积、L3/L4 转发等
7. 集群健康看板 API 暴露实例状态（含 disk_usage / wal_backlog）+ drain/restore 运维操作
8. 4 类关键监控看板（executor 健康 / 结果存储 / Task 执行 / HA 对账）覆盖核心场景
9. 错误码体系统一汇总，`ADHOC_*` 前缀分类
10. scan_rows/scan_bytes 加到 task 表，EngineResourceCollector SPI 采集（Kyuubi / StarRocks），一期可空
11. 不建 adhoc_query_event 表，不建 adhoc_query_resource 表
12. 日志统一在 executor（job.log / task.log），server 不产生 Job/Task 日志文件
13. MDC 注入 client_request_id / trace_id / taskId / jobId / userId，按 Task 串联全链路
14. Trace 链路：client_request_id 贯穿 SDK -> server -> executor -> 引擎，Job 生命周期 9 个事件可追溯
15. 告警规则覆盖关键场景（宕机 / 磁盘水位 / 上传 / WAL 堆积 / 心跳对账 / 假 DOWN），分 P1/P2/P3 三级
16. Grafana 大盘二期建设（基于 4 类关键监控看板扩展）
