# 模块 12：Java SDK

> 本文档定义平台的 SDK 与 RPC 接口设计，包含 Java SDK（HTTP REST 封装）与内部 RPC 接口（server 与 executor 之间的 gRPC 协议）。

## 1. 概述

### 1.1 模块职责

本文档定义平台的 SDK 与 RPC 接口设计，包含两部分：

- **Java SDK**：封装 HTTP REST 调用，提供面向 Java 用户的调用接口，独立打包为 adhoc-api-sdk jar
- **内部 RPC 接口**：server 与 executor 之间的 gRPC 协议定义，包括 dispatchJob、FetchLog、cancelJob（server -> executor）和 heartbeat、reportTaskStatus、reportJobStatus（executor -> server）

SDK 是用户接入平台的标准方式，独立打包，可被其他项目直接依赖。内部 RPC 接口是平台组件间的通信契约，不对外暴露。

### 1.2 设计原则

- **SDK 走 HTTP**：SDK 与 server 之间用 HTTP REST 通信，兼容 curl/Postman/浏览器场景，无需 gRPC 依赖
- **内部 RPC 走 gRPC**：server 与 executor 之间用 gRPC，强类型、高性能
- **proto 为内部契约源头**：gRPC .proto 文件定义 server/executor 间接口契约
- **轻量**：SDK 仅依赖 adhoc-common + HTTP 客户端 + jackson，不引入 server/executor 逻辑
- **线程安全**：AdhocClient 实例线程安全，可复用，建议单例
- **Builder 模式**：客户端构建用 Builder，请求参数用 Builder
- **统一异常**：所有错误抛 AdhocException，携带 error_code + message
- **可配置**：超时、重试、endpoint 通过 Builder 或配置文件
- **无状态**：SDK 不缓存 Job/Task 状态，每次调用都走 server

### 1.3 打包与依赖

**SDK 独立打包**：

```xml
<artifactId>adhoc-api-sdk</artifactId>
<packaging>jar</packaging>
```

**SDK 依赖关系**：

```
adhoc-api-sdk
    └── adhoc-common（枚举、异常、工具类）
    └── jackson-databind（JSON 序列化）
    └── okhttp / spring-web（HTTP 客户端，二选一）
```

SDK 不依赖 gRPC，不依赖 adhoc-protocol（proto 文件仅供 server/executor 内部使用）。

**内部 RPC 依赖**（server/executor 模块）：

```
adhoc-server / adhoc-executor
    └── adhoc-protocol（gRPC protobuf 生成类）
    └── grpc-netty-shaded（gRPC 传输）
    └── grpc-stub（gRPC stub）
```

**被其他项目依赖**：

```xml
<dependency>
    <groupId>io.gitee.songchaolin</groupId>
    <artifactId>adhoc-api-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

用户项目引入此依赖即可调用，无需依赖 server/executor。

**多语言 SDK**：

| 语言 | 生成方式 | 模块 |
|---|---|---|
| Java | 手写 HTTP 客户端 | adhoc-api-sdk（当前） |
| Python | OpenAPI 生成或手写 | adhoc-api-sdk-python（后续） |
| Go | OpenAPI 生成或手写 | adhoc-api-sdk-go（后续） |

## 2. 协议架构

### 2.1 协议分层

平台有三层通信：

| 通信路径 | 协议 | 端口 | 说明 |
|---|---|---|---|
| SDK -> server | HTTP REST | 8080 | 用户请求入口，JSON 交互 |
| server -> executor | gRPC | 9090 | Job 下发、日志拉取、取消（结果 server 直读存储） |
| executor -> server | gRPC | 9090 | 心跳、状态上报 |

```
┌──────────┐   HTTP REST    ┌──────────┐   gRPC         ┌──────────┐
│  SDK     │ ──────────────>│  server  │ ──────────────>│ executor │
│ (Java)   │                │ (8080)   │ <──────────────│ (9090)   │
└──────────┘                └──────────┘   gRPC         └──────────┘
                                 │                           │
                                 │       DB (MySQL)          │
                                 └───────────┬───────────────┘
                                             ▼
                                    ┌────────────────┐
                                    │ StorageClient  │
                                    └────────────────┘
```

### 2.2 server 端实现

server 对外暴露 HTTP REST 接口（8080），对 executor 用 gRPC 客户端（9090）：

```
SDK 请求 ──HTTP──> REST Controller (8080)
                       │
                       ▼
                JobService / TaskService（业务逻辑层）
                       │
                       ├──> adhoc-dao（DB 读写）
                       └──> gRPC Stub ──gRPC──> executor (9090)
```

server 不接触数据，不写日志/结果文件，不做 SQL 拆分（拆分在 executor），不做权限检查（依赖引擎侧权限）。

### 2.3 认证机制

SDK 通过 HTTP 头传递用户身份：

| HTTP 头 | 说明 | 示例 |
|---|---|---|
| `X-Adhoc-User-Id` | 用户 ID（必填） | `user123` |
| `X-Adhoc-User-Name` | 用户中文名（URL 编码，选填） | `%E5%BC%A0%E4%B8%89`（张三） |

**不做认证**：信任请求头 userId，不做 token 校验。鉴权依赖引擎层权限控制，server 不做权限检查。引擎执行时引擎侧拦截，无权限则引擎报错，executor 捕获后标记 Task FAILED（ENGINE_ERROR）。

### 2.4 用户身份来源

两种设置方式：

**方式一：全局默认（Builder 设置）**

```java
AdhocClient client = AdhocClient.builder()
    .endpoint("http://your-gateway:8080")
    .defaultUserId("user123")
    .defaultUserName("张三")
    .build();
```

所有请求默认用此 userId，单次请求可在 submitJob 时覆盖。

**方式二：单次请求覆盖**

```java
client.submitJob(SubmitJobRequest.builder()
    .userId("user456")
    .userName("李四")
    .sqlContent("SET spark.xxx=100; SELECT 1")
    .engineType(EngineType.KYUUBI)
    .build());
```

## 3. gRPC 协议设计（内部 RPC）

### 3.1 proto 文件位置

```
adhoc-protocol/
└── src/main/proto/
    └── internal/                        # server <-> executor 内部协议
        ├── common.proto                 # 公共类型
        ├── job_dispatch.proto           # server -> executor：dispatchJob / cancelJob
        ├── data_fetcher.proto           # server -> executor：FetchLog（FetchResult 已删，server 直读存储）
        ├── heartbeat.proto              # executor -> server：heartbeat
        └── status_report.proto          # executor -> server：reportTaskStatus / reportJobStatus
```

**全部内部协议**：proto 文件位于 `internal/` 目录，package 为 `adhoc.internal.v1`，不对外暴露。SDK 不依赖这些 proto，多语言 SDK 也不从这里生成。

### 3.2 common.proto（公共类型）

```protobuf
syntax = "proto3";

package adhoc.internal.v1;

option java_multiple_files = true;
option java_package = "io.gitee.songchaolin.adhoc.internal.grpc.v1";

// 引擎类型（只支持 KYUUBI / STARROCKS，无 HIVE）
enum EngineType {
  ENGINE_TYPE_UNSPECIFIED = 0;
  KYUUBI = 1;
  STARROCKS = 2;
}

// Job 状态（7 态）
enum JobStatus {
  JOB_STATUS_UNSPECIFIED = 0;
  PENDING = 1;
  DISPATCHING = 2;
  RUNNING = 3;
  SUCCESS = 4;
  PARTIAL_FAILED = 5;
  FAILED = 6;
  CANCELED = 7;
}

// Task 状态
enum TaskStatus {
  TASK_STATUS_UNSPECIFIED = 0;
  TASK_PENDING = 1;
  TASK_RUNNING = 2;
  TASK_SUCCESS = 3;
  TASK_FAILED = 4;
  TASK_REJECTED = 5;   // 保留备用，executor 拆分执行阶段不产生
  TASK_CANCELED = 6;
  TASK_TIMEOUT = 7;
}

// 存储类型（两态：NONE / PERSISTENT）
enum StorageType {
  STORAGE_TYPE_UNSPECIFIED = 0;
  NONE = 1;
  PERSISTENT = 2;
}

// 结果状态
enum ResultStatus {
  RESULT_STATUS_UNSPECIFIED = 0;
  WRITING = 1;
  COMPLETE = 2;
  INCOMPLETE = 3;
}

// 存储上传状态
enum OssUploadStatus {
  OSS_UPLOAD_UNSPECIFIED = 0;
  PENDING = 1;
  SUCCESS = 2;
  FAILED = 3;
}

// 统一错误
message Error {
  string error_code = 1;      // ADHOC_* 前缀
  string error_message = 2;
}

// 列定义
message ColumnSchema {
  string column_name = 1;
  string column_type = 2;
  int32 column_index = 3;
}
```

### 3.3 job_dispatch.proto（server -> executor：dispatchJob / cancelJob）

```protobuf
syntax = "proto3";

package adhoc.internal.v1;

import "internal/common.proto";

// Job 下发服务
service JobExecutor {
  // 下发整个 Job
  rpc dispatchJob(DispatchJobRequest) returns (DispatchJobResponse);
}

// Job 管理服务
service JobManager {
  // 取消整个 Job
  rpc cancelJob(CancelJobRequest) returns (CancelJobResponse);
}

message DispatchJobRequest {
  string job_id = 1;
  string user_id = 2;
  string user_name = 3;
  string sql_content = 4;         // 整个 Job 的 SQL 原文（含 SET/USE/SELECT）
  string engine_type = 5;         // KYUUBI / STARROCKS
  string engine_instance = 6;
  string engine_params = 7;       // JSON 字符串
  string client_ip = 8;
  string client_request_id = 9;   // 幂等键
}

message DispatchJobResponse {
  bool accepted = 1;              // executor 是否接受
  string error_message = 2;       // 拒绝原因
}

message CancelJobRequest {
  string job_id = 1;
  string reason = 2;              // 取消原因（用户取消 / 超时等）
}

message CancelJobResponse {
  bool cancelled = 1;             // 是否已取消
  string error_message = 2;
}
```

**dispatchJob 设计要点**：
- server 下发整个 Job 的 sql_content，executor 负责拆分（识别 SET/USE 作为前缀，合并到下一个真正执行的 Task）
- executor 接收后创建 JDBC session，按 segment_index 顺序执行 Task
- engine_type 必须显式指定（KYUUBI / STARROCKS）
- executor 拆分后若全是 SET/USE 无可执行 SQL，标记 Job FAILED（ADHOC_JOB_NO_EXECUTABLE_SQL）
- session 绑定 executor，server 宕机不影响 RUNNING Job，只有 executor 宕机才丢失 session

**cancelJob 设计要点**：
- 取消整个 Job，executor 停止后续 PENDING Task 的执行
- RUNNING Task：executor 杀引擎查询，60s 未确认 server 强制 CANCELED
- session 模式下，取消后关闭 session
- 取消是异步动作，API 返回后 Task 可能还在 RUNNING，需轮询 status 确认终态

### 3.4 data_fetcher.proto（server -> executor：FetchLog）

```protobuf
syntax = "proto3";

package adhoc.internal.v1;

import "internal/common.proto";

// 数据拉取服务（server 转发 SDK 日志请求到 executor；结果 server 直读存储不走此服务）
service DataFetcher {
  // 拉日志分页（读 executor 内存 buffer）
  rpc fetchLog(FetchLogRequest) returns (FetchLogResponse);
}

message FetchLogRequest {
  string target_id = 1;           // task_id 或 job_id
  int32 offset = 2;               // 行偏移
  int32 limit = 3;                // 拉取行数
  bool is_job_log = 4;            // true=读 job buffer, false=读 task buffer
  string trace_id = 5;            // 透传
}

message FetchLogResponse {
  bool hit = 1;                   // 内存 buffer 是否命中（false=buffer 不存在/已清理）
  repeated string lines = 2;      // 日志行
  int64 total_lines = 3;          // buffer 总行数（用于推算新 offset）
  bool finished = 4;              // Task/Job 是否终态
}
```

**FetchLog 设计要点**：
- target_id 拉取 Task 日志（task.log，is_job_log=false）或 Job 日志（job.log，is_job_log=true）
- executor 读内存 buffer（`List<String>` per Task/Job），实时性 ~100ms
- executor DOWN 或 Task 终态：server 直读存储快照（`storageClient.download(persistent_log_path)`）
- 日志未上传存储（executor 宕机且未 flush）返回 `ADHOC_LOG_INCOMPLETE`（丢最后 ≤2s）

**FetchResult 已删除**：结果 server 直读存储（`storageClient.download(persistent_path)` -> `AdhocResultReader` 分页），不再转发 executor。读路径由 storage_type + oss_upload_status 决定：
- storage_type = PERSISTENT 且 oss_upload_status = SUCCESS：server 直读存储
- storage_type = NONE：返回 ADHOC_RESULT_NO_RESULT
- oss_upload_status = PENDING：返回 ADHOC_RESULT_UPLOAD_PENDING
- oss_upload_status = FAILED：返回 ADHOC_RESULT_UPLOAD_FAILED

### 3.5 heartbeat.proto（executor -> server：heartbeat）

```protobuf
syntax = "proto3";

package adhoc.internal.v1;

import "internal/common.proto";

// 心跳服务（executor -> server）
service HeartbeatService {
  rpc heartbeat(HeartbeatRequest) returns (HeartbeatResponse);
}

message HeartbeatRequest {
  string instance_id = 1;                   // executor 实例 ID
  int64 heartbeat_time = 2;                 // 心跳时间（epoch millis）
  double cpu_usage = 3;                     // CPU 使用率
  double mem_usage = 4;                     // 内存使用率
  double disk_usage = 5;                    // 磁盘使用率
  repeated TaskStatusReport running_tasks = 6;  // 当前在执行的 Task 列表（对账用）
}

message TaskStatusReport {
  string task_id = 1;
  string status = 2;                        // RUNNING / SUCCESS / FAILED
  string stage = 3;                         // EXECUTING / FETCHING / WRITING
}

message HeartbeatResponse {
  bool success = 1;
  string server_id = 2;                     // 接收心跳的 server 实例 ID
  repeated string commands = 3;             // server 下发的命令（如 cancel）
}
```

**heartbeat 设计要点**：
- executor 每 5s 上报心跳，带上 running_tasks 列表（对账用）
- server 收到心跳后对账：
  - DB 中 RUNNING 的 Task 若不在 heartbeat.running_tasks 里，标记 FAILED（TASK_LOST）
  - DB 状态与心跳状态不一致时（如 DB=RUNNING，心跳=SUCCESS），以心跳为准更新 DB（executor 写库失败时兜底）
- 心跳超时 30s（6 次未收到），server 标记 executor DOWN
- server 下发 commands（如 cancel）通过 HeartbeatResponse 返回，executor 下次心跳时执行
- load_score 计算：`cpu_usage * 0.3 + mem_usage * 0.3 + disk_usage * 0.2 + running_tasks_ratio * 0.2`，调度时选最低分 executor

### 3.6 status_report.proto（executor -> server：reportTaskStatus / reportJobStatus）

```protobuf
syntax = "proto3";

package adhoc.internal.v1;

import "internal/common.proto";

// Task 状态上报服务（executor -> server，L3 接口转发）
service TaskStatusService {
  rpc reportTaskStatus(ReportTaskStatusRequest) returns (ReportTaskStatusResponse);
}

// Job 状态上报服务（executor -> server，L3 接口转发）
service JobStatusService {
  rpc reportJobStatus(ReportJobStatusRequest) returns (ReportJobStatusResponse);
}

message ReportTaskStatusRequest {
  string task_id = 1;
  string status = 2;                        // Task 状态
  string stage = 3;                         // 当前阶段
  string fail_reason_category = 4;          // 失败原因分类
  string error_code = 5;                    // 错误码
  string error_message = 6;                 // 错误详情
  int64 finish_time = 7;                    // 完成时间
  ResultSummary result_summary = 8;         // 结果摘要
}

message ReportTaskStatusResponse {
  bool success = 1;
  string error_message = 2;
}

message ResultSummary {
  string query_id = 1;                      // Task ID
  int64 result_rows = 2;                    // 结果总行数
  int64 result_bytes = 3;                   // 结果字节数
  string persistent_path = 4;               // 存储 key
  string storage_type = 5;                  // NONE / PERSISTENT
  string result_status = 6;                 // WRITING / COMPLETE / INCOMPLETE
  string oss_upload_status = 7;             // PENDING / SUCCESS / FAILED
  string oss_upload_error = 8;              // 上传失败原因
}

message ReportJobStatusRequest {
  string job_id = 1;
  string status = 2;                        // Job 状态
  string fail_reason = 3;                   // 失败原因
  int64 finish_time = 4;                    // 完成时间
  string executor_instance = 5;             // 执行 Job 的 executor
}

message ReportJobStatusResponse {
  bool success = 1;
  string error_message = 2;
}
```

**reportTaskStatus 设计要点**：
- 场景：executor 能连 server（gRPC）但连不上 DB，通过 server 代为更新 DB（L3 接口转发）
- 调用链：executor 直写 DB 失败（L1）-> 重试失败（L2）-> gRPC 调 server.reportTaskStatus（L3）-> server 代写 DB
- 若 server 也连不上 DB 或 gRPC 失败，executor 写本地 WAL（L4，`wal/{taskId}.json`），后台线程每 30s 重试
- executor 重启时优先提交本地 WAL
- 心跳对账作为最终兜底：executor 写库失败时，心跳带 Task 状态，server 对账校正

**reportJobStatus 设计要点**：
- Job 终态时 executor 直写 DB，失败则通过 server 代写
- executor_instance 字段标识执行 Job 的 executor，用于 server 对账

### 3.7 错误传递

gRPC 错误传递用两种方式：

**业务错误**（如结果不可读、限流超限）：返回正常 response，error_message 字段填充错误信息。原因：gRPC status code 只有 OK/CANCELLED/DEADLINE_EXCEEDED 等通用类，业务错误码用 status 表达不准确。

**系统错误**（如超时、网络断）：用 gRPC status code：
- `DEADLINE_EXCEEDED`：调用超时
- `UNAVAILABLE`：对端不可用，可重试

executor 和 server 内部将两种错误统一包装，记录到日志并按错误码处理。

## 4. SDK HTTP API 设计

### 4.1 AdhocClient 核心接口

AdhocClient 是 HTTP 客户端封装，提供面向 Java 用户的调用接口：

```java
public interface AdhocClient extends AutoCloseable {

    // === Job 提交与查询 ===
    JobSubmitResult submitJob(SubmitJobRequest request);
    JobDetail getJob(String jobId);
    JobStatus getJobStatus(String jobId);
    void cancelJob(String jobId);
    List<TaskSummary> getJobTasks(String jobId);

    // === Task 状态与结果 ===
    TaskStatus getTaskStatus(String taskId);
    TaskResult getTaskResult(String taskId, int pageNo, int pageSize);
    TaskLog getTaskLog(String taskId, int offset, int limit);
    JobLog getJobLog(String jobId, int offset, int limit);

    // === 目录树 ===
    FileNode createNode(CreateNodeRequest request);
    List<FileNode> listNodes(String parentNodeId);
    FileNode getNode(String nodeId);
    void renameNode(String nodeId, String newName);
    void moveNode(String nodeId, String newParentNodeId);
    void deleteNode(String nodeId);
    void restoreNode(String nodeId);
    List<FileNode> searchNodes(String keyword, String type);
    JobSubmitResult submitByFile(SubmitByFileRequest request);
}
```

**实现类**：`HttpAdhocClient`，基于 OkHttp 或 RestTemplate，调用 server 的 REST API。

### 4.2 HTTP 端点列表

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/adhoc/job/submit` | 提交 Job |
| GET | `/api/adhoc/job/{jobId}` | 查询 Job 状态（含 Task 列表） |
| POST | `/api/adhoc/job/{jobId}/cancel` | 取消 Job |
| GET | `/api/adhoc/job/{jobId}/tasks` | 查询 Job 的 Task 列表 |
| GET | `/api/adhoc/job/{jobId}/log?offset=&limit=` | 拉 Job 日志分页 |
| GET | `/api/adhoc/task/{taskId}` | 查询 Task 状态 |
| GET | `/api/adhoc/task/{taskId}/result?pageNo=&pageSize=` | 拉结果分页（server 直读存储） |
| GET | `/api/adhoc/task/{taskId}/log?offset=&limit=` | 拉 Task 日志分页 |
| POST | `/api/adhoc/file/node` | 创建目录树节点 |
| GET | `/api/adhoc/file/nodes?parentNode=` | 列出子节点 |
| GET | `/api/adhoc/file/node/{nodeId}` | 查询节点 |
| PUT | `/api/adhoc/file/node/{nodeId}/rename` | 重命名节点 |
| PUT | `/api/adhoc/file/node/{nodeId}/move` | 移动节点 |
| DELETE | `/api/adhoc/file/node/{nodeId}` | 删除节点 |
| POST | `/api/adhoc/file/node/{nodeId}/restore` | 恢复节点 |
| GET | `/api/adhoc/file/nodes/search?keyword=&type=` | 搜索节点 |
| POST | `/api/adhoc/file/submit` | 从文件提交查询 |

**提交 Job 请求体**：

```json
{
  "sql_content": "SET spark.xxx=100; USE db_ods; SELECT * FROM table_a WHERE dt='2026-07-01';",
  "engine_type": "KYUUBI",
  "engine_instance": "kyuubi-instance-1",
  "engine_params": "{\"spark.executor.memory\":\"2g\"}",
  "user_id": "user123",
  "user_name": "alice",
  "client_ip": "10.0.0.1",
  "client_request_id": "req-abc-123"
}
```

**注意**：
- engine_type 必须显式指定（KYUUBI / STARROCKS），未指定返回 `ADHOC_ENGINE_TYPE_REQUIRED`
- engine_params 为 JSON 字符串，格式错误返回 `ADHOC_ENGINE_PARAMS_INVALID`
- sql_content 可包含多段 SQL（按 `;` 拆分），SET/USE 作为前缀合并到后续 Task（executor 拆分）
- server 前置校验：SQL 语法（g4）、段数（<= max_tasks_per_job）、限流、engine_type 合法性、engine_params JSON 格式

### 4.3 Builder 模式

```java
AdhocClient client = AdhocClient.builder()
    .endpoint("http://your-gateway:8080")
    .connectTimeoutMillis(3000)
    .readTimeoutMillis(30000)
    .maxRetries(3)
    .retryBackoffMillis(1000)
    .defaultUserId("user123")
    .defaultUserName("张三")
    .build();
```

### 4.4 请求 Builder

```java
SubmitJobRequest request = SubmitJobRequest.builder()
    .sqlContent("SET spark.sql.shuffle.partitions=100; SELECT * FROM db_ods.dim_user WHERE dt='2026-07-01'")
    .engineType(EngineType.KYUUBI)
    .engineInstance("kyuubi-instance-1")
    .engineParams(EngineParams.builder()
        .add("spark.executor.memory", "2g")
        .build())
    .clientRequestId("req-001")
    .build();
```

## 5. 核心 API 详解

### 5.1 submitJob（提交查询）

**请求参数**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| sqlContent | String | 是 | SQL 原文（可含多段，按 `;` 拆分） |
| engineType | EngineType | 是 | KYUUBI / STARROCKS |
| engineInstance | String | 否 | 指定引擎实例，未指定用默认实例 |
| engineParams | Map<String,String> | 否 | session 初始参数 |
| userId | String | 否 | 覆盖默认 userId |
| userName | String | 否 | 覆盖默认 userName |
| clientRequestId | String | 否 | 客户端请求 ID（幂等键） |

**返回**：

```java
public class JobSubmitResult {
    private String jobId;
    private JobStatus status;      // PENDING / DISPATCHING
    private List<String> taskIds;  // executor 拆分后的 Task ID 列表（异步，可能为空）
}
```

**异常**：

| error_code | 触发条件 |
|---|---|
| ADHOC_JOB_TOO_MANY_TASKS | SQL 段数超 max_tasks_per_job（默认 20） |
| ADHOC_JOB_LIMIT_EXCEEDED | 限流超限（全局/per-user PENDING 或 RUNNING 超限） |
| ADHOC_ENGINE_TYPE_REQUIRED | 未指定 engine_type |
| ADHOC_ENGINE_TYPE_INVALID | engine_type 非 KYUUBI / STARROCKS |
| ADHOC_ENGINE_PARAMS_INVALID | engine_params JSON 格式错误 |
| ADHOC_SQL_SYNTAX_ERROR | SQL 语法错误（g4 解析失败） |
| ADHOC_JOB_NO_EXECUTABLE_SQL | Job 全是 SET/USE，无可执行 SQL（executor 拆分时发现） |

**示例**：

```java
try {
    JobSubmitResult result = client.submitJob(
        SubmitJobRequest.builder()
            .sqlContent("SET spark.sql.shuffle.partitions=100; USE db_ods; SELECT * FROM table_a WHERE dt='2026-07-01'")
            .engineType(EngineType.KYUUBI)
            .clientRequestId("req-001")
            .build()
    );
    String jobId = result.getJobId();
    while (true) {
        JobStatus status = client.getJobStatus(jobId);
        if (status.isTerminal()) break;
        Thread.sleep(2000);
    }
    JobDetail detail = client.getJob(jobId);
    for (TaskSummary task : detail.getTasks()) {
        TaskResult taskResult = client.getTaskResult(task.getQueryId(), 1, 100);
        System.out.println(taskResult.getRows());
    }
} catch (AdhocException e) {
    System.err.println("error_code=" + e.getErrorCode() + ", msg=" + e.getMessage());
}
```

### 5.2 getTaskResult（拉结果）

分页拉取结果：

```java
public class TaskResult {
    private String taskId;
    private List<ColumnSchema> schema;
    private List<Map<String, Object>> rows;
    private long totalRows;
    private int pageNo;
    private int pageSize;
    private String storageType;      // NONE / PERSISTENT
    private boolean hasMore;
    private long affectedRows;       // DDL/DML 时有值
}
```

**分页规则**：
- pageNo 从 1 开始，pageSize 默认 100，上限 1000
- 读路径：server 直读存储（`storageClient.download(persistent_path)` -> `AdhocResultReader` skip 分页），不转发 executor
  - storage_type = PERSISTENT 且 oss_upload_status = SUCCESS：server 直读存储
  - storage_type = NONE：返回 `ADHOC_RESULT_NO_RESULT`
  - oss_upload_status = PENDING：返回 `ADHOC_RESULT_UPLOAD_PENDING`
  - oss_upload_status = FAILED：返回 `ADHOC_RESULT_UPLOAD_FAILED`

**异常**：

| error_code | 触发条件 |
|---|---|
| ADHOC_RESULT_NO_RESULT | 无结果数据（DDL/DML 或 Task 失败，storage_type=NONE） |
| ADHOC_RESULT_INCOMPLETE | 结果传输中断，不完整（executor 宕机时 result_status=WRITING 被标记为 INCOMPLETE） |
| ADHOC_RESULT_UPLOAD_PENDING | 上传进行中（oss_upload_status=PENDING，用户稍后重试） |
| ADHOC_RESULT_UPLOAD_FAILED | 上传失败（oss_upload_status=FAILED，重试上限后） |
| ADHOC_SESSION_LOST | session 丢失（executor 宕机） |

### 5.3 getTaskLog（查日志）

分页拉取日志：

```java
public TaskLog getTaskLog(String taskId, int offset, int limit);

public class TaskLog {
    private List<String> lines;
    private int nextOffset;
    private boolean hasMore;
}
```

Job 日志同理：

```java
public JobLog getJobLog(String jobId, int offset, int limit);
```

**增量查询规则**：
- 运行中 + executor UP：server 转发 executor（FetchLog），executor 读内存 buffer（实时 ~100ms）
- executor DOWN 或 Task 终态：server 直读存储快照（`storageClient.download(persistent_log_path)`）
- 日志未上传存储且 executor DOWN：返回 `ADHOC_LOG_INCOMPLETE`（丢最后 ≤2s）

**异常**：

| error_code | 触发条件 |
|---|---|
| ADHOC_LOG_INCOMPLETE | 日志不完整（executor DOWN 且日志未上传存储） |
| ADHOC_LOG_PARTIAL | 日志为部分快照（executor DOWN，存储只有定期 flush 的快照，丢最后 ≤2s） |

### 5.4 cancelJob（取消）

设置 Job 的 cancel_requested=1，executor 停止后续 Task：

- PENDING Task：直接 CANCELED
- RUNNING Task：executor 杀引擎查询，60s 未确认 server 强制 CANCELED
- session 模式：取消后关闭 session

取消是异步动作，API 返回后 Task 可能还在 RUNNING，需轮询 status 确认终态。

### 5.5 目录树 API

```java
FileNode dir = client.createNode(CreateNodeRequest.builder()
    .parentNode(null)
    .nodeType(NodeType.DIRECTORY)
    .nodeName("my_queries")
    .build());

FileNode file = client.createNode(CreateNodeRequest.builder()
    .parentNode(dir.getNodeId())
    .nodeType(NodeType.FILE)
    .nodeName("daily_active_users.sql")
    .sqlContent("SET spark.xxx=100; SELECT * FROM ...")
    .description("日活查询")
    .build());

JobSubmitResult result = client.submitByFile(SubmitByFileRequest.builder()
    .fileNodeId(file.getNodeId())
    .engineType(EngineType.KYUUBI)
    .build());

List<FileNode> hits = client.searchNodes("dim_user", "FILE");
```

## 6. 异常处理

### 6.1 统一异常类

```java
public class AdhocException extends RuntimeException {
    private final String errorCode;     // ADHOC_* 前缀
    private final String errorMessage;
    private final Integer httpStatus;   // HTTP 状态码

    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getHttpStatus() { return httpStatus; }
}
```

### 6.2 错误码表

| 错误码 | 说明 | 触发方 |
|---|---|---|
| ADHOC_JOB_TOO_MANY_TASKS | Job 段数超限（> max_tasks_per_job） | server 前置校验 |
| ADHOC_JOB_LIMIT_EXCEEDED | 限流超限（全局/per-user PENDING 或 RUNNING 超限） | server 前置校验 |
| ADHOC_JOB_NO_EXECUTABLE_SQL | Job 全是 SET/USE，无可执行 SQL | executor 拆分时发现 |
| ADHOC_ENGINE_TYPE_REQUIRED | 未指定 engine_type | server 前置校验 |
| ADHOC_ENGINE_TYPE_INVALID | engine_type 非法（非 KYUUBI / STARROCKS） | server 前置校验 |
| ADHOC_ENGINE_PARAMS_INVALID | engine_params JSON 格式错误 | server 前置校验 |
| ADHOC_SQL_SYNTAX_ERROR | SQL 语法错误（g4 解析失败） | server 前置校验 |
| ADHOC_RESULT_NO_RESULT | 无结果数据（DDL/DML 或 Task 失败） | server 读路径 |
| ADHOC_RESULT_INCOMPLETE | 结果传输中断，不完整 | server 读路径 |
| ADHOC_RESULT_UPLOAD_PENDING | 上传进行中 | server 读路径 |
| ADHOC_RESULT_UPLOAD_FAILED | 上传失败 | server 读路径 |
| ADHOC_LOG_INCOMPLETE | 日志不完整 | server 读路径 |
| ADHOC_LOG_PARTIAL | 日志为部分快照 | server 读路径 |
| ADHOC_EXECUTOR_CRASHED | executor 宕机 | server HA 补偿 |
| ADHOC_SESSION_LOST | session 丢失（executor 宕机） | server HA 补偿 |
| ADHOC_SERVER_CRASHED | server 宕机 | SDK 重试失败后抛出 |

### 6.3 网络层重试

SDK 对网络错误自动重试：

- `maxRetries`：默认 3 次
- `retryBackoffMillis`：默认 1000ms，指数退避（1s, 2s, 4s）
- 幂等键：`clientRequestId`，重试时携带同一键，server 幂等处理
- 非幂等操作（submitJob）必须传 clientRequestId 才能重试
- 重试条件：ConnectionRefused / ReadTimeout / 5xx
- 业务错误（4xx + ADHOC_* 错误码）不重试，直接抛 AdhocException

## 7. 客户端重试策略

### 7.1 SDK 调 server

| 场景 | 策略 |
|---|---|
| 网络错误（连接拒绝、读超时、5xx） | 重试 3 次，指数退避 1s, 2s, 4s |
| 业务错误（4xx + ADHOC_* 错误码） | 不重试，直接抛 AdhocException |
| 所有重试失败 | 抛 ADHOC_SERVER_CRASHED |

### 7.2 server 调 executor（dispatchJob）

| 场景 | 策略 |
|---|---|
| gRPC UNAVAILABLE / DEADLINE_EXCEEDED | 重试 2 次，指数退避 1s, 2s |
| 仍失败 | 标记 Job FAILED，fail_reason = "executor 不可达" |

### 7.3 server 调 executor（FetchLog）

| 场景 | 策略 |
|---|---|
| gRPC 失败 | fallback 存储快照（如果 persistent_log_path 非空，读存储日志快照） |
| executor 不可达且日志未上传存储 | 返回 ADHOC_LOG_INCOMPLETE |

注：结果读路径不走 executor（server 直读存储），无 FetchResult fallback。

### 7.4 executor 调 server（heartbeat）

| 场景 | 策略 |
|---|---|
| gRPC 失败（原 server 不可达） | 查 adhoc_server_instance 表找其他 UP server，向新 server 汇报 |
| 所有 server 不可达 | 心跳暂停，后台线程每 5s 重试连接 server |

executor 本地缓存 server 列表，每 30s 从 DB 刷新，避免每次心跳都查 DB。

### 7.5 executor 调 server（reportTaskStatus / reportJobStatus）

| 场景 | 策略 |
|---|---|
| gRPC 失败（server 不可达或连不上 DB） | 写本地 WAL（`wal/{taskId}.json`） |
| WAL 写入后 | 后台线程每 30s 重试，server 恢复后接口转发成功则删除 WAL |
| executor 重启 | 优先提交本地 WAL |

**四层保障**：L1 直写 DB -> L2 重试（1s, 2s, 4s，3 次）-> L3 接口转发（reportTaskStatus）-> L4 本地 WAL + 心跳对账兜底。

## 8. 多语言 SDK 生成

### 8.1 生成流程

由于 SDK 走 HTTP REST，多语言 SDK 从 OpenAPI/Swagger 规范生成：

```
server 暴露 OpenAPI spec（/v3/api-docs）
                │
                ├── openapi-generator-cli  -> adhoc-api-sdk-java（当前，手写）
                ├── openapi-generator-python -> adhoc-api-sdk-python（后续）
                ├── openapi-generator-go     -> adhoc-api-sdk-go（后续）
                └── openapi-generator-typescript -> adhoc-api-sdk-ts（后续）
```

### 8.2 Python SDK 生成示例（后续）

```bash
# 安装工具
pip install openapi-generator-cli

# 生成
openapi-generator-cli generate \
    -i http://your-gateway:8080/v3/api-docs \
    -g python \
    -o adhoc-api-sdk-python
```

```python
# Python SDK 使用示例
import adhoc_api_sdk
from adhoc_api_sdk.api import job_api
from adhoc_api_sdk.model.submit_job_request import SubmitJobRequest

configuration = adhoc_api_sdk.Configuration(host="http://your-gateway:8080")
configuration.api_key['X-Adhoc-User-Id'] = 'user123'

with adhoc_api_sdk.ApiClient(configuration) as api_client:
    api = job_api.JobApi(api_client)
    request = SubmitJobRequest(
        sql_content="SET spark.xxx=100; SELECT * FROM db_ods.dim_user",
        engine_type="KYUUBI",
        engine_params='{"spark.executor.memory":"2g"}',
        client_request_id="req-001",
    )
    response = api.submit_job(request)
    print(response.job_id)
```

### 8.3 Go SDK 生成示例（后续）

```bash
# 安装工具
go install github.com/OpenAPITools/openapi-generator-cli/cmd/openapi-generator@latest

# 生成
openapi-generator generate \
    -i http://your-gateway:8080/v3/api-docs \
    -g go \
    -o adhoc-api-sdk-go
```

### 8.4 多语言 SDK 一致性保证

- 所有语言 SDK 从同一份 OpenAPI spec 生成，接口契约一致
- 认证统一用 HTTP 头（`X-Adhoc-User-Id` / `X-Adhoc-User-Name`）
- 错误码统一用 `ADHOC_*` 前缀字符串
- 枚举值（EngineType / JobStatus 等）由 OpenAPI schema 定义，各语言映射一致

## 9. 配置

### 9.1 Builder 配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| endpoint | 必填 | server 地址，`http://` 前缀 |
| connectTimeoutMillis | 3000 | 连接超时 |
| readTimeoutMillis | 30000 | 读超时 |
| maxRetries | 3 | 网络错误重试次数 |
| retryBackoffMillis | 1000 | 重试退避起始值（指数退避 1s, 2s, 4s） |
| defaultUserId | null | 全局默认 userId |
| defaultUserName | null | 全局默认 userName |

### 9.2 配置文件方式

支持通过 classpath 下的 `adhoc-sdk.properties` 加载：

```properties
adhoc.sdk.endpoint=http://your-gateway:8080
adhoc.sdk.connect-timeout-millis=3000
adhoc.sdk.read-timeout-millis=30000
adhoc.sdk.max-retries=3
adhoc.sdk.retry-backoff-millis=1000
adhoc.sdk.default-user-id=user123
adhoc.sdk.default-user-name=张三
```

```java
AdhocClient client = AdhocClient.fromClasspath();
```

## 10. 线程安全

### 10.1 AdhocClient 线程安全

- AdhocClient 实例线程安全，可被多线程共享
- 底层 HTTP 客户端（OkHttp / RestTemplate）线程安全
- 建议单例使用，避免重复创建连接池

### 10.2 请求对象不可变

- SubmitJobRequest 等 Builder 产出的对象不可变
- 可安全在多线程间传递

### 10.3 资源释放

- AdhocClient 实现 AutoCloseable，close 时释放连接池
- 推荐 try-with-resources 或应用关闭时 close

```java
try (AdhocClient client = AdhocClient.builder()
        .endpoint("http://your-gateway:8080")
        .defaultUserId("user123")
        .build()) {
    client.submitJob(request);
}
```

## 11. 完整用法示例

### 11.1 同步提交并等待结果

```java
public class AdhocExample {
    public static void main(String[] args) {
        try (AdhocClient client = AdhocClient.builder()
                .endpoint("http://your-gateway:8080")
                .defaultUserId("user123")
                .defaultUserName("张三")
                .build()) {

            JobSubmitResult submit = client.submitJob(
                SubmitJobRequest.builder()
                    .sqlContent("SET spark.sql.shuffle.partitions=100; USE db_ods; SELECT count(*) FROM table_a WHERE dt='2026-07-01'")
                    .engineType(EngineType.KYUUBI)
                    .engineInstance("kyuubi-instance-1")
                    .clientRequestId("req-001")
                    .build()
            );
            String jobId = submit.getJobId();
            System.out.println("submitted jobId=" + jobId);

            while (true) {
                JobStatus status = client.getJobStatus(jobId);
                System.out.println("status=" + status);
                if (status.isTerminal()) break;
                Thread.sleep(2000);
            }

            JobDetail detail = client.getJob(jobId);
            for (TaskSummary task : detail.getTasks()) {
                TaskResult result = client.getTaskResult(task.getQueryId(), 1, 100);
                System.out.println("schema=" + result.getSchema());
                System.out.println("rows=" + result.getRows());

                // 拉日志
                TaskLog log = client.getTaskLog(task.getQueryId(), 0, 100);
                System.out.println("log=" + log.getLines());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### 11.2 日志增量轮询

```java
public void watchLog(AdhocClient client, String taskId) {
    int offset = 0;
    while (true) {
        TaskLog log = client.getTaskLog(taskId, offset, 100);
        for (String line : log.getLines()) {
            System.out.println(line);
        }
        offset = log.getNextOffset();
        if (!log.isHasMore()) {
            // 检查 Task 是否终态
            TaskStatus status = client.getTaskStatus(taskId);
            if (status.isTerminal()) break;
        }
        try {
            Thread.sleep(1000);  // 1s 轮询
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
}
```

### 11.3 目录树管理

```java
FileNode root = client.createNode(CreateNodeRequest.builder()
    .parentNode(null)
    .nodeType(NodeType.DIRECTORY)
    .nodeName("daily_reports")
    .build());

FileNode sql = client.createNode(CreateNodeRequest.builder()
    .parentNode(root.getNodeId())
    .nodeType(NodeType.FILE)
    .nodeName("dau.sql")
    .sqlContent("SET spark.xxx=100; SELECT dt, count(*) as dau FROM ... GROUP BY dt")
    .description("日活趋势")
    .build());

client.submitByFile(SubmitByFileRequest.builder()
    .fileNodeId(sql.getNodeId())
    .engineType(EngineType.KYUUBI)
    .build());
```

### 11.4 取消 Job

```java
client.cancelJob(jobId);

// 轮询确认终态
while (true) {
    JobStatus status = client.getJobStatus(jobId);
    if (status.isTerminal()) break;
    Thread.sleep(1000);
}
```

## 12. 与其他模块的接口

### 12.1 与 adhoc-server 的接口

SDK 调用 server 的 HTTP REST 接口：

| 模块 | HTTP 路径 | SDK 方法 |
|---|---|---|
| 模块 1（任务调度） | `/api/adhoc/job/*` | submitJob / getJob / getJobStatus / cancelJob / getJobTasks |
| 模块 1（任务调度） | `/api/adhoc/task/*` | getTaskStatus / getTaskResult / getTaskLog |
| 模块 4（结果存储） | `/api/adhoc/task/{taskId}/result` | getTaskResult（server 直读存储） |
| 模块 9（执行日志） | `/api/adhoc/task/{taskId}/log`、`/api/adhoc/job/{jobId}/log` | getTaskLog / getJobLog |
| 模块 8（目录树） | `/api/adhoc/file/*` | createNode / listNodes / ... / submitByFile |

### 12.2 与 adhoc-protocol 的接口

SDK **不依赖** adhoc-protocol（proto 文件仅供 server/executor 内部使用）。

server 和 executor 共享 adhoc-protocol 的 proto 定义：
- gRPC stub 从 `adhoc-protocol/src/main/proto/internal/*.proto` 生成
- Java 类包名：`io.gitee.songchaolin.adhoc.internal.grpc.v1`
- 内部 RPC 不对外暴露，多语言 SDK 不从这里生成

### 12.3 与 adhoc-common 的接口

SDK 共享 adhoc-common 的枚举和异常：

- `EngineType`（KYUUBI / STARROCKS）
- `JobStatus` / `TaskStatus` / `StorageType` / `NodeType`
- `AdhocException`：统一异常

### 12.4 不依赖的模块

SDK **不依赖**：
- adhoc-server（仅通过 HTTP 调用）
- adhoc-executor
- adhoc-protocol（gRPC proto，内部用）
- adhoc-sql-parser
- adhoc-dao
- adhoc-storage
- adhoc-observability

## 13. 限制

| 限制项 | 默认值 | 说明 |
|---|---|---|
| 单次结果拉取上限 | 1000 行 | pageSize 上限 |
| 网络重试次数 | 3 | 仅网络错误重试，指数退避 1s, 2s, 4s |
| 连接超时 | 3s | |
| 读超时 | 30s | 大结果拉取可能超时，建议用分页 |
| engine_type | KYUUBI / STARROCKS | 必须显式指定 |
| 多语言 SDK | 当前仅 Java | Python/Go 后续从 OpenAPI 生成 |
| 异步回调 | 不支持 | 后续可加 CompletableFuture 重载 |
| 多 server 故障切换 | SDK 层不支持 | SDK 重试同一 endpoint，server 宕机返回 ADHOC_SERVER_CRASHED |

## 14. 验收标准

1. adhoc-api-sdk 独立打包为 jar，仅依赖 adhoc-common + HTTP 客户端 + jackson
2. 可被其他项目通过 Maven 依赖引入，无需额外配置
3. AdhocClient 线程安全，Builder 模式构建，AutoCloseable 释放资源
4. SDK 通过 HTTP REST 调用 server，不依赖 gRPC
5. server 暴露 HTTP REST（8080），对 executor 用 gRPC 客户端（9090）
6. 认证：HTTP 头 `X-Adhoc-User-Id` / `X-Adhoc-User-Name`，不做 token 校验，鉴权依赖引擎侧权限
7. proto 文件位于 adhoc-protocol/src/main/proto/internal/，package 为 adhoc.internal.v1
8. 内部 gRPC service：
   - server -> executor：JobExecutor（dispatchJob）、DataFetcher（fetchLog）、JobManager（cancelJob）
   - executor -> server：HeartbeatService（heartbeat）、TaskStatusService（reportTaskStatus）、JobStatusService（reportJobStatus）
9. heartbeat 带 running_tasks 列表，server 对账 DB 中 RUNNING Task 状态，超时 30s 标记 executor DOWN
10. reportTaskStatus 携带 ResultSummary（结果摘要），用于 L3 接口转发，失败写本地 WAL
11. HTTP 端点覆盖：submitJob / getJob / cancelJob / getJobTasks / getTaskStatus / getTaskResult / getTaskLog / getJobLog / 目录树 CRUD
12. userId 可全局默认（Builder）或单次覆盖（Request）
13. 核心 API 覆盖：submitJob / getJob / cancelJob / getTaskResult / getTaskLog / getJobLog / 目录树 CRUD
14. 统一异常 AdhocException，携带 error_code + message + httpStatus
15. 错误码表覆盖：ADHOC_JOB_TOO_MANY_TASKS / ADHOC_JOB_LIMIT_EXCEEDED / ADHOC_JOB_NO_EXECUTABLE_SQL / ADHOC_ENGINE_TYPE_REQUIRED / ADHOC_ENGINE_TYPE_INVALID / ADHOC_ENGINE_PARAMS_INVALID / ADHOC_SQL_SYNTAX_ERROR / ADHOC_RESULT_NO_RESULT / ADHOC_RESULT_INCOMPLETE / ADHOC_RESULT_UPLOAD_PENDING / ADHOC_RESULT_UPLOAD_FAILED / ADHOC_LOG_INCOMPLETE / ADHOC_LOG_PARTIAL / ADHOC_EXECUTOR_CRASHED / ADHOC_SESSION_LOST / ADHOC_SERVER_CRASHED
16. 网络错误自动重试（maxRetries=3，指数退避 1s, 2s, 4s），幂等键 clientRequestId
17. 客户端重试策略：SDK 调 server 重试 3 次，server 调 executor（dispatchJob）重试 2 次，FetchLog fallback 存储快照，heartbeat 找其他 server，reportTaskStatus 写本地 WAL
18. OpenAPI spec 作为多语言 SDK 契约源头，后续可生成 Python/Go 客户端
19. 不依赖 server/executor/protocol/parser/dao 等模块
20. 完整用法示例覆盖：同步提交、日志轮询、目录树管理、取消 Job
