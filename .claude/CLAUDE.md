# 项目规约（CLAUDE.md）
> 本文件是 bigdata-adhoc 项目的核心规约文档。
> 每次与本项目相关的对话开始时，请先阅读本文件全文，并以此作为回答的基准。
> 最后更新：2026-08-03

---

## 目录

1. [项目概览](#1-项目概览)
2. [编码规范](#2-编码规范)
3. [数据库设计规范](#3-数据库设计规范)
4. [分层架构规范](#4-分层架构规范)
5. [异常处理与错误码规范](#5-异常处理与错误码规范)
6. [Git 提交规范](#6-git-提交规范)
7. [核心业务流程说明](#7-核心业务流程说明)
8. [已有设计文档索引](#8-已有设计文档索引)

---

## 1. 项目概览

### 1.1 项目定位

bigdata-adhoc 是面向数据开发人员的即席查询平台，支持 **KYUUBI**（Hive JDBC 协议）和 **STARROCKS**（MySQL JDBC 协议）两种执行引擎，提供 SQL 提交、解析、调度、执行、结果存储、日志查询的完整链路。

### 1.2 模块划分

```
bigdata-adhoc
├── adhoc-server         # 调度与任务分发（QueueWorker、JobLogCollector、RunningJobRegistry）
├── adhoc-executor       # SQL 执行引擎（JobExecutionRunner、KyuubiEngineExecutor、StarRocksEngineExecutor）
├── adhoc-common         # 公共组件（枚举、异常、工具类、DTO）
├── adhoc-dao            # 数据访问层（Entity、Mapper）
├── adhoc-storage        # 结果存储（OSS 上传、ResultSet 序列化）
├── adhoc-sql-parser     # SQL 解析与多段拆分
├── adhoc-protocol       # gRPC 协议定义
├── adhoc-observability  # 可观测性（指标、追踪）
├── adhoc-api-sdk        # Java SDK（gRPC + REST）
└── adhoc-dependencies   # 依赖管理 BOM
```

### 1.3 技术栈

- **Java 8** + **Spring Boot 2.5.6**
- **MyBatis-Plus**（Lambda 查询，禁用字符串列名）
- **gRPC**（server-executor 内部通信）
- **MySQL 8**（元数据存储，库名 `adhoc`）
- **OSS**（结果与日志存储，内置 local 与 aliyun OSS（aliyun-sdk-oss，SPI 可扩展））
- **Apollo**（动态配置中心）

### 1.4 核心设计原则

- **session 绑定 executor**：server 宕机不影响 RUNNING Job，只有 executor 宕机才影响
- **server 不接触数据**：executor 完全负责 Job 执行（拆分 + 调度 + 存储 + 状态）
- **CAS 抢占**：多 server 部署下无锁抢占，不引入分布式锁
- **user_id 隔离**：所有限制按 user 维度，含 user_id 的表冗余 user_name

---

## 2. 编码规范

### 2.1 包命名规范

统一使用 `io.gitee.songchaolin.adhoc.*`，按模块划分：

| 模块 | 包路径 |
|------|--------|
| server 业务层 | `io.gitee.songchaolin.adhoc.server.service` |
| server 调度层 | `io.gitee.songchaolin.adhoc.server.schedule` |
| server HA | `io.gitee.songchaolin.adhoc.server.ha` |
| executor 执行器 | `io.gitee.songchaolin.adhoc.executor.engine` |
| executor 运行器 | `io.gitee.songchaolin.adhoc.executor.runner` |
| 实体类 | `io.gitee.songchaolin.adhoc.dao.entity` |
| Mapper | `io.gitee.songchaolin.adhoc.dao.mapper` |

```java
// ✅ 正确示例
package io.gitee.songchaolin.adhoc.server.service;

// ❌ 错误示例
package com.example.service;  // 缺少 adhoc 层级
package com.adhoc.server;     // 缩写不完整
```

### 2.2 类命名规范

| 类型 | 命名规则 | 示例 |
|------|----------|------|
| Entity | 表名驼峰，无后缀 | `AdhocQueryJob`、`AdhocQueryTask` |
| Mapper | Entity + Mapper | `AdhocQueryJobMapper` |
| Service | 业务名 + Service | `JobService`、`JobQueryService` |
| Runner | 动作 + Runner | `JobExecutionRunner` |
| Executor | 引擎名 + EngineExecutor | `KyuubiEngineExecutor` |
| Enum | 业务含义，无后缀 | `JobStatus`、`TaskStatus`、`AdhocErrorCode` |
| DTO | 业务名 + Request/Response | `JobSubmitRequest`、`JobSubmitResponse` |
| Config | 模块名 + Config | `AdhocServerConfig`、`AdhocExecutorConfig` |

```java
// ✅ 正确示例
public class JobService { }
public class AdhocQueryJobMapper { }
public enum JobStatus { }

// ❌ 错误示例
public class JobServiceImpl { }    // 不用 Impl 后缀
public class JobManager { }        // 用 Service 不用 Manager
public class JobStatusEnum { }     // 枚举不加 Enum 后缀
```

### 2.3 方法命名规范

| 操作类型 | 方法前缀 | 示例 |
|----------|----------|------|
| 查询单个 | `get`/`find` | `getJobById`、`findPendingJob` |
| 查询列表 | `list`/`select` | `listRunningJobs`、`selectByUserId` |
| 保存/插入 | `save`/`insert`/`register` | `saveJob`、`registerCancelTarget` |
| 更新 | `update`/`mark`/`set` | `updateStatus`、`markSuccess`、`setLastPulledOffset` |
| 删除 | `delete`/`remove`/`clear` | `removeJob`、`clearJobLog` |
| 执行 | `execute`/`run`/`dispatch` | `executeQuery`、`runSegments`、`dispatchJob` |
| 校验 | `validate`/`check` | `validateSql`、`checkPendingLimits` |

```java
// ✅ 正确示例
public AdhocQueryJob getJobById(String jobId) { }
public void markSuccess(String taskId) { }
public void executeQuery(Connection conn, String sql) { }

// ❌ 错误示例
public AdhocQueryJob queryJobById(String jobId) { }  // 查询用 get/find，不用 query
public void updateSuccess(String taskId) { }         // 状态变更用 mark，不用 update
public void doQuery(Connection conn, String sql) { } // 执行用 execute，不用 do
```

### 2.4 常量与枚举规范

**常量**：全大写 + 下划线分隔，定义在常量类或配置类中

```java
// ✅ 正确示例
public class LogConstants {
    public static final String COMPLETE_MARKER_LINE = "[executor] [INFO] ===COMPLETE===";
}

public class AdhocServerConfig {
    public static final ConfigKey<Long> SCHEDULE_INTERVAL_MS = ConfigKey.longKey("adhoc.server.schedule.interval-ms", 2000L);
}

// ❌ 错误示例
public class LogConstants {
    public static final String completeMarkerLine = "...";  // 应全大写
}
```

**枚举**：使用 `name()` 方法存储到数据库，提供 `is()` 静态判断方法

```java
// ✅ 正确示例
public enum JobStatus {
    PENDING,
    DISPATCHING,
    RUNNING,
    SUCCESS,
    PARTIAL_FAILED,
    FAILED,
    CANCELED;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static boolean isTerminal(String value) {
        return SUCCESS.name().equals(value)
                || FAILED.name().equals(value)
                || PARTIAL_FAILED.name().equals(value)
                || CANCELED.name().equals(value);
    }
}

// 使用方式
if (JobStatus.isTerminal(job.getStatus())) { ... }
if (JobStatus.RUNNING.is(job.getStatus())) { ... }

// ❌ 错误示例
if (job.getStatus().equals("RUNNING")) { }  // 禁止字符串硬编码
```

### 2.5 日志规范

**核心要求**：
1. 使用 SLF4J（`LoggerFactory.getLogger`）
2. 日志前缀格式：`[模块] [级别] [job=X][task=Y] 消息内容`
3. 关键日志必须包含 jobId/taskId 上下文
4. 业务日志用 `jobLog.append()` 写入 Job 日志流

```java
// ✅ 正确示例
private static final Logger log = LoggerFactory.getLogger(JobExecutionRunner.class);

// 业务日志（写入 jobLog）
jobLog.append("[executor] [INFO] [job=" + jobId + "][task=" + taskId + "] submitted sql: " + sql);
jobLog.append("[executor] [ERROR] task " + taskId + " FAILED: " + e.getMessage());

// 服务端日志（运维/排查）
log.info("【Job开始】jobId={} 用户={} 引擎={}", jobId, req.getUserId(), req.getEngineType());
log.error("【Task失败】jobId={} taskId={} 错误={}", jobId, taskId, e.getMessage(), e);

// ❌ 错误示例
log.info("job started");                    // 缺少 jobId 上下文
log.info("[job=" + jobId + "] task done");  // 级别缺失
System.out.println("debug info");           // 禁止使用 System.out
```

**日志级别使用**：
- `log.error`：异常堆栈、严重错误（含第三个参数 `e`）
- `log.warn`：业务异常、降级、重试
- `log.info`：关键业务节点（开始、完成、调度、派发）
- `log.debug`：详细执行过程、开发调试信息

### 2.6 注释规范

**类注释**：说明职责、关键协作类、设计要点

```java
/**
 * Job 执行 runner（重构版）：编排 job/task 生命周期，DB 状态写/结果持久化/引擎路由/LIMIT 规整拆给协作类。
 * <ul>
 *   <li>结果：{@link TaskResultWriter}（serialize + OssClient.upload + summary + task meta）</li>
 *   <li>DB 状态：{@link JobStateWriter}/{@link TaskStateWriter}（集中 LambdaUpdateWrapper）</li>
 *   <li>引擎路由：{@link EngineSelector}；LIMIT 规整：{@link SqlLimitEnforcer}</li>
 * </ul>
 */
@Component
public class JobExecutionRunner { }
```

**方法注释**：说明职责、参数、返回值、异常

```java
/**
 * 取消当前 job：直接关 HiveConnection 底层 TTransport（反射，绕过 synchronized CloseSession），
 * OS 级 socket 中断阻塞的 execute（prefix 或主查询），使 runner catch 后 markCanceled。
 *
 * @param jobId Job ID
 */
@Override
public void cancel(String jobId) { }
```

**行内注释**：关键逻辑、复杂判断、业务规则

```java
// 代理用户上下文（仅 Kyuubi，eng.proxyUser!=null）：doAs 使 getCurrentUser()=proxyUser
// 非 Kerberos（simple/LDAP）下 hive-jdbc 不读 UGI subject，真正的 Kyuubi impersonation 由
// connect 设置 hive.server2.proxy.user 完成（见 KyuubiEngineExecutor.connect）
if (eng.proxyUser != null) {
    UserGroupInformation proxyUGI = UserGroupInformation.createRemoteUser(eng.proxyUser);
    proxyUGI.doAs((PrivilegedExceptionAction<Void>) () -> {
        runSegments(ctx);
        return null;
    });
}
```

---

## 3. 数据库设计规范

### 3.1 表命名规范

- 前缀：`adhoc_`
- 分隔符：下划线 `_`
- 全部小写

```sql
-- ✅ 正确示例
CREATE TABLE adhoc_query_job ( ... );
CREATE TABLE adhoc_query_task ( ... );
CREATE TABLE adhoc_executor_instance ( ... );

-- ❌ 错误示例
CREATE TABLE query_job ( ... );        -- 缺少 adhoc_ 前缀
CREATE TABLE adhocQueryJob ( ... );    -- 应使用下划线分隔
CREATE TABLE ADHOC_QUERY_JOB ( ... );  -- 应使用小写
```

### 3.2 字段命名规范

- 数据库：下划线分隔（`snake_case`）
- 实体类：驼峰命名（`camelCase`）
- MyBatis-Plus 自动映射

```sql
-- 数据库字段
CREATE TABLE adhoc_query_job (
    job_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    submit_time DATETIME NOT NULL,
    ...
);
```

```java
// 实体类字段
@Data
@TableName("adhoc_query_job")
public class AdhocQueryJob {
    @TableId(type = IdType.ASSIGN_UUID)
    private String jobId;
    private String userId;
    private Date submitTime;
    ...
}
```

### 3.3 索引命名规范

| 索引类型 | 命名规则 | 示例 |
|----------|----------|------|
| 普通索引 | `idx_表名_字段名` | `idx_user_status` |
| 唯一索引 | `uk_表名_字段名` | `uk_client_request_id` |
| 组合索引 | `idx_表名_字段1_字段2` | `idx_status_submit` |

```sql
-- ✅ 正确示例
CREATE TABLE adhoc_query_job (
    ...
    KEY idx_user_status (user_id, status),
    KEY idx_status_submit (status, submit_time),
    UNIQUE KEY uk_client_request_id (client_request_id)
);

-- ❌ 错误示例
KEY idx_user (user_id);                 -- 缺少表名简写
UNIQUE KEY uniq_client_request_id;      -- 应使用 uk_ 前缀
KEY index_status_submit;                -- 应使用 idx_ 前缀
```

### 3.4 SQL 编写规范

**核心要求**：
1. 使用 MyBatis-Plus Lambda 查询，禁止字符串列名
2. 复杂查询使用 XML mapper
3. 禁止字符串拼接 SQL（防注入）

```java
// ✅ 正确示例：Lambda 查询
List<AdhocQueryJob> jobs = jobMapper.selectList(
    new LambdaQueryWrapper<AdhocQueryJob>()
        .eq(AdhocQueryJob::getUserId, userId)
        .eq(AdhocQueryJob::getStatus, JobStatus.PENDING.name())
        .orderByAsc(AdhocQueryJob::getSubmitTime)
);

// ✅ 正确示例：Lambda 更新
jobMapper.update(null, new LambdaUpdateWrapper<AdhocQueryJob>()
    .eq(AdhocQueryJob::getJobId, jobId)
    .set(AdhocQueryJob::getStatus, JobStatus.CANCELED.name())
    .set(AdhocQueryJob::getFinishTime, new Date())
);

// ❌ 错误示例：字符串列名
List<AdhocQueryJob> jobs = jobMapper.selectList(
    new QueryWrapper<AdhocQueryJob>()
        .eq("user_id", userId)  // 禁止使用字符串列名
);
```

**XML Mapper 规范**：

**核心规则**：
1. **复杂查询必须使用 XML mapper**：多表关联、子查询、复杂条件
2. **简单单表查询可用注解**：`@Select`/`@Update`/`@Insert`，但仅限单表、无条件或仅主键条件
3. **禁止在 Mapper 接口中写复杂 SQL**：一律移到 XML 文件

```xml
<!-- ✅ 正确示例：带注释说明用途 -->
<!-- 取一个可调度的 PENDING Job（按提交时间 ASC；带 running 限流子查询 -->
<select id="selectOnePendingJobId" resultType="java.lang.String">
    SELECT job_id FROM adhoc_query_job j
    WHERE j.status='PENDING' AND j.is_deleted=0
      AND j.submit_time > DATE_SUB(NOW(), INTERVAL #{recentWindowHours} HOUR)
    ORDER BY j.submit_time ASC LIMIT 1
</select>

<!-- 使用 #{} 防注入，禁止 ${} 拼接 -->
<select id="selectById" resultType="...">
    SELECT * FROM adhoc_query_job WHERE job_id = #{jobId}
</select>

<!-- 结果复用：查询最近成功的同 SQL Task -->
<select id="selectSuccessTaskBySqlHash" resultType="java.lang.String">
    SELECT query_id FROM adhoc_query_task
    WHERE user_id = #{userId}
      AND sql_hash = #{sqlHash}
      AND status = 'SUCCESS'
      AND finish_time >= DATE_SUB(NOW(), INTERVAL #{ttlSeconds} SECOND)
      AND is_deleted = 0
    ORDER BY finish_time DESC
    LIMIT 1
</select>
```

**Mapper 接口规范**：

```java
// ✅ 正确示例：接口只定义方法签名
public interface AdhocQueryTaskMapper extends BaseMapper<AdhocQueryTask> {
    /** 结果复用：查询最近成功的同 SQL Task */
    String selectSuccessTaskBySqlHash(@Param("userId") String userId,
                                      @Param("sqlHash") String sqlHash,
                                      @Param("ttlSeconds") int ttlSeconds);
}

// ❌ 错误示例：在接口中写复杂 SQL
public interface AdhocQueryTaskMapper extends BaseMapper<AdhocQueryTask> {
    @Select("SELECT query_id FROM adhoc_query_task WHERE ...")  // 禁止在接口中写复杂 SQL
    String selectSuccessTaskBySqlHash(...);
}
```

### 3.5 关键表字段说明

#### adhoc_query_job（Job 主表）

| 字段 | 类型 | 说明 |
|------|------|------|
| job_id | VARCHAR(64) | Job ID（UUID），主键 |
| user_id | VARCHAR(64) | 提交人 ID |
| user_name | VARCHAR(64) | 提交人中文名（冗余） |
| sql_content | TEXT | 原始 SQL（拆分前） |
| engine_type | VARCHAR(16) | KYUUBI/STARROCKS |
| status | VARCHAR(16) | PENDING/DISPATCHING/RUNNING/SUCCESS/PARTIAL_FAILED/FAILED/CANCELED |
| executor_instance | VARCHAR(128) | 执行 Job 的 executor instance_id |
| cancel_requested | TINYINT | 取消请求标志位 |
| processing_server_instance | VARCHAR(128) | 承接 Job 的 server instance_id |
| submit_time | DATETIME | Job 提交时间 |
| dispatch_time | DATETIME | dispatchJob 下发时间 |
| finish_time | DATETIME | Job 终态时间 |
| persistent_log_path | VARCHAR(256) | job log OSS key |

#### adhoc_query_task（Task 主表）

| 字段 | 类型 | 说明 |
|------|------|------|
| query_id | VARCHAR(64) | Task ID（UUID），主键 |
| job_id | VARCHAR(64) | 所属 Job |
| segment_index | INT | Job 内序号 |
| sql_content | TEXT | 单段可执行 SQL |
| sql_type | VARCHAR(16) | DQL/DDL/DML/CTAS/AUX 等 |
| status | VARCHAR(16) | PENDING/RUNNING/SUCCESS/FAILED/CANCELED |
| fail_stage | VARCHAR(16) | DISPATCH/SPLIT/EXECUTING/FETCHING/WRITING/OSS_UPLOAD |
| fail_reason_category | VARCHAR(64) | ENGINE_ERROR/SKIPPED_DUE_TO_PRIOR_FAILURE 等 |
| error_code | VARCHAR(64) | ADHOC_* 前缀错误码 |
| error_message | TEXT | 失败详情 |

#### adhoc_executor_instance（Executor 实例表）

| 字段 | 类型 | 说明 |
|------|------|------|
| instance_id | VARCHAR(128) | executor instance_id |
| host | VARCHAR(64) | 主机 IP |
| grpc_port | INT | gRPC 端口 |
| status | VARCHAR(16) | UP/DOWN |
| accepting | TINYINT | 是否接受新任务 |
| engine_types | VARCHAR(256) | 支持的引擎类型（逗号分隔） |
| running_tasks | TEXT | 在跑任务 ID 的 JSON 数组 |
| max_concurrent_tasks | INT | 最大并发任务数 |
| heartbeat_time | DATETIME | 最后心跳时间 |

---

## 4. 分层架构规范

### 4.1 Controller 层职责

**职责**：参数校验（`@Valid`）、调用 Service、返回统一响应

**规范**：
- 不写业务逻辑
- 不直接调用 Mapper
- 使用 `@Valid` 校验请求参数
- 返回 DTO 对象（由 `AdhocResponseAdvice` 统一包裹为 `Result`）

```java
// ✅ 正确示例
@RestController
@RequestMapping("/api/v1/job")
public class JobController {

    private final JobService jobService;

    @PostMapping("/submit")
    public JobSubmitResponse submit(@Valid @RequestBody JobSubmitRequest req,
                                    @RequestHeader("X-Adhoc-User-Id") String userId,
                                    @RequestHeader("X-Adhoc-User-Name") String userName) {
        return jobService.submit(req, userId, userName);
    }
}

// ❌ 错误示例：Controller 包含业务逻辑
@PostMapping("/submit")
public Result<JobSubmitResponse> submit(...) {
    if (req.getSqlContent() == null) {  // 参数校验应在 @Valid 或 Service 层
        return Result.error("SQL 不能为空");
    }
    AdhocQueryJob job = new AdhocQueryJob();  // 业务逻辑应在 Service 层
    job.setJobId(UUID.randomUUID().toString());
    jobMapper.insert(job);  // 禁止 Controller 直接调用 Mapper
    return Result.success(new JobSubmitResponse(job.getJobId()));
}
```

### 4.2 Service 层职责

**职责**：业务编排、事务管理、调用 Mapper 或其他 Service

**规范**：
- 一个 Service 类专注一个业务领域
- 事务注解 `@Transactional` 加在需要事务的方法上
- 异常通过 `AdhocException` 抛出，由全局异常处理器统一处理

```java
// ✅ 正确示例
@Service
public class JobService {

    private final AdhocQueryJobMapper jobMapper;
    private final QueueWorker queueWorker;

    public JobSubmitResponse submit(JobSubmitRequest req, String userId, String userName) {
        validate(req);                          // 参数校验
        checkPendingLimits(userId);             // 业务规则校验
        AdhocQueryJob job = createJob(req, userId, userName);
        jobMapper.insert(job);                  // 数据访问
        queueWorker.triggerDispatch();          // 触发调度
        return new JobSubmitResponse(job.getJobId());
    }

    private void validate(JobSubmitRequest req) {
        if (req.getEngineType() == null || req.getEngineType().isEmpty()) {
            throw new AdhocException(AdhocErrorCode.ADHOC_ENGINE_TYPE_REQUIRED);
        }
    }
}
```

### 4.3 Mapper 层职责

**职责**：仅 SQL 操作，不写业务逻辑

**规范**：
- 继承 `BaseMapper<T>`
- 复杂查询使用 XML mapper
- 禁止在 Mapper 中编写业务逻辑

```java
// ✅ 正确示例
public interface AdhocQueryJobMapper extends BaseMapper<AdhocQueryJob> {

    @Select("SELECT job_id FROM adhoc_query_job WHERE status='PENDING' ORDER BY submit_time ASC LIMIT 1")
    String selectOnePendingJobId(...);

    int claimJob(@Param("jobId") String jobId, @Param("serverInstanceId") String serverInstanceId);

    int revertToPending(@Param("jobId") String jobId);
}
```

### 4.4 分层依赖规则

**规则**：单向依赖，禁止跨层调用

```
Controller -> Service -> Mapper
           -> Service -> Service
```

```java
// ✅ 正确示例
Controller -> Service -> Mapper
Service -> Service（如 JobService -> QueueWorker）

// ❌ 错误示例
Controller -> Mapper        // 禁止跨层
Service -> Controller      // 禁止反向依赖
```

---

## 5. 异常处理与错误码规范

### 5.1 统一异常拦截

使用 `@RestControllerAdvice` 全局拦截异常：

```java
@RestControllerAdvice(basePackages = "io.gitee.songchaolin.adhoc.server.web")
public class AdhocResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(AdhocResponseAdvice.class);

    /** 业务异常：返回 FAIL + errorCode: message（业务预期，不打堆栈） */
    @ExceptionHandler(AdhocException.class)
    public Result<Void> handleAdhoc(AdhocException e) {
        log.warn("[adhoc business error] uri={} | {}", currentUri(), e.getMessage());
        return Result.error(ResultCodeEnum.FAIL.getCode(), e.getErrorCode().name() + ": " + e.getMessage());
    }

    /** @Valid 参数校验失败：返回 PARAMS_IS_INVALID + 字段错误明细 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getAllErrors().stream()
                .filter(err -> err instanceof FieldError)
                .map(err -> ((FieldError) err).getField() + " " + err.getDefaultMessage())
                .collect(Collectors.joining(";"));
        log.warn("[param invalid] uri={} | {}", currentUri(), detail);
        return Result.error(ResultCodeEnum.PARAMS_IS_INVALID.getCode(), ResultCodeEnum.PARAMS_IS_INVALID.getMsg() + ":" + detail);
    }

    /** 未知异常：打印堆栈 + 接口 URI 定位来源 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("[unexpected error] uri={} | {}", currentUri(), e.getMessage(), e);
        String msg = e.getMessage();
        String detail = e.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
        return Result.error(ResultCodeEnum.SYSTEM_INNER_ERROR.getCode(), detail);
    }
}
```

### 5.2 错误码枚举规范

> `defaultMessage` 统一用**面向用户的友好中文提示**；枚举名（`name()`）作为机器可读标识透传给前端/SDK。抛异常优先用 1-参构造（走 default），需附上下文时用 2-参构造传入更具体的中文消息。

```java
public enum AdhocErrorCode {
    ADHOC_JOB_TOO_MANY_TASKS("SQL 语句段数超过单次提交上限"),
    ADHOC_JOB_LIMIT_EXCEEDED("Job 提交数量超过限制，请稍后重试"),
    ADHOC_ENGINE_TYPE_REQUIRED("引擎类型不能为空"),
    ADHOC_ENGINE_TYPE_INVALID("引擎类型非法"),
    ADHOC_SQL_SYNTAX_ERROR("SQL 语法错误"),
    ADHOC_EXECUTOR_CRASHED("执行器已宕机，任务执行中断"),
    ADHOC_JOB_NOT_FOUND("Job 不存在或已失效"),
    ADHOC_SQL_DANGEROUS_STATEMENT("检测到危险 SQL 语句，已被拦截");

    private final String defaultMessage;

    AdhocErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
```

### 5.3 业务异常抛出规范

```java
// ✅ 正确示例
public void validate(JobSubmitRequest req) {
    if (req.getEngineType() == null || req.getEngineType().isEmpty()) {
        throw new AdhocException(AdhocErrorCode.ADHOC_ENGINE_TYPE_REQUIRED);
    }
    if (!EngineType.KYUUBI.is(req.getEngineType()) && !EngineType.STARROCKS.is(req.getEngineType())) {
        throw new AdhocException(AdhocErrorCode.ADHOC_ENGINE_TYPE_INVALID);
    }
    if (segments.size() > maxTasksPerJob) {
        throw new AdhocException(AdhocErrorCode.ADHOC_JOB_LIMIT_EXCEEDED,
                "SQL 段数超限: " + segments.size() + " > max-tasks-per-job=" + maxTasksPerJob);
    }
}

// ❌ 错误示例
throw new RuntimeException("引擎类型不能为空");  // 禁止使用 RuntimeException
throw new AdhocException("引擎类型不能为空");     // 必须使用 AdhocErrorCode
```

### 5.4 日志记录规范

**异常必须记录堆栈**：

```java
// ✅ 正确示例
log.error("【Job异常】jobId={} 错误={}", jobId, t.getMessage(), t);

// ❌ 错误示例
log.error("Job 异常: " + e.getMessage());  // 缺少堆栈参数
```

---

## 6. Git 提交规范

### 6.1 Commit Message 格式

```
<type>: <subject>

[optional body]

Co-Authored-By: Claude <noreply@anthropic.com>
```

**type 类型**：

| 类型 | 说明 | 示例 |
|------|------|------|
| feat | 新功能 | `feat: 新增 Job 取消功能` |
| fix | Bug 修复 | `fix: 修复 Kyuubi cancel 失效问题` |
| docs | 文档更新 | `docs: 补充 Job 执行流程文档` |
| style | 代码格式（不影响逻辑） | `style: 统一日志格式` |
| refactor | 重构（不是新功能也不是修复） | `refactor: 抽取 JobStateWriter` |
| test | 测试用例 | `test: 添加 JobService 单测` |
| chore | 构建/工具变动 | `chore: 升级 Spring Boot 版本` |

```bash
# ✅ 正确示例
git commit -m "feat: 新增 StarRocks 引擎支持"
git commit -m "fix: 修复 executor 宕机后 job 状态不一致问题"
git commit -m "refactor: 抽取 TaskResultWriter 统一结果写入"

# ❌ 错误示例
git commit -m "修复bug"           # 缺少 type 前缀
git commit -m "fix bug"           # 缺少冒号
git commit -m "fix:修复问题"      # 冒号后缺少空格
```

### 6.2 分支命名规范

| 分支类型 | 命名规则 | 示例 |
|----------|----------|------|
| 功能分支 | `feature/xxx` | `feature/starrocks-engine` |
| 修复分支 | `fix/xxx` | `fix/cancel-timeout` |
| 热修复分支 | `hotfix/xxx` | `hotfix/executor-crash` |
| 发布分支 | `release/xxx` | `release/v1.2.0` |

### 6.3 PR/MR 规范

**标题格式**：`[模块] 简要描述`

**描述内容**：
1. **变更背景**：为什么需要这个变更
2. **变更内容**：做了哪些修改
3. **测试情况**：如何测试、测试结果
4. **影响范围**：可能影响的模块/功能

```markdown
## 变更背景
Kyuubi cancel 在 prefix 阻塞期失效，需要通过关 transport 实现 OS 级中断。

## 变更内容
- 新增 `closeTransportDirectly` 方法，反射关闭 HiveConnection 底层 TTransport
- KyuubiEngineExecutor.cancel() 直接关 transport 而非依赖 Statement.cancel

## 测试情况
- 本地测试：prefix 期 cancel 成功中断
- 集成测试：主查询期 cancel 正常

## 影响范围
- adhoc-executor 模块
- Kyuubi 引擎取消功能
```

---

## 7. 核心业务流程说明

### 7.1 Job 执行链路

**关键类**：
- `QueueWorker.dispatcher()`：扫描 PENDING Job -> CAS 抢占 -> 派发到 executor
- `JobExecutionRunner.run()`：接收 dispatchJob 请求 -> 拆分 SQL -> 执行 Task -> 聚合结果
- `KyuubiEngineExecutor` / `StarRocksEngineExecutor`：引擎执行器

**流程描述**：

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 1. 提交阶段 (server)                                                     │
│    JobService.submit()                                                   │
│    ├── 参数校验（engine_type + SQL 非空 + STARROCKS 语句类型限制）        │
│    ├── 限流检查（PENDING 全局/per-user）                                  │
│    ├── 幂等检查（client_request_id）                                      │
│    ├── Job 入库（PENDING）                                                │
│    └── 触发调度 queueWorker.triggerDispatch()                            │
└─────────────────────────────────────────────────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────────────┐
│ 2. 调度阶段 (server)                                                     │
│    QueueWorker.dispatcher()                                              │
│    ├── selectOnePendingJobId()：查询可调度 Job（带限流子查询）             │
│    ├── claimJob()：CAS 抢占 PENDING -> DISPATCHING                        │
│    ├── 查询可用 executor（UP + accepting + 支持引擎）                     │
│    ├── tryDispatch()：逐个 ping + dispatchJob gRPC                       │
│    │   ├── 成功：executor 返回 accepted=true                              │
│    │   └── 失败：尝试下一个 executor（failover）                           │
│    └── 全失败：回退 PENDING                                               │
└─────────────────────────────────────────────────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────────────┐
│ 3. 执行阶段 (executor)                                                   │
│    JobExecutionRunner.run()                                              │
│    ├── 引擎选择：EngineSelector.select(engineType, userId)               │
│    ├── SQL 拆分：SqlScriptProcessor.process(sqlContent)                  │
│    ├── 创建 Task：createTasks() 批量插入                                  │
│    ├── 连接引擎：executor.connect(url, user, password, proxyUser)        │
│    ├── 注册取消目标：registerCancelTarget(jobId, conn)                   │
│    ├── 顺序执行 Task：                                                    │
│    │   runTask(i)                                                        │
│    │   ├── 检查 cancel_requested（段间 cancel）                           │
│    │   ├── 检查 priorFailed（失败传播，跳过下游 task）                     │
│    │   ├── 执行 prefix（SET/USE）                                         │
│    │   ├── 执行主查询：executeQuery(conn, jobId, sql, serverLogSink)     │
│    │   ├── 结果写入：taskResultWriter.write() -> OSS                     │
│    │   └── 更新 Task 状态：markSuccess/markFailed/markCanceled           │
│    └── 聚合 Job 结果：aggregateJob()                                      │
└─────────────────────────────────────────────────────────────────────────┘
```

**取消/跳过/失败传播机制**：

```
取消请求流程：
1. server 端：JobService.cancel() -> UPDATE cancel_requested=1
2. executor 端（段间检查）：
   - runTask() 每段前检查 isCancelRequested(jobId)
   - 发现 cancel_requested=1 -> markCanceled(taskId) -> 跳过剩余 task
3. executor 端（段内中断）：
   - Kyuubi：KyuubiEngineExecutor.cancel(jobId) -> closeTransportDirectly()
   - StarRocks：StarRocksEngineExecutor.cancel(jobId) -> Statement.cancel()
4. aggregateJob()：检查 cancel_requested -> Job 状态=CANCELED

失败传播流程：
1. Task A 执行失败 -> priorFailed=true, lastFailedTaskId=A
2. Task B 执行前检查 priorFailed
3. priorFailed=true -> markSkipped(taskId, lastFailedTaskId) -> 跳过不执行
4. Task C 同上，直到所有剩余 Task 被跳过
5. aggregateJob()：success=0, failed>0 -> Job 状态=FAILED
```

### 7.2 日志收集链路

**关键类**：
- `JobLogCollector.collect()`：定期拉取 executor 日志 -> 刷 OSS
- `LogBufferRegistry`：executor 端内存日志 buffer
- `OssClient`：OSS 上传

**流程描述**：

```
executor 端：
┌─────────────────────────────────────────────────────────────────────────┐
│ JobExecutionRunner.run()                                                 │
│ ├── 创建 LogBuffer：logBufferRegistry.putJobLog(jobId, jobLog)          │
│ ├── 执行过程：jobLog.append("[executor] [INFO] ...")                    │
│ └── finally：jobLog 留内存，等待 server fetchJobLog                      │
└─────────────────────────────────────────────────────────────────────────┘
                                  ↓
server 端（定期 2s）：
┌─────────────────────────────────────────────────────────────────────────┐
│ JobLogCollector.collect()                                                │
│ ├── 遍历活跃 Job：registry.activeJobIds()                                │
│ ├── ① 拉 executor job-log：                                              │
│ │   executorChannelPool.fetchJobLog(executor, jobId, offset)            │
│ │   -> registry.appendAll(jobId, newLines)                              │
│ ├── ② 终态：追加 COMPLETE_MARKER_LINE                                    │
│ ├── ③ 刷 OSS：uploadJobLog(jobId, jobLog.snapshot())                    │
│ │   -> jobMapper.update(persistentLogPath=key)                          │
│ └── ④ 终态：通知 executor 清理                                           │
│     executorChannelPool.clearJobLog(executor, jobId)                    │
└─────────────────────────────────────────────────────────────────────────┘
                                  ↓
                        OSS: yl-adhoc/logs/20260803/{jobId}/job.log
```

**日志补偿机制**：
- 正常情况：server 刷 OSS 成功 -> gRPC 通知 executor 清理
- 异常情况：server 刷 OSS 失败 -> executor 兜底补全（LogBufferRegistry 定期检查）

### 7.3 引擎连接与取消机制

**Kyuubi 引擎**：

```
连接：
KyuubiEngineExecutor.connect(url, user, password, proxyUser)
├── Properties props = new Properties()
├── props.setProperty("user", user)
├── props.setProperty("hive.server2.proxy.user", proxyUser)  // 代理用户
└── DriverManager.getConnection(url, props)

取消（关键设计）：
KyuubiEngineExecutor.cancel(jobId)
├── runningConnections.get(jobId)  // 获取 connection
├── closeTransportDirectly(jobId, conn)
│   ├── 反射获取 HiveConnection.transport 字段
│   ├── transport.close()  // OS 级 socket 中断
│   └── 绕过 synchronized CloseSession，避免被阻塞的 execute 持锁卡死
└── runner 捕获 SQLException -> markCanceled

为何不只用 Statement.cancel：
1. prefix（use db）是冷启动第一个操作、阻塞在引擎 session/Spark app 创建
2. 期间无 Statement 注册，Statement.cancel 拿不到 handle 无效
3. PENDING 期 Kyuubi 忽略 CancelOperation
4. 关 transport 是 OS 级 socket 中断，不依赖锁/handle，prefix 和主查询都能中断
```

**StarRocks 引擎**：

```
连接：
StarRocksEngineExecutor.connect(url, user, password, proxyUser)
└── DriverManager.getConnection(url, user, password)  // MySQL 协议

取消：
StarRocksEngineExecutor.cancel(jobId)
├── runningStatements.get(jobId)  // 获取 Statement
└── statement.cancel()  // MySQL JDBC 支持，中断 StarRocks 查询
```

---

## 8. 已有设计文档索引

### specs/2026-07-08-adhoc-redesign/

| 文档 | 用途 |
|------|------|
| `00-architecture-overview.md` | **总体架构**：模块划分、部署拓扑、设计原则、修订要点 |
| `01-task-scheduling-execution.md` | **调度与执行**：QueueWorker、JobExecutionRunner、Task 生命周期 |
| `02-sql-parsing-governance.md` | **SQL 解析与治理**：g4 解析、SqlType 分类、治理规则 |
| `03-engine-routing.md` | **引擎路由**：Kyuubi/StarRocks 选择、proxyUser 代理 |
| `04-result-storage.md` | **结果存储**：OSS 上传、ResultSet 序列化、存储路径规范 |
| `05-high-availability.md` | **高可用**：server/executor 宕机补偿、reconcile 机制 |
| `06-observability.md` | **可观测性**：指标、追踪、日志规范 |
| `08-directory-tree.md` | **目录结构**：完整项目目录树 |
| `09-execution-log.md` | **执行日志**：日志格式、收集机制、COMPLETE_MARKER |
| `10-limitations.md` | **限制与约束**：已知限制、二期规划 |
| `11-schema.md` | **数据库 Schema**：完整 DDL、索引说明 |
| `12-sdk.md` | **SDK 设计**：Java SDK 接口、gRPC/REST 协议 |
| `13-config-catalog.md` | **配置目录**：AdhocServerConfig、AdhocExecutorConfig 配置项 |

### 其他参考

| 路径 | 内容 |
|------|------|
| `sql/create.sql` | 完整建表 DDL（含索引、注释） |
| `superpowers/plans/` | 实施计划、MVP 交付计划 |
| `.claude/memory/` | 项目记忆（设计决策、已完成功能） |

---

## 附录：代码示例

### A. Service 标准模板

```java
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final AdhocQueryJobMapper jobMapper;
    private final QueueWorker queueWorker;

    public JobService(AdhocQueryJobMapper jobMapper, QueueWorker queueWorker) {
        this.jobMapper = jobMapper;
        this.queueWorker = queueWorker;
    }

    public JobSubmitResponse submit(JobSubmitRequest req, String userId, String userName) {
        // 1. 参数校验
        validate(req);

        // 2. 业务规则校验
        checkPendingLimits(userId);

        // 3. 幂等检查
        if (req.getClientRequestId() != null) {
            AdhocQueryJob existing = jobMapper.selectOne(new LambdaQueryWrapper<AdhocQueryJob>()
                    .eq(AdhocQueryJob::getClientRequestId, req.getClientRequestId()));
            if (existing != null) {
                return new JobSubmitResponse(existing.getJobId());
            }
        }

        // 4. 业务逻辑
        AdhocQueryJob job = new AdhocQueryJob();
        job.setJobId("Job_" + UUID.randomUUID().toString().replace("-", ""));
        job.setUserId(userId);
        job.setUserName(userName);
        job.setSqlContent(req.getSqlContent());
        job.setEngineType(req.getEngineType());
        job.setStatus(JobStatus.PENDING.name());
        job.setSubmitTime(new Date());

        // 5. 数据访问
        jobMapper.insert(job);

        // 6. 日志
        log.info("【提交】jobId={} 用户={} 引擎={}", job.getJobId(), userId, req.getEngineType());

        // 7. 触发后续动作
        queueWorker.triggerDispatch();

        return new JobSubmitResponse(job.getJobId());
    }

    private void validate(JobSubmitRequest req) {
        if (req.getEngineType() == null || req.getEngineType().isEmpty()) {
            throw new AdhocException(AdhocErrorCode.ADHOC_ENGINE_TYPE_REQUIRED);
        }
    }

    private void checkPendingLimits(String userId) {
        long pendingUser = jobMapper.selectCount(new LambdaQueryWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getUserId, userId)
                .eq(AdhocQueryJob::getStatus, JobStatus.PENDING.name()));
        if (pendingUser >= maxPendingPerUser) {
            throw new AdhocException(AdhocErrorCode.ADHOC_JOB_LIMIT_EXCEEDED,
                    "用户 PENDING Job 超限: " + pendingUser);
        }
    }
}
```

### B. Runner 标准模板

```java
@Component
public class JobExecutionRunner {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionRunner.class);

    private final AdhocQueryJobMapper jobMapper;
    private final EngineSelector engineSelector;
    private final LogBufferRegistry logBufferRegistry;

    public void run(DispatchJobRequest req) {
        String jobId = req.getJobId();
        log.info("【Job开始】jobId={} 用户={} 引擎={}", jobId, req.getUserId(), req.getEngineType());

        LogBuffer jobLog = new LogBuffer();
        logBufferRegistry.putJobLog(jobId, jobLog);

        try {
            // 1. 引擎选择
            EngineContext eng = engineSelector.select(req.getEngineType(), req.getUserId());

            // 2. SQL 拆分
            List<ProcessedSqlSegment> segments = eng.processor.process(req.getSqlContent());

            // 3. 创建 Task
            List<String> taskIds = createTasks(req, jobId, segments);

            // 4. 连接引擎
            Connection conn = eng.executor.connect(eng.url, eng.user, eng.password, eng.proxyUser);

            try {
                // 5. 注册取消目标
                eng.executor.registerCancelTarget(jobId, conn);

                // 6. 顺序执行
                for (int i = 0; i < segments.size(); i++) {
                    runTask(ctx, i);
                }
            } finally {
                eng.executor.unregisterCancelTarget(jobId);
                closeQuietly(conn, jobId);
            }

            // 7. 聚合结果
            aggregateJob(jobId, jobLog);

        } catch (Exception e) {
            log.error("【Job异常】jobId={} 错误={}", jobId, e.getMessage(), e);
            jobLog.append("[executor] [ERROR] job " + jobId + " failed: " + e.getMessage());
            markFailed(jobId);
        }
    }
}
```

---

**文档版本**：v1.0
**适用范围**：bigdata-adhoc 全模块
**维护者**：项目开发团队