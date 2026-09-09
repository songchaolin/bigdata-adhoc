# 模块 03：引擎路由

> 本模块负责 engine_type 合法性校验、engine_instance 选择、基于 load_score 的 executor 选择，在 Job 提交前置校验阶段和调度阶段执行。engine_type 由用户提交时显式指定，平台不做引擎选择推算（无 AUTO 模式）、不降级、不换引擎。

## 1. 概述

### 1.1 模块职责

- **engine_type 校验**：提交时校验 engine_type 必填且合法（KYUUBI/STARROCKS）
- **engine_instance 选择**：用户指定则用指定的，未指定则用该 engine_type 的默认 instance（配置）
- **engine_params 校验**：JSON 格式校验 + 白名单校验（adhoc_engine_param_rule 表，参数键 + 值类型 + 范围/枚举）
- **executor 选择**：基于 load_score 打分选最低分 executor，dispatchJob 下发
- **EngineEndpoint 选择**：executor 内部选 EngineEndpoint（本模块提供候选列表）

### 1.2 设计原则

- **用户显式指定**：engine_type 必须由用户提交时指定，server 不推算、不降级、不换引擎
- **配置中心管配置，实例表管存活**：EngineType/Instance/Endpoint 在 Apollo（或本地 yml），不落库；executor 实例表落库维护存活
- **load_score 软性打分**：executor 选择基于 cpu/mem/disk/running_tasks 综合打分，不硬性拒绝高水位 executor
- **职责分离**：server 只选 executor 下发 dispatchJob，executor 内部负责 JDBC connection 创建和 EngineEndpoint 选择

### 1.3 引擎范围

| engine_type | g4 解析器 | 说明 |
|---|---|---|
| KYUUBI | Spark SQL g4 | 通过 Kyuubi 连接 Spark 引擎 |
| STARROCKS | StarRocks g4 | 连接 StarRocks 引擎 |

用户提交时必须指定 engine_type，不指定则拒绝（`ADHOC_ENGINE_TYPE_REQUIRED`），engine_type 非法则拒绝（`ADHOC_ENGINE_TYPE_INVALID`）。

## 2. 引擎三级模型

### 2.1 模型定义

```
EngineType（怎么执行）
  └── EngineInstance（连哪个逻辑集群，每实例独立账号）
        └── EngineEndpoint（具体连哪个 ip:port）
```

- **EngineType = KYUUBI**：用 Kyuubi JDBC 协议执行，背后是 Spark 引擎
- **EngineInstance = kyuubi-01**：连 01 号 Kyuubi 集群（不是 02）
- **EngineEndpoint = kyuubi-1:10009**：连这台主机这个端口

**server 只选 EngineType + EngineInstance**，EngineEndpoint 由 executor 内部按负载选。server 不关心引擎集群内部拓扑。

### 2.2 EngineInstance 选择

引擎实例选择只有两种场景：

| 场景 | engine_instance 选择 |
|---|---|
| 用户指定 instance | 直接用用户指定的 |
| 用户未指定 instance | 用该 engine_type 的默认 instance（配置） |

用户提交时必须指定 engine_type，engine_instance 可选；engine_instance 必须属于该 engine_type（否则拒绝）。

### 2.3 EngineEndpoint 选择

每个 EngineInstance 下有多个 EngineEndpoint（HA + 负载均衡），由 executor 内部选择：

| 策略 | 说明 |
|---|---|
| 健康过滤 | 跳过 DOWN 的 endpoint |
| 负载排序 | 按 endpoint 当前连接数排序，选最闲的 |
| 兜底轮询 | 无负载信息时 round-robin |

单条 endpoint 也统一走 EndpointManager 轮询，行为一致。

### 2.4 多实例配置（每实例独立账号）

每个 EngineInstance 有独立的连接账号与密码（不同集群账号体系可能不同），配置结构：

```yaml
adhoc:
  engine:
    KYUUBI:
      kyuubi-01:                       # 实例名（提交时可指定 engine_instance）
        endpoints: ${ADHOC_KYUUBI_ENDPOINTS:kyuubi-1:10009}   # 逗号分隔，HA 共用下面这组账号
        user: ${ADHOC_KYUUBI_USER:}
        password: ${ADHOC_KYUUBI_PASSWORD:}
      # kyuubi-02:                    # 多实例继续往下加
    STARROCKS:
      starrocks-01:
        endpoints: ${ADHOC_SR_ENDPOINTS:starrocks-fe-1:9030}
        user: ${ADHOC_SR_USER:root}
        password: ${ADHOC_SR_PASSWORD:change-me}
```

**鉴权模式**（每引擎可配，`adhoc.engine.{ENGINE}.auth-mode`）：

| 模式 | 行为 |
|---|---|
| FIXED（默认） | 统一账号：所有用户共用 instance 配置的 user/password 连接引擎 |
| USER | 按提交人身份：以用户工号作为连接用户名（密码为空/统一），由引擎侧完成身份识别 |

USER 模式下 executor 用提交人 userId 作为连接账号，引擎侧（如配置了权限插件）按该用户鉴权。两种模式可随时切换，下游执行器无需改动。

### 2.5 实例表 vs 配置中心

| 数据 | 来源 | 维护方式 |
|---|---|---|
| EngineType + EngineInstance + EngineEndpoint | Apollo / 本地 yml | 配置中心推送，不落库 |
| adhoc_server_instance / adhoc_executor_instance | 进程自注册 | 心跳维护，落库 |

配置中心管"应该有什么"（相对静态），实例表管"现在有什么还活着"（动态）。

## 3. server 在引擎路由中的职责

### 3.1 engine_type 合法性校验

```
if engine_type is null or empty:
    拒绝，返回 ADHOC_ENGINE_TYPE_REQUIRED
if engine_type not in (KYUUBI, STARROCKS):
    拒绝，返回 ADHOC_ENGINE_TYPE_INVALID
```

### 3.2 engine_instance 合法性校验

```
if engine_instance is null or empty:
    engine_instance = 配置的默认 instance
else:
    allowed_instances = 配置中该 engine_type 下的实例列表
    if engine_instance not in allowed_instances:
        拒绝，返回 ADHOC_ENGINE_INSTANCE_INVALID
```

engine_instance 必须属于该 engine_type，跨 engine_type 的 instance 不允许。

### 3.3 engine_params 校验（JSON 格式 + 白名单）

```
if engine_params is not null and engine_params != "":
    # 1. JSON 格式校验
    try:
        params = JSON.parse(engine_params)
    except:
        拒绝，返回 ADHOC_ENGINE_PARAMS_INVALID

    # 2. 白名单校验（查 adhoc_engine_param_rule 表）
    for param_key, param_value in params.items():
        rule = SELECT * FROM adhoc_engine_param_rule
               WHERE engine_type = ? AND param_key = ? AND enabled = 1
        if rule is null:
            拒绝，返回 ADHOC_ENGINE_PARAM_NOT_ALLOWED

        # 3. 值类型校验
        switch rule.param_value_type:
            case INT:
                v = Integer.parseInt(param_value)
                if v < rule.min_value or v > rule.max_value:
                    拒绝，返回 ADHOC_ENGINE_PARAM_OUT_OF_RANGE
            case STRING:
                if rule.allowed_values is not null:
                    if param_value not in rule.allowed_values.split(','):
                        拒绝，返回 ADHOC_ENGINE_PARAM_VALUE_INVALID
            case BOOLEAN:
                if param_value not in ('true', 'false'):
                    拒绝，返回 ADHOC_ENGINE_PARAM_VALUE_INVALID
```

白名单规则表 `adhoc_engine_param_rule` 的 DDL 详见模块 11，由运维维护。

### 3.4 executor 选择（load_score 打分）

校验通过后，server 基于 load_score 打分选 executor（详见 §5），然后 dispatchJob 下发。

### 3.5 dispatchJob 下发

server 选定 executor 后，调 dispatchJob 下发整个 Job：

```protobuf
message DispatchJobRequest {
  string job_id = 1;
  string user_id = 2;
  string user_name = 3;
  string sql_content = 4;
  string engine_type = 5;       // KYUUBI/STARROCKS
  string engine_instance = 6;
  string engine_params = 7;     // JSON
  string client_ip = 8;
  string client_request_id = 9;
}
```

server 不做引擎选择推算（无 AUTO），engine_type 由用户指定；server 不创建 JDBC connection，由 executor 创建。

## 4. executor 在引擎路由中的职责

executor 接收 dispatchJob 后，承担以下引擎路由相关职责：

### 4.1 JDBC connection 创建

根据 engine_type + engine_instance 创建 JDBC connection：

| engine_type | JDBC URL 模板 |
|---|---|
| KYUUBI | `jdbc:kyuubi://{endpoint}/`（Hive JDBC 协议） |
| STARROCKS | `jdbc:mysql://{endpoint}/`（MySQL 协议） |

EngineEndpoint 由 executor 从配置的 endpoints 列表中选择（健康过滤 + 负载排序 + 兜底轮询），server 不参与。

连接账号按 §2.4 的鉴权模式确定；Kyuubi 的代理用户通过 `hive.server2.proxy.user` 连接属性透传。

### 4.2 engineParams 作为 connection 初始 params

engineParams 在 connection 创建时作为初始 params 设置。例如 KYUUBI 的 `spark.sql.shuffle.partitions=100` 会作为 connection 属性传入。

### 4.3 SET 语句覆盖/追加

SET 语句在 session 里可以覆盖/追加 engineParams：

- engineParams 是 connection 创建时的初始 params
- SET 语句在 session 里执行，可以覆盖/追加 engineParams
- 两者共存，SET 优先级更高（后执行）

详见模块 01（SET + session 复用）。

### 4.4 Session 生命周期

```
executor 接收 dispatchJob
  │
  ▼ 创建 JDBC connection（session）
  │   connection params = engineParams（作为初始 params）
  │   engineType 由用户显式指定（无 AUTO）
  │
  ▼ 拆分 Job，创建 Task 记录（直写 DB）
  │
  ▼ 顺序执行 Task（segment_index ASC）：
  │   for each task:
  │     在 session 里执行 prefix_sql + sql_content
  │     session 状态保留（临时表、视图、USE db 等）
  │
  ▼ Job 终态，关闭 session
```

Session 存储位置：executor 内存里维护 `Map<jobId, JDBC.Connection>`，Job 终态后清理。

### 4.5 引擎白名单校验（双层）

引擎白名单校验分两层（详见 §6）：

1. **server 前置校验（Job 级）**：提交时模块 02 g4 解析后，对 STARROCKS 引擎校验 SqlType 是否属于允许集合（DQL/CTAS/SESSION_CONFIG/AUX）。不属于则整个 Job 拒绝，返回 `ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED`。
2. **executor 拆分后校验（Task 级，兜底）**：executor 拆分 Task 后，根据 g4 解析出的 SqlType 校验是否在该 engine_type 的白名单内。不在白名单内的 Task 标记 FAILED，fail_reason_category = ENGINE_ERROR。

## 5. load_score 打分机制

### 5.1 心跳上报资源水位

executor 定期心跳上报资源水位：

```protobuf
message HeartbeatRequest {
  string instance_id = 1;
  int64 heartbeat_time = 2;
  double cpu_usage = 3;
  double mem_usage = 4;
  double disk_usage = 5;
  repeated TaskStatusReport running_tasks = 6;
}
```

server 收到心跳后更新 `adhoc_executor_instance` 表的 cpu_usage/mem_usage/disk_usage/running_tasks 字段。

### 5.2 load_score 计算公式

```
load_score = cpu_usage * 0.3 + mem_usage * 0.3 + disk_usage * 0.2 + running_tasks_ratio * 0.2
```

其中：
- `cpu_usage`：executor CPU 使用率（0-1）
- `mem_usage`：executor 内存使用率（0-1）
- `disk_usage`：executor 磁盘使用率（0-1）
- `running_tasks_ratio` = 当前 RUNNING Task 数 / executor 最大并发 Task 数（0-1）

### 5.3 调度时选 executor

```
server 调度 Job 时：
  │
  ▼ 1. 查可用 executor（status=UP 且 accepting=1 且支持该 engine_type）
  │
  ▼ 2. 计算 load_score
  │
  ▼ 3. 按 load_score 升序，选最低分
  │
  ▼ 4. dispatchJob 到该 executor（失败则 failover 到下一个，全失败 Job 回退 PENDING）
```

### 5.4 软性打分原则

**不硬性拒绝**：水位 > 80% 的 executor 不跳过，软性影响打分。水位高的 executor 打分高，少分配 Job。

理由：
- 避免水位阈值设置不合理导致 executor 资源浪费
- 高水位 executor 仍可承接 Job，只是优先级低
- 极端情况（所有 executor 高水位）仍能调度，不阻塞用户

### 5.5 打分示例

| executor | cpu_usage | mem_usage | disk_usage | running_tasks_ratio | load_score |
|---|---|---|---|---|---|
| executor-1 | 0.3 | 0.4 | 0.5 | 0.2 | 0.09+0.12+0.10+0.04 = 0.35 |
| executor-2 | 0.6 | 0.7 | 0.8 | 0.5 | 0.18+0.21+0.16+0.10 = 0.65 |
| executor-3 | 0.5 | 0.5 | 0.6 | 0.3 | 0.15+0.15+0.12+0.06 = 0.48 |

选 load_score 最低的 executor-1 下发 dispatchJob。

### 5.6 无可用 executor 的处理

```
无 status=UP 的可用 executor：
  │
  ▼ Job 保持 PENDING，等待下一轮调度
  │   （由模块 01 的调度线程周期性重试）
  │
  ▼ 监控告警：连续 N 次无可用 executor，运维介入
```

不创建 FAILED 状态（executor 短暂全 DOWN 时 Job 可恢复）。

## 6. 引擎白名单

每个引擎支持的 SqlType 列表（server 前置校验 + executor 拆分 Task 后双重校验）：

| 引擎 | 默认允许 SqlType | 说明 |
|---|---|---|
| KYUUBI | DQL, DDL_CREATE, DDL_ALTER, DDL_DROP, DML_INSERT, DML_MODIFY, CTAS, SESSION_CONFIG, AUX | 透传 Spark，支持 DELETE |
| STARROCKS | DQL, CTAS, SESSION_CONFIG, AUX | **仅允许 SELECT 相关语句**，不支持 DDL/DML（详见 §6.1） |

配置项：`adhoc.engine.{engineType}.allowed-sql-types`（逗号分隔，Apollo 优先，代码枚举兜底）。

校验分两层：

1. **server 前置校验（Job 级，提交时）**：模块 02 g4 解析出每个 SQL 的 SqlType 后，若 engine_type = STARROCKS 且 SqlType ∈ {DDL_CREATE, DDL_ALTER, DDL_DROP, DML_INSERT, DML_MODIFY, DCL, UNKNOWN}，整个 Job 拒绝，返回 `ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED`。此校验在 split 前完成，避免无效 Job 下发到 executor。
2. **executor 拆分后校验（Task 级，兜底）**：executor 拆分 Task 后，根据 g4 解析出的 SqlType 校验是否在该 engine_type 的白名单内。不在白名单内的 Task 标记 FAILED，fail_reason_category = ENGINE_ERROR，error_message 含具体 SqlType 不支持的提示。

SESSION_CONFIG（SET/USE）作为 prefix_sql 前缀执行，不单独走白名单校验。

### 6.1 STARROCKS 语句类型限制

StarRocks 引擎定位为 OLAP 查询引擎，DDL/DML 修改操作走专用数仓通道，不走 adhoc：

| SqlType | STARROCKS 是否允许 | 说明 |
|---|---|---|
| DQL | ✅ 允许 | SELECT 查询 |
| CTAS | ✅ 允许 | CREATE TABLE AS SELECT |
| SESSION_CONFIG | ✅ 允许 | SET/USE（作为 prefix_sql） |
| AUX | ✅ 允许 | SHOW/DESCRIBE/EXPLAIN |
| DDL_CREATE | ❌ 拒绝 | CREATE TABLE/VIEW |
| DDL_ALTER | ❌ 拒绝 | ALTER TABLE |
| DDL_DROP | ❌ 拒绝 | DROP TABLE |
| DML_INSERT | ❌ 拒绝 | INSERT |
| DML_MODIFY | ❌ 拒绝 | UPDATE/DELETE |
| DCL | ❌ 拒绝 | GRANT/REVOKE |
| UNKNOWN | ❌ 拒绝 | 无法解析的语句 |

server 前置校验拦截时返回 `ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED`，error_message 示例：`StarRocks 引擎不支持 DDL_CREATE 语句类型，请使用 KYUUBI 引擎`。

## 7. 与其他模块的接口

### 7.1 任务调度与执行模块（模块 01）

- 模块 01 在前置校验阶段调用本模块校验 engine_type/engine_instance/engine_params 合法性
- 模块 01 在调度阶段调用本模块选 executor（load_score 打分），dispatchJob 下发
- 失败不换引擎（无降级），executor 宕机走 HA 补偿（标记 FAILED），其他错误直接 FAILED

### 7.2 SQL 解析与治理模块（模块 02）

- 模块 02 产出 SqlType，供 executor 拆分后做引擎白名单校验

### 7.3 高可用模块（模块 05）

- executor 宕机后，高可用模块标记 task FAILED（fail_reason_category=EXECUTOR_CRASHED）
- 本模块不参与宕机补偿，但 executor 重启恢复 UP 后可重新接收新 Job（按 load_score 重新调度）
- executor 假 DOWN 恢复后，不回退已 FAILED 的 Task 状态
