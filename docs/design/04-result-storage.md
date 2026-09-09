# 模块 4：结果存储

> 本模块负责查询结果的持久化上传、元信息落库、分页读取、结果复用、TTL 清理。结果写入与上传在 task 生命周期的 FETCHING 阶段（拉结果）和 WRITING 阶段（内存序列化 + 上传存储）执行。

## 1. 概述

### 1.1 模块职责

- **hasResultSet 分流**：DDL/DML 仅记录 affected_rows，DQL/CTAS 落结果
- **存储后端可插拔（SPI）**：通过 `StorageClient` 接口抽象结果/日志持久化，内置 local（本地文件系统）与 aliyun（阿里云 OSS）两种实现，第三方可自行实现并注册为 Spring Bean
- **一次性上传**：executor 拉完结果全量序列化后一次性 `uploadResult` 上传（内置实现无 append/multipart 语义），不分段
- **元信息落库**：单张 `adhoc_result_summary` 表，schema 存文件头不单独建表
- **server 直读存储**：查 DB + `StorageClient.download` 直读存储分页，不转发 executor、不依赖 executor 存活
- **结果复用**：sql_hash + userId + TTL 命中后复制 summary，直接读存储
- **TTL 清理**：local 实现自扫描清理过期文件；aliyun 实现依赖 OSS 生命周期规则

### 1.2 设计原则

- **executor 只写不本地存储**：结果在 executor 内存序列化后上传存储，不落 executor 本地文件（结果/日志都不本地存储）
- **server 直读存储**：读路径只查 DB + `download`，不访问 executor，executor DOWN 不影响读
- **状态极简**：storage_type 两态 NONE/PERSISTENT，结果上传成功即 PERSISTENT，无中间态
- **长度前缀格式**：MAGIC + schema JSON + 数据行（长度前缀），存储对象与读路径共用同一格式
- **不缓存 rowOffsets**：读走长度前缀顺序扫描，避免百万行 offset 数组驻留内存
- **存储可插拔**：SPI 接口屏蔽后端差异，local 适合开发/单机部署，aliyun 适合生产/集群部署
- **无脱敏**：本期不做字段脱敏，存储上是原始数据

### 1.3 前置依赖

- 模块 1：task 表的 `has_result_set` / `affected_rows` / `sql_type` / `executor_instance` 字段
- 模块 2：SqlType 判定（DQL/CTAS 有结果集）、sql_hash 规范化 SQL
- 模块 1：`dispatchJob` RPC 接口（Job 下发 executor）
- §8：上传失败处理策略
- §6.4（模块 5）：executor 宕机时 result_summary 处理策略

## 2. hasResultSet 分流

```
Task 执行完成
  │
  ├── if hasResultSet = false（DDL/DML：DDL_CREATE/DDL_ALTER/DDL_DROP/DML_INSERT/DML_MODIFY）：
  │     仅回写 task.affected_rows，不写存储，不写 result_summary
  │     storage_type = NONE（无 summary 行，或 summary 行 storage_type=NONE）
  │
  └── if hasResultSet = true（DQL/CTAS）：
        executor 拉结果行（上限 100w 行，LIMIT 强制）
        │
        ▼ 内存序列化（全量，MAGIC+schema+rows 长度前缀，见 §5）
        │
        ▼ 一次性 uploadResult 上传存储（见 §4.2）
```

**hasResultSet 判定**（来自模块 2 SqlType）：

| SqlType | hasResultSet |
|---|---|
| DQL | true |
| CTAS | true |
| DDL_CREATE / DDL_ALTER / DDL_DROP | false |
| DML_INSERT / DML_MODIFY | false |
| DCL / AUX / UNKNOWN | false |

**LIMIT 100w 强制**：所有 hasResultSet=true 的查询，executor 在执行前强制追加 `LIMIT 1000000`。即使原 SQL 已带 LIMIT，也强制改写为 `LIMIT 1000000`（取较小值）。单结果最大 100w 行，约 200MB-500MB。

## 3. 存储后端设计

### 3.1 StorageClient SPI 接口

`adhoc-storage` 模块定义统一存储 SPI（`io.gitee.songchaolin.adhoc.storage.spi.StorageClient`），屏蔽后端差异：

```java
public interface StorageClient {
    /** 上传结果文件，返回可回读的 key */
    String uploadResult(String fileName, byte[] data);

    /** 上传日志文件（同 fileName 覆盖写快照），返回可回读的 key */
    String uploadLog(String fileName, byte[] data);

    /** 下载（返回 InputStream，调用方负责 close） */
    InputStream download(String key);

    /** 文件是否存在 */
    boolean exist(String key);
}
```

- `uploadResult`：上传结果文件，返回的 key 写入 `result_summary.persistent_path`，供 server 侧 `download` 回读
- `uploadLog`：上传日志文件，同 `fileName` 覆盖写快照（server 定期拉取 executor 日志后覆盖写入，见模块 9）
- `download`：下载，返回 `InputStream`，调用方负责 close；server 读路径用它流式分页
- `exist`：文件是否存在，用于结果复用前的存在性校验

**Bean 覆盖机制**：内置实现均带 `@ConditionalOnMissingBean(StorageClient.class)`，第三方实现只需注册为 Spring Bean 即可覆盖内置实现（`adhoc.storage.type` 未匹配 local/aliyun 时也按 Bean 类型装配）。

### 3.2 存储对象 key 命名

```
{prefix}/{jobId}/{taskId}/result.part-0
```

- 内置实现按 `result/` / `log/` 前缀区分类型
- `{day}` 分区（Job 提交日期 yyyy-MM-dd）由实现内部按需追加，用于清理
- 一个 Task 一个 key，一期一个对象 `result.part-0`（不分片）
- 二期大结果可按行数/字节数分片 part-0、part-1...，summary 表预留 persistent_path 多文件支持（key 前缀 + 分片索引）

### 3.3 写入流程

```
executor 拉结果行（上限 100w 行，LIMIT 强制）
  │
  ▼ 内存序列化（全量，流式追加到 ByteArrayOutputStream）
  │   MAGIC(4B) + schema 行 + 数据行（长度前缀，见 §5）
  │
  ▼ 序列化完成（isLast）
  │
  ▼ 一次性 uploadResult 上传存储（不分段）
  │   fullKey = storageClient.uploadResult(ossKey, data)
  │   ossKey = {jobId}/{taskId}/result.part-0
  │
  ├── upload 成功：INSERT/UPDATE result_summary
  │   storage_type = PERSISTENT, persistent_path = fullKey
  │   oss_upload_status = SUCCESS
  │
  └── upload 失败：重试（见 §12），重试上限后 oss_upload_status = FAILED
  │
  ▼ TTL 过期（local 自扫描清理 / aliyun 桶生命周期规则，见 §14）
  │   server 端 TTL 任务清理 summary 表行（见 §14）
```

### 3.4 不缓存 rowOffsets 的理由

大结果（如 100w 行）若缓存行偏移数组：
- 100 万行 * 8 字节 offset = 8MB 内存/Task
- 多 Task 并发 = 数十 MB 内存，不可接受

存储读走长度前缀顺序扫描：skip N 行靠"读 4 字节 length -> skip length 字节 -> 重复 N 次"，性能接近 O(1) per row，1000 行 skip < 10ms。无需缓存 offset。

### 3.5 流式序列化实现

```
executor 拉结果行
  │
  ▼ AdhocResultSerializer.init()
  │   写 MAGIC + schema 行（schema 序列化为 JSON，见 §5）到 ByteArrayOutputStream
  │
  ▼ 流式追加数据行（每行 INT_LEN + row bytes）
  │   不攒批，拉一行写一行
  │
  ▼ isLast 时 close（内存 buffer 完整）
  │
  ▼ INSERT adhoc_result_summary（DB 占位）
  │   SET result_status = 'WRITING',
  │       storage_type = 'NONE', persistent_path = NULL
  │       oss_upload_status = 'PENDING'
  │
  ▼ storageClient.uploadResult(ossKey, buffer)
  │
  ▼ upload 成功：UPDATE storage_type = PERSISTENT,
  │   persistent_path = fullKey, result_status = COMPLETE,
  │   oss_upload_status = SUCCESS
  │   upload 失败：重试（见 §12）
```

**为什么内存序列化而非流式上传**：内置实现 `uploadResult` 接受 `byte[]` 一次性上传，无 append/multipart 语义。因此 executor 必须在内存中完整序列化后再上传。100w 行约 200MB-500MB，executor JVM 配置足够容纳。

## 4. 上传与 storage_type

### 4.1 storage_type 两态枚举

| storage_type | 含义 | 存储 | 读取路径 |
|---|---|---|---|
| NONE | 无结果集 | 无 | 无结果可读 |
| PERSISTENT | 已上传存储 | 有 | server 直接读存储 |

### 4.2 一次性 uploadResult

executor 内存序列化完成后，一次性上传到存储，不分段、不流式：

```java
// executor 端（TaskResultWriter.write）
public String write(String taskId, String jobId, QueryResult qr, long taskStartMs) throws IOException {
    byte[] data = AdhocResultSerializer.serialize(qr.getSchema(), qr.getRows());
    String ossKey = jobId + "/" + taskId + "/result.part-0";

    // 1. 先 DB 占位（WRITING + path=null）-> 上传失败不产生孤儿（summary 行存在，path null）
    AdhocResultSummary rs = new AdhocResultSummary();
    rs.setQueryId(taskId);
    rs.setResultRows((long) qr.getRows().size());
    rs.setResultBytes((long) data.length);
    rs.setPersistentPath(null);
    rs.setStorageType(StorageType.NONE.name());
    rs.setResultStatus(ResultStatus.WRITING.name());
    rs.setOssUploadStatus(OssUploadStatus.PENDING.name());
    resultSummaryMapper.insert(rs);

    // 2. upload 存储
    String fullKey = storageClient.uploadResult(ossKey, data);

    // 3. update summary（path + PERSISTENT + COMPLETE）-- 失败则存储孤儿（summary WRITING，二期对账清理）
    resultSummaryMapper.update(null, new LambdaUpdateWrapper<AdhocResultSummary>()
        .eq(AdhocResultSummary::getQueryId, taskId)
        .set(AdhocResultSummary::getPersistentPath, fullKey)
        .set(AdhocResultSummary::getStorageType, StorageType.PERSISTENT.name())
        .set(AdhocResultSummary::getResultStatus, ResultStatus.COMPLETE.name())
        .set(AdhocResultSummary::getOssUploadStatus, OssUploadStatus.SUCCESS.name()));

    return "[executor] [INFO] [job=" + jobId + "][task=" + taskId + "] result: rows=...";
}
```

**写顺序防存储孤儿**：先 DB 占位（WRITING + path=null）-> upload 存储 -> update summary（path+PERSISTENT+COMPLETE）。存储上传失败时 summary 保持 WRITING path=null（无孤儿）；update 失败时存储有但 summary WRITING（孤儿，二期对账清理）。

**为什么一次性上传而非分段**：
- 内置实现无 append/multipart API，只能整文件上传
- 一次性上传逻辑简单，失败重试容易（重传整个文件）
- 100w 行约 200MB-500MB，单次上传可接受（对象存储带宽通常 100MB/s+，几秒内完成）
- upload 过程中 `oss_upload_status=PENDING`，读路径不读半成品（见 §7.1）

### 4.3 状态迁移图

```
Task 完成（hasResultSet=true）
  │
  ▼ 内存序列化中（result_status=WRITING）
  │   storage_type = NONE, oss_upload_status = PENDING（DB 占位行）
  │
  ▼ 一次性 uploadResult 上传存储
  │
  ├── upload 成功：
  │     storage_type = PERSISTENT, persistent_path = fullKey
  │     result_status = COMPLETE, oss_upload_status = SUCCESS（终态）
  │     │
  │     └── TTL 过期：local 自扫描清理 / aliyun 桶生命周期 + server TTL 任务清理表行（见 §14）
  │
  └── upload 失败（重试上限后）：
        oss_upload_status = FAILED
        storage_type 保持 NONE（path 仍 null）
        读路径见 §7.1：FAILED 时返回 ADHOC_RESULT_UPLOAD_FAILED

Task 完成（hasResultSet=false，DDL/DML）
  │
  └── storage_type = NONE（终态，无 summary 行或 summary 行标记 NONE）
```

### 4.4 不需要 tmp 后缀 + rename

upload 成功后直接 UPDATE `oss_upload_status = SUCCESS`，不需要 tmp 后缀 + rename：

- `oss_upload_status=PENDING` 时，读路径见 §7.1 不读半成品
- upload 成功才 UPDATE `oss_upload_status=SUCCESS`，此时存储对象已完整
- **状态控制读路径，而非文件名控制**

对象存储的 `upload` 同 key 会覆盖（主要用于日志定期快照覆盖，见 §3.1；结果一次性 upload 不涉及覆盖）。

## 5. 文件格式（存储与读路径共用）

### 5.1 文件结构

```
┌─────────────────────────────────────────┐
│ MAGIC (4 bytes) = 0xADH0C                │  文件头标识
├─────────────────────────────────────────┤
│ schema 行                                │  INT_LEN (4 bytes) + schema JSON bytes
├─────────────────────────────────────────┤
│ data 行 0                                │  INT_LEN (4 bytes) + row bytes
├─────────────────────────────────────────┤
│ data 行 1                                │  INT_LEN (4 bytes) + row bytes
├─────────────────────────────────────────┤
│ ...                                      │
└─────────────────────────────────────────┘
```

### 5.2 schema JSON 格式

schema 行存 JSON 字节，不再单独建 `adhoc_result_schema` 表：

```json
[
  {"col_index": 0, "col_name": "id", "col_type": "BIGINT"},
  {"col_index": 1, "col_name": "name", "col_type": "STRING"},
  {"col_index": 2, "col_name": "amount", "col_type": "DECIMAL(18,2)"},
  {"col_index": 3, "col_name": "event_time", "col_type": "TIMESTAMP"}
]
```

字段说明：
- `col_index`：列序号（0 起）
- `col_name`：列名
- `col_type`：列类型（STRING/INT/BIGINT/DOUBLE/DECIMAL/DATE/TIMESTAMP/ARRAY/MAP/STRUCT 等）

**为什么 schema 存文件头而非单独建表**：
- schema 与数据行同文件，读写原子性强
- 减少一张表（adhoc_result_schema）的维护成本
- 读文件时一次读出 schema，无需跨表 join
- 结果复用时复制 summary 一行即可，schema 随文件复用

### 5.3 为什么用长度前缀

- **支持 skip**：读第 N 行时按 INT_LEN 跳过前 N-1 行，不解析行内容
- **支持流式写**：executor 拉一行写一行，不攒批
- **格式紧凑**：无字段名重复开销
- **存储与读路径共用**：同一份代码 uploadResult 和 download 读
- **紧凑二进制**：成熟做法，经生产验证

### 5.4 模块结构

`adhoc-storage` 模块封装读写逻辑，executor（写）和 server（读存储）共用：

```
adhoc-storage
├── format/AdhocResultFormat          ← MAGIC/INT_LEN/readInt/getIntBytes
├── format/AdhocResultSerializer      ← schema JSON / row 序列化（写 ByteArrayOutputStream）
├── reader/AdhocResultReader          ← readLine/skip/hasNext（读 InputStream）
├── reader/AdhocResultSplit           ← 分页边界
├── model/ResultSchema                ← List<Column>
├── model/ResultRow                   ← Object[] row
└── spi/
    ├── StorageClient                 ← 存储 SPI 接口（4 方法）
    ├── StorageAutoConfiguration      ← 按 adhoc.storage.type 装配 local/aliyun Bean
    ├── LocalStorageClient            ← 本地文件系统实现
    └── AliyunStorageClient               ← 阿里云 OSS实现
```

- `format/*`：文件格式常量和序列化器，读写共用
- `reader/*`：server 读存储时使用，`AdhocResultReader` 包装 `StorageClient.download` 返回的 InputStream，`skip` 跳过前 N 行
- `model/*`：`ResultSchema`（列定义）和 `ResultRow`（行数据），读写共用
- `spi/*`：存储 SPI，内置 local/aliyun 两种实现 + 自动装配

## 6. 存储对象 key 规则

### 6.1 key

```
{jobId}/{taskId}/result.part-0
```

- 实现内部按 `result/` / `log/` 前缀区分类型（local 写入 baseDir 下 result/ 与 log/ 目录；aliyun 写入 bucket 下 result/ 与 log/ 前缀）
- 一个 Task 一个 key，一期一个对象 `result.part-0`（不分片）
- 二期大结果分片时按 part-0、part-1... 命名，summary 表的 persistent_path 支持多文件（key 前缀 + 分片索引）

## 7. 分页读取

### 7.1 读路径（server 直读存储）

任意 server 收到查询请求后，查 result_summary 后直接读存储：

```
SDK 调 GET /api/adhoc/task/{taskId}/result?pageNo=2&pageSize=100
  │
  ▼ 任意 server A 接收
  │
  ▼ 查 result_summary（storage_type, result_status, oss_upload_status）
  │
  ├── result_status = INCOMPLETE：
  │     返回 ADHOC_RESULT_INCOMPLETE（结果不完整）
  │
  ├── storage_type = NONE：
  │     返回 ADHOC_RESULT_NO_RESULT（无结果集，DDL/DML）
  │
  ├── oss_upload_status = PENDING（upload 中）：
  │     返回 ADHOC_RESULT_UPLOAD_PENDING（稍后重试）
  │
  ├── oss_upload_status = FAILED（upload 失败，见 §12）：
  │     返回 ADHOC_RESULT_UPLOAD_FAILED
  │
  └── storage_type = PERSISTENT 且 oss_upload_status = SUCCESS：
        storageClient.download(key) -> InputStream
        AdhocResultReader(InputStream).skip(offset).read(pageSize)
```

**关键设计**：
- 不走 server 间转发，不转发 executor
- 任意 server 都能直接读存储（只需 result_summary 查 DB）
- executor DOWN 不影响读（存储已持久化）
- 无 `FetchResult` gRPC

### 7.2 分页代码

```java
public PageResult readPage(String taskId, int offset, int pageSize) {
    // ① 查 result_summary
    ResultSummary summary = resultSummaryMapper.selectByQueryId(taskId);
    if (summary == null) {
        throw new AdhocException(ADHOC_RESULT_NO_RESULT);
    }
    if (summary.getResultStatus() == ResultStatus.INCOMPLETE) {
        throw new AdhocException(ADHOC_RESULT_INCOMPLETE);
    }

    StorageType st = summary.getStorageType();
    OssUploadStatus us = summary.getOssUploadStatus();

    // ② storage_type=NONE
    if (st == StorageType.NONE) {
        throw new AdhocException(ADHOC_RESULT_NO_RESULT);
    }

    // ③ upload 状态校验
    if (us == OssUploadStatus.PENDING) {
        throw new AdhocException(ADHOC_RESULT_UPLOAD_PENDING);
    }
    if (us == OssUploadStatus.FAILED) {
        throw new AdhocException(ADHOC_RESULT_UPLOAD_FAILED);
    }

    // ④ 直接读存储
    return readFromStorage(summary.getPersistentPath(), offset, pageSize);
}

private PageResult readFromStorage(String key, int offset, int pageSize) {
    // download 返回 InputStream，skip offset 行，读 pageSize 行，关闭
    try (InputStream in = storageClient.download(key)) {
        AdhocResultReader reader = new AdhocResultReader(in);
        reader.skip(offset);
        List<ResultRow> rows = reader.read(pageSize);
        return new PageResult(reader.getSchema(), rows, reader.getTotalRows());
    }
}
```

### 7.3 skip 性能

```
读第 1000 行：
  读 MAGIC（4 bytes）
  读 schema 行（INT_LEN + schema bytes）
  for i in 0..998:
    读 INT_LEN (4 bytes) -> skip length 字节
  读第 1000 行
```

不解析行内容，只读 4 字节 + skip，1000 行 skip < 10ms（InputStream 顺序读，内置实现有缓冲）。

### 7.4 无 gRPC 短超时

原 `FetchResult` 转发 executor 的 500ms 短超时已删除（server 直读存储，无 executor 转发）。`download` 超时由实现内部控制。

## 8. 元数据表（单张）

### 8.1 adhoc_result_summary（结果摘要）

| 字段 | 类型 | 说明 |
|---|---|---|
| query_id | varchar(64) PK | Task ID |
| executor_instance | varchar(256) | 写入结果的 executor（审计用，读路径不依赖） |
| result_rows | bigint | 结果总行数 |
| result_bytes | bigint | 结果字节数 |
| persistent_path | varchar(256) | 存储 key（upload 成功后有值） |
| storage_type | varchar(16) | NONE/PERSISTENT |
| result_status | varchar(16) | WRITING/COMPLETE/INCOMPLETE |
| oss_upload_status | varchar(16) | PENDING/SUCCESS/FAILED（字段名沿用 oss 前缀，语义为上传状态） |
| oss_upload_error | varchar(512) | 上传失败原因（oss_upload_status=FAILED 时有值） |
| create_time | datetime | 创建时间 |

索引：`pk_query`（query_id 主键）、`idx_storage_type`、`idx_oss_upload_status`

**storage_type 两态**：NONE（无结果）/ PERSISTENT（已上传存储），无 LOCAL/LOCAL_PERSISTENT/PERSISTENT_ONLY 中间态。

**result_status 三态**：
- WRITING：结果写入中（executor 拉结果行 + 序列化过程中）
- COMPLETE：结果完整（序列化完成）
- INCOMPLETE：结果不完整（executor 宕机时 WRITING 状态被标记为 INCOMPLETE）

**不再有 adhoc_result_schema / adhoc_result_file 表**：schema 存文件头（见 §5.2），一个 Task 一个文件路径存 summary 的 persistent_path。

## 9. executor <-> server 通信

### 9.1 FetchLog RPC（server -> executor，日志实时）

server 读日志时转发到 executor 读内存 buffer（日志实时，详见模块 9）。**结果读取不再走 gRPC**（server 直读存储）：

```protobuf
service DataFetcher {
  rpc fetchLog(FetchLogRequest) returns (FetchLogResponse);  // 日志实时读
  // fetchResult 已删除（server 直读存储，见 §7）
}
```

**FetchResult 删除原因**：
- server 直读存储，不需要转发 executor 读结果
- executor DOWN 不影响读（存储持久化）
- 减少 gRPC 接口和 executor 读线程池负担

### 9.2 不再有 reportResult RPC

executor 内存序列化结果后直接上传存储 + 直写 result_summary，不通过 RPC 上报结果行给 server。server 只在用户翻页时通过 `download` 主动读存储。

**去掉的原因**：
- server 不再持有结果数据，健壮性提升（server 宕机不影响结果）
- 减少 RPC 流量（100w 行不再流经 server）
- 减少一次网络跳（executor -> server -> 存储变为 executor -> 存储直传）

### 9.3 状态上报（reportTaskStatus RPC）

executor 通过 reportTaskStatus RPC 上报 Task 状态变更（非结果数据），仅在 executor 直写 DB 失败时作为 L3 兜底：

```protobuf
service TaskStatusService {
  rpc reportTaskStatus(ReportTaskStatusRequest) returns (ReportTaskStatusResponse);
}

message ReportTaskStatusRequest {
  string task_id = 1;
  string status = 2;
  string stage = 3;
  string fail_reason_category = 4;
  string error_code = 5;
  string error_message = 6;
  int64 finish_time = 7;
  ResultSummary result_summary = 8;  // 含 storage_type / persistent_path 等
}
```

注意：`result_summary` 字段只传元信息（存储 key、行数、storage_type），不传结果数据行。

## 10. 线程池隔离

### 10.1 executor 线程池

结果读取不再走 executor（server 直读存储），executor 线程池只负责执行 + 日志 FetchLog gRPC 响应：

| 线程池 | 用途 | 大小 |
|---|---|---|
| 执行线程池 | 处理 dispatchJob，执行 SQL，序列化结果，upload 存储 | 按配置（如 20） |
| gRPC 响应线程池 | 处理 FetchLog（读内存 buffer），轻量 | 复用 gRPC 默认 |

**结果读线程池已删除**：原 `FetchResult` 读线程池随 `FetchResult` 删除而删除，server 直读存储不占用 executor 线程。

### 10.2 无读线程池满问题

原读线程池满返回 `ADHOC_EXECUTOR_READ_BUSY` 的场景已不存在（server 直读存储）。存储读并发由 server 自身线程池控制，与 executor 无关。

### 10.3 为什么不再需要隔离

- **执行不被读拖慢**：读请求不进 executor，executor 只管执行 + upload
- **读不被执行拖慢**：读走 server -> 存储，与 executor 执行互不影响
- **资源可控**：executor 资源全用于执行，读压力由 server + 存储承担

## 11. 结果复用

### 11.1 复用前提

复用前提：原 Task 必须 `storage_type = PERSISTENT` 且 `oss_upload_status = SUCCESS`（已上传存储）。

原 Task 如果 `oss_upload_status=PENDING/FAILED`（未上传成功），**不允许复用**。原因：
- 复用结果应直接读存储，不指向原 executor
- 只有存储上的结果才能稳定复用

### 11.2 复用机制

提交 Job 时，按 `sql_hash + userId + TTL 300s` 查最近 SUCCESS Task：

```sql
SELECT query_id FROM adhoc_query_task t
JOIN adhoc_result_summary s ON t.query_id = s.query_id
WHERE t.user_id = ?
  AND t.sql_hash = ?
  AND t.status = 'SUCCESS'
  AND s.storage_type = 'PERSISTENT'
  AND s.oss_upload_status = 'SUCCESS'
  AND s.result_status = 'COMPLETE'
  AND t.finish_time >= NOW() - INTERVAL 300 SECOND
ORDER BY t.finish_time DESC
LIMIT 1;
```

### 11.3 命中后操作

```sql
-- 1. 新 Task 直接 SUCCESS
INSERT INTO adhoc_query_task
  (query_id, job_id, segment_index, sql_content, sql_hash, user_id, status,
   reused_from_task_id, submit_time, finish_time)
VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS',
   ?, NOW(), NOW());

-- 2. 复制 result_summary 行（persistent_path 不变，storage_type 保持 PERSISTENT）
--    新 Task 不绑定原 executor，直接读存储
INSERT INTO adhoc_result_summary
  (query_id, executor_instance, result_rows, result_bytes,
   persistent_path, storage_type, result_status, oss_upload_status,
   create_time)
SELECT ?, NULL, result_rows, result_bytes,
   persistent_path, 'PERSISTENT', 'COMPLETE', 'SUCCESS',
   NOW()
FROM adhoc_result_summary WHERE query_id = ?;
```

**关键点**：
- 复用结果的 storage_type 保持 PERSISTENT（不依赖原 executor）
- executor_instance 设为 NULL（不绑定原 executor）
- 不再复制 adhoc_result_schema 和 adhoc_result_file 行（schema 在文件头）

### 11.4 sql_hash 计算

```java
String sqlHash = DigestUtils.sha256Hex(normalizedSql);
```

`normalizedSql` 来自模块 2 的 SQL 规范化（大小写统一、空白统一、引号统一）。sql_hash 计算包含 prefix_sql：`sql_hash = SHA256(prefix_sql + sql_content)`。

### 11.5 为什么按 userId

同一条 SQL 不同用户查，权限不同，结果不能复用。按 `userId + sql_hash` 复用保证安全。

### 11.6 为什么 TTL 300s

- 太短（如 30s）：复用命中率低
- 太长（如 1h）：数据可能已变（下游 ETL 重写了源表）
- 5 分钟是即席查询常见间隔，平衡命中率和新鲜度

### 11.7 复用命中的 Task 阶段时间

复用命中的 Task 所有阶段时间字段为 NULL，`reused_from_task_id` 指向原 Task，`finish_time = submit_time`，`duration_ms = 0`。前端可展示"结果复用，未走引擎"。

## 12. 上传失败处理

### 12.1 处理策略

```
内存序列化完成，uploadResult 上传存储失败
  │
  ▼ storage_type 保持 NONE（path 仍 null），oss_upload_status = FAILED
  │   oss_upload_error = "上传失败: {详情}"
  │
  ▼ 读路径（见 §7.1）：
  │   oss_upload_status=FAILED 时返回 ADHOC_RESULT_UPLOAD_FAILED
  │   用户无法读取结果（存储无数据）
  │
  ▼ 监控告警：oss_upload_status=FAILED 的 Task 告警，运维介入
  │
  ▼ 重试（见 §12.3）：成功则 UPDATE oss_upload_status=SUCCESS
```

**核心思想**：存储上传失败时结果不可读（存储是唯一持久化层），需立即重试 + 告警，无本地兜底期。

### 12.2 上传成功

```
内存序列化完成，uploadResult 上传存储成功
  │
  ▼ UPDATE result_summary
  │   SET storage_type = 'PERSISTENT',
  │       persistent_path = '{fullKey}',
  │       result_status = 'COMPLETE',
  │       oss_upload_status = 'SUCCESS'
  │   WHERE query_id = ?;
```

**不需要 tmp 后缀 + rename**（见 §4.4）：
- `oss_upload_status=PENDING` 时，读路径不读半成品
- upload 成功才 UPDATE `oss_upload_status=SUCCESS`，此时存储对象已完整
- 状态控制读路径，而非文件名控制

### 12.3 上传重试

上传失败时重试（指数退避：1s, 2s, 4s, 8s, 16s, 30s，最多 6 次，总等待 ~60s）：

```java
public void uploadWithRetry(String taskId, String ossKey, byte[] buffer) {
    long[] delays = {1000, 2000, 4000, 8000, 16000, 30000};
    for (int i = 0; i < delays.length; i++) {
        try {
            String fullKey = storageClient.uploadResult(ossKey, buffer);
            // 成功
            resultSummaryMapper.updateStorage(taskId,
                StorageType.PERSISTENT, fullKey,
                OssUploadStatus.SUCCESS, null);
            return;
        } catch (Exception e) {
            log.warn("upload failed, retry {}/{}, taskId={}", i + 1, delays.length, taskId, e);
            if (i < delays.length - 1) {
                try { Thread.sleep(delays[i]); } catch (InterruptedException ie) { break; }
            }
        }
    }
    // 重试上限后
    resultSummaryMapper.updateUploadStatus(taskId,
        OssUploadStatus.FAILED, "上传失败（重试 6 次仍失败）");
}
```

重试期间：
- `oss_upload_status=PENDING`
- 读路径返回 `ADHOC_RESULT_UPLOAD_PENDING`（用户稍后重试）
- 监控指标 `adhoc_result_oss_upload_retry_total` 累加

### 12.4 重试上限后

重试 6 次仍失败：
- `oss_upload_status = FAILED`
- `oss_upload_error` 记录失败详情
- 用户读取返回 `ADHOC_RESULT_UPLOAD_FAILED`
- 监控告警，运维介入（内存 buffer 已释放，无法手动重传，需重跑 Task）

### 12.5 无 executor 重启补传

executor 不本地存储结果，无本地文件可补传：

- executor 重启不补传结果（内存 buffer 已释放）
- `oss_upload_status=FAILED` 的 Task 需重跑（用户重新提交；若 5min 内同 user 同 sql_hash 重提交，命中结果复用除外）

## 13. executor 宕机的 result_summary 处理

详见模块 5 §6.4，本节聚焦 result_summary 部分。

```
executor 宕机，server HA 线程标记 executor DOWN
  │
  ▼ 对每个被标记 FAILED 的 Task，查 result_summary：
  │
  ├── 无 summary（executor 还没来得及写元信息）：
  │     不创建 summary
  │     用户读取返回 ADHOC_RESULT_NO_RESULT
  │
  ├── result_status = WRITING（结果不完整，序列化中或 upload 中）：
  │     UPDATE result_summary
  │     SET result_status = 'INCOMPLETE'
  │     WHERE query_id = ?;
  │     用户读取返回 ADHOC_RESULT_INCOMPLETE
  │
  └── result_status = COMPLETE 且 oss_upload_status = SUCCESS（结果完整，已上传存储）：
        保持 summary 不变
        用户读取直读存储（executor DOWN 不影响读）
```

无 LOCAL 态，结果要么 upload 成功（存储可读，executor DOWN 无影响），要么 upload 未完成（result_status=WRITING -> INCOMPLETE）。**无 ADHOC_RESULT_LOST 场景**。

### 13.1 SQL 实现

```java
// server HA 线程，对每个 FAILED Task
public void handleExecutorDownTask(String taskId) {
    ResultSummary summary = resultSummaryMapper.selectByQueryId(taskId);
    if (summary == null) {
        // 无 summary，不创建，用户读取时返回 ADHOC_RESULT_NO_RESULT
        return;
    }
    if (summary.getResultStatus() == ResultStatus.WRITING) {
        // 结果不完整（序列化中或 upload 中）
        resultSummaryMapper.updateResultStatus(taskId, ResultStatus.INCOMPLETE);
        return;
    }
    // result_status = COMPLETE 且 oss_upload_status = SUCCESS
    // 不需要更新 summary，读路径直读存储（executor DOWN 无影响）
}
```

### 13.2 executor 假 DOWN 后的恢复

详见模块 5 §6.6。executor 假 DOWN 后恢复时：

```
executor 心跳恢复，发现自己是 DOWN 状态
  │
  ▼ 主动 UPDATE 为 UP
  │
  ▼ 不恢复已 FAILED 的 Task（避免状态回退）
  │
  ▼ 不补传结果（无本地文件，见 §12.5）
  │
  ▼ 已 FAILED 的 Task 状态不回退（用户可能已重试）
```

## 14. TTL 清理

### 14.1 存储对象 TTL

存储对象生命周期由后端实现控制：

- **local 实现**：server 后台线程定期扫描 baseDir 下 result/ 与 log/ 目录，清理 N 天前的文件
- **aliyun 实现**：依赖对象存储桶生命周期规则（lifecycle rule），到期自动删除对象

### 14.2 result_summary 表清理（server 端，30 天）

server 后台线程每 1 小时扫描 result_summary，清理 30 天前的表行：

```java
@Scheduled(cron = "0 0 * * * ?")  // 每小时
public void cleanupExpiredResultSummary() {
    int retentionDays = config.getInt("adhoc.result.retention-days", 30);
    LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

    // 扫描 30 天前的 summary
    List<ResultSummary> expired = resultSummaryMapper.selectExpired(cutoff);
    for (ResultSummary summary : expired) {
        // 存储对象由后端清理（local 自扫描 / aliyun 桶规则），应用侧不重复删文件
        // 删除 summary 表行
        resultSummaryMapper.deleteByQueryId(summary.getQueryId());
        // 软删除 task 表（保留任务记录，不保留结果）
        taskMapper.softDeleteByQueryId(summary.getQueryId());
    }
}
```

### 14.3 清理范围

**存储对象清理**（后端）：
- local：自扫描 baseDir 下过期文件
- aliyun：桶生命周期规则自动删除对象

**表清理**（server 端）：
- 删除 result_summary 表行
- 软删除 adhoc_query_task（保留任务记录，不保留结果）

### 14.4 清理时机对比

| 清理对象 | 时机 | 执行方 | 触发条件 |
|---|---|---|---|
| 存储对象（local） | 30 天 | server 自扫描 | baseDir 下文件 mtime + 30 天 |
| 存储对象（aliyun） | 30 天 | 对象存储桶规则 | 桶 lifecycle rule |
| result_summary 行 | 30 天 | server | create_time + 30 天 |
| task 表行 | 30 天 | server | 随存储对象清理（软删除） |

## 15. 配置项汇总

```properties
# 结果限制
adhoc.result.max-rows=1000000                    # 单结果最大行数（LIMIT 100w）

# 存储后端（SPI，见 §3.1）
adhoc.storage.type=local                          # local / aliyun，不配则按 Bean 类型装配

# local 实现
adhoc.storage.local.base-dir=./data/storage       # 本地存储根目录

# aliyun 实现
adhoc.storage.aliyun.endpoint=                        # 阿里云 OSS endpoint（如 https://oss-cn-hangzhou.aliyuncs.com）
adhoc.storage.aliyun.bucket=                          # 桶名
adhoc.storage.aliyun.access-key=                      # 访问密钥
adhoc.storage.aliyun.secret-key=                      # 秘密密钥

# 结果保留
adhoc.result.retention-days=30                    # 结果保留天数（result_summary 表清理）
adhoc.result.cleanup-interval-minutes=60          # summary 清理任务间隔
adhoc.result.oss-upload-max-retries=6             # 上传最大重试次数
adhoc.result.oss-upload-retry-base-ms=1000        # 上传重试基础间隔（指数退避）

# 结果复用
adhoc.result.reuse-ttl-seconds=300                # 结果复用 TTL
```

**优先级**：Apollo > 环境变量 > 本地 yml > 代码默认值，无需重启即可生效（Apollo 推送）。

## 16. 错误码

| 错误码 | 含义 |
|---|---|
| `ADHOC_RESULT_NO_RESULT` | 无结果数据（DDL/DML，或 executor 宕机时未写 summary） |
| `ADHOC_RESULT_INCOMPLETE` | 结果传输中断，不完整（executor 宕机时 result_status=WRITING 被标记为 INCOMPLETE） |
| `ADHOC_RESULT_UPLOAD_PENDING` | 上传进行中（oss_upload_status=PENDING，用户稍后重试） |
| `ADHOC_RESULT_UPLOAD_FAILED` | 上传失败（oss_upload_status=FAILED，重试上限后） |

## 17. 监控指标

| 指标 | 含义 |
|---|---|
| `adhoc_result_serialize_total` | 结果序列化次数 |
| `adhoc_result_serialize_failed_total` | 结果序列化失败次数 |
| `adhoc_result_oss_upload_total` | 上传次数 |
| `adhoc_result_oss_upload_success_total` | 上传成功次数 |
| `adhoc_result_oss_upload_failed_total` | 上传失败次数（重试上限后） |
| `adhoc_result_oss_upload_retry_total` | 上传重试次数 |
| `adhoc_result_oss_upload_duration_ms` | 上传耗时分布 |
| `adhoc_result_read_oss_total` | server 直接读存储次数 |
| `adhoc_result_summary_cleanup_total` | result_summary 表清理行数 |
| `adhoc_result_reuse_hit_total` | 结果复用命中次数 |

## 18. 与其他模块的接口

### 18.1 模块 1（任务调度与执行）

- executor 在 FETCHING 阶段拉结果序列化
- executor 在 WRITING 阶段 upload 存储 + 写 result_summary
- executor 通过 dispatchJob 接收 Job，内部拆分 Task 并执行
- task 表的 `has_result_set` / `affected_rows` / `sql_type` / `executor_instance` 字段由模块 1 维护
- 模块 1 的结果复用校验在提交时调用本模块的查询接口
- task 表的 `executor_instance` 字段仅审计用，读路径不依赖（server 直读存储）

### 18.2 模块 2（SQL 解析与治理）

- SqlType 判定 hasResultSet（DQL/CTAS = true）
- normalizedSql + prefix_sql 产出 sql_hash
- LIMIT 100w 强制追加在 executor 执行前（由模块 1 调用本模块的工具方法）
- 模块 2 的血缘写到 adhoc_query_table_ref（本模块不涉及）

### 18.3 模块 5（高可用与补偿）

- **executor 宕机处理**：模块 5 §6.4，本模块 §13
  - result_summary 按 result_status 三态分流（无 summary / WRITING -> INCOMPLETE / COMPLETE）
  - COMPLETE + oss_upload_status=SUCCESS：用户读取直读存储（executor DOWN 无影响）
  - WRITING：标记 INCOMPLETE，返回 ADHOC_RESULT_INCOMPLETE
- **executor 假 DOWN 后恢复**：模块 5 §6.6，本模块 §13.2（不补传，无本地文件）
- `adhoc_executor_instance.status` 字段用于日志 FetchLog 读路径决策（结果读不依赖，见模块 9）

### 18.4 模块 6（可观测性）

- 本模块的监控指标由模块 6 采集
- 上传成功率、summary 清理等任务的执行情况由模块 6 监控
- `oss_upload_status=FAILED` 的 Task 触发告警（模块 6 配置告警规则）

### 18.5 模块 12（SDK / 内部 proto）

- **DataFetcher**（server -> executor，定义在 `adhoc-protocol/src/main/proto/internal/data_fetcher.proto`）：
  - `FetchResult`：**已删除**（server 直读存储，不转发 executor）
  - `FetchLog(taskId, offset, limit, trace_id)`：从 executor 内存 buffer 拉日志分页（实时，详见模块 9）
  - 详见模块 12
- **TaskStatusService**（executor -> server）：
  - `reportTaskStatus(...)`：L3 接口转发状态（含 result_summary 元信息，不含数据行）
- 转发调用携带 `trace_id` 透传，串联跨 server 链路

## 19. 验收标准

1. hasResultSet 分流：DDL/DML 仅写 task.affected_rows，不写存储、不写 result_summary；DQL/CTAS 走存储
2. DQL/CTAS 强制 LIMIT 1000000，单结果最大 100w 行
3. executor 拉结果行内存序列化（MAGIC + schema JSON + 数据行长度前缀），不写本地文件
4. 内存序列化完成后一次性 `uploadResult` 上传存储，不分段、不流式
5. upload 成功：UPDATE storage_type = PERSISTENT, oss_upload_status = SUCCESS，不需要 tmp 后缀 + rename
6. upload 失败：oss_upload_status = FAILED，记录 error
7. upload 重试：指数退避 1s/2s/4s/8s/16s/30s，最多 6 次
8. executor 不本地存储，重启不补传结果（无本地文件）
9. storage_type 两态枚举：NONE / PERSISTENT
10. 存储对象 TTL 过期：local 自扫描 / aliyun 桶生命周期规则
11. result_summary 表 30 天过期：server TTL 任务删除表行 + 软删除 task 表行
12. 文件格式：MAGIC(4B) + schema JSON 行 + 数据行（INT_LEN + row bytes），存储与读路径共用
13. schema 存文件头（JSON 格式），不建 adhoc_result_schema 表
14. 不缓存 rowOffsets 数组，读走长度前缀顺序扫描 + skip
15. 读路径：查 result_summary，按 storage_type + oss_upload_status 决策
    - NONE：返回 ADHOC_RESULT_NO_RESULT
    - PERSISTENT + SUCCESS：storageClient.download 直读存储分页
    - PERSISTENT + PENDING：返回 ADHOC_RESULT_UPLOAD_PENDING
    - PERSISTENT + FAILED：返回 ADHOC_RESULT_UPLOAD_FAILED
16. 无 FetchResult gRPC，无 executor 读线程池，无 ADHOC_EXECUTOR_READ_BUSY
17. executor DOWN 不影响读（存储持久化，server 直读）
18. 单张 result_summary 表，schema 和存储 key 都存 summary，不再有 adhoc_result_schema / adhoc_result_file 表
19. 结果复用：sql_hash + userId + 5min TTL，命中后复制 summary 行（storage_type=PERSISTENT，executor_instance=NULL，直接读存储）
20. 结果复用前提：原 Task storage_type = PERSISTENT 且 oss_upload_status = SUCCESS（已上传存储）
21. executor 宕机时 result_summary 处理：无 summary -> ADHOC_RESULT_NO_RESULT；WRITING -> INCOMPLETE -> ADHOC_RESULT_INCOMPLETE；COMPLETE + SUCCESS -> 直读存储（executor DOWN 无影响）
22. StorageClient SPI：4 方法接口（uploadResult/uploadLog/download/exist），内置 local/aliyun 两种实现，第三方可注册 Bean 覆盖
23. local 实现：base-dir 下 result/ 与 log/ 前缀，路径穿越防护
24. aliyun 实现：endpoint/bucket/access-key/secret-key（阿里云 OSS）
25. 本期不做脱敏，存储上是原始数据
