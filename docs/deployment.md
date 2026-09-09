# 部署指南

> 本篇是 [README 快速开始](../README.md#快速开始)的生产深化版：单机体验直接看 README，生产部署（多节点 / Apollo / 网关 / OSS）看本篇。

## 部署架构回顾

```
                 ┌──────────────────────────┐
   REST / SDK ──▶│  adhoc-server (可多实例)  │──▶ MySQL（元数据）
                 │  调度 / CAS 抢占 / 日志收集 │
                 └───────────┬──────────────┘
                             │ gRPC（dispatch / fetchJobLog / 心跳）
                 ┌───────────▼──────────────┐
                 │ adhoc-executor (可多实例)  │──▶ Kyuubi / StarRocks
                 │  SQL 拆段 / 执行 / 结果存储 │
                 └───────────┬──────────────┘
                             │ StorageClient
                    local 目录  /  阿里云 OSS
```

完整架构说明见 [README](../README.md#架构) 与 [设计文档 00-architecture-overview](design/00-architecture-overview.md)。要点：

- **adhoc-server**：接收 REST/SDK 请求、调度分发、收集日志。无状态、可多实例水平扩展（CAS 无锁抢占）。
- **adhoc-executor**：真正连引擎执行 SQL、写结果。可多实例，session 绑定 executor。
- **MySQL**：元数据库（Job/Task/实例表），所有实例共用一个库。
- **Apollo**（可选）：动态配置中心，生产推荐。
- **OSS**（可选）：结果与日志对象存储，生产多节点必配。

## 生产部署步骤

### 1. 打包

```bash
mvn clean package -DskipTests
# 产物：
# adhoc-server/target/*.jar   （REST 8080 / gRPC 9090）
# adhoc-executor/target/*.jar （HTTP 8081 / gRPC 9091）
```

### 2. 建库建表

`sql/create.sql` 为幂等 DDL（`CREATE TABLE IF NOT EXISTS`），可重复执行：

```bash
mysql -e "CREATE DATABASE adhoc DEFAULT CHARACTER SET utf8mb4"
mysql adhoc < sql/create.sql
```

后续版本增量脚本见 `sql/` 目录（如 `datetime3-migration.sql`、`migrate-jvm-metrics.sql`），升级时按文件名顺序补执行。

### 3. 配置 Apollo（可选）

两个应用各自在 Apollo 注册：

| 应用 | app.id | 默认端口 |
|------|--------|---------|
| adhoc-server | `bigdata-adhoc-server` | HTTP 8080 / gRPC 9090 |
| adhoc-executor | `bigdata-adhoc-executor` | HTTP 8081 / gRPC 9091 |

`app.id` 与 `apollo.meta` 写在 `META-INF/app.properties`（Apollo 客户端约定），**修改后需重新打包**；也可用 `-Dapollo.meta` 或环境变量 `APOLLO_META` 覆盖，无需改包。

不接 Apollo 时：直接用本地 `application.yml` + 环境变量（见 README 快速开始）。

配置项清单见 [configuration.md](configuration.md) 与 [apollo-prod-config.md](apollo-prod-config.md)（完整生产清单）。

### 4. 启动

```bash
java -jar adhoc-server.jar   # 多实例则改 -Dserver.port / -Dgrpc.server.port
java -jar adhoc-executor.jar
```

server 侧需配置 executor 的 gRPC 地址（本地 yml 的 `grpc.client.adhoc-executor.address`，多 executor 用 `static://h1:9091,h2:9091`）。

### 5. 验证

```bash
# Swagger 在线文档能打开
curl http://<server-host>:8080/doc.html

# 提交一条查询
curl -X POST http://<server-host>:8080/api/job \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: user001" \
  -d '{"sqlContent": "SELECT 1", "engineType": "STARROCKS"}'
```

## 多节点部署

### 多 server（水平扩展）

- 所有 server 对等、无主从：各自独立扫描 PENDING Job，`UPDATE ... WHERE status='PENDING'` CAS 抢占，同一 Job 只会被一个 server 领走，**无需分布式锁**。
- server 无状态（任务状态全在 MySQL），任意 server 可查询 / 取消 / 读结果，客户端负载均衡即可。
- 多实例建议显式配置 `adhoc.server.instance-id`（如 `server-prod-01`），便于排障追溯。
- **server 宕机不影响 RUNNING Job**（session 在 executor 内存），其名下 PENDING Job 由其他 server 经超时补偿接管重调度。

### 多 executor

- 按引擎分组：`adhoc.executor.engine-types` 配置该实例支持的引擎子集（默认 `KYUUBI,STARROCKS`）。
- 调度时 server 选择 UP 且 accepting 且支持目标引擎的 executor；ping 失败自动 failover 到下一个。
- 扩容：新 executor 配好引擎连接启动即自注册（心跳上报），无需改 server；server 侧 gRPC 地址列表加上新实例即可。
- **executor 宕机**：心跳 30s 超时标 DOWN → 其上 RUNNING Task 标 FAILED（`EXECUTOR_CRASHED`，session 丢失不重试）→ Job 状态重算；server 有 reconcile 兜底对账卡住的 Job。

HA 细节见 [设计文档 05-high-availability](design/05-high-availability.md)。

### 容量建议

| 项 | 说明 |
|----|------|
| 元数据库 | Job/Task 表增长快，预留磁盘；可定期归档终态数据 |
| executor 并发 | `adhoc.executor.max-concurrent-tasks` 按引擎承载设（默认 10000 偏大） |
| server 数量 | 2~3 个即可，调度扫描对 DB 压力小 |
| 限流 | 生产建议调小 per-user 限额，防单用户占满队列，见 [configuration.md](configuration.md) |

## nginx 网关与用户头注入

平台自身不带登录体系，用户身份由前置网关 / 反向代理认证后注入标准头，**server 端口不应直接暴露公网**（否则身份头可伪造）：

```nginx
location /adhoc/ {
    proxy_set_header X-Adhoc-User-Id   $remote_user_id;   # 网关侧解析后的用户标识
    proxy_set_header X-Adhoc-User-Name $remote_user_name; # 展示名（如中文姓名）
    proxy_pass http://adhoc-server:8080/;
}
```

两个头的语义：

| 头 | 必填 | 说明 |
|----|------|------|
| `X-Adhoc-User-Id` | 是 | 用户唯一标识（限流、归属校验、结果复用均按此隔离） |
| `X-Adhoc-User-Name` | 否 | 展示名，冗余存储 |

**监控大盘放行**：若网关对全部路径强制登录，需为大盘单独放行（其自身用 token 鉴权）：

```nginx
# /gw/adhoc/ 场景下，这两条需加入网关白名单（免登录）：
location /gw/adhoc/dashboard/    { proxy_pass http://adhoc-server:8080/dashboard/; }
location /gw/adhoc/api/metrics/  { proxy_pass http://adhoc-server:8080/api/metrics/; }
```

用户侧接口（`/api/**`）仍走网关登录 + 注入身份头。

## 存储配置

结果与日志通过 `StorageClient` SPI 落盘，两 App（server + executor）**必须配成一致**，否则结果/日志落到不同空间：

```yaml
adhoc.storage:
  # local：默认，开箱即用，结果落在本地目录——仅适合单机/体验
  type: local
  local.base-dir: ./data/storage

  # aliyun：生产多节点推荐，结果/日志落对象存储，任意实例可读
  # type: aliyun
  # aliyun.endpoint: https://oss-cn-hangzhou.aliyuncs.com
  # aliyun.bucket: bigdata-adhoc
  # aliyun.access-key: xxx
  # aliyun.secret-key: xxx
```

- **单机/开发**：`local` 即可。
- **生产多节点**：必须 `aliyun`（或自实现存储）——executor 写结果、server/任意节点读结果，共享存储是前提。
- 第三方存储自行实现 `io.gitee.songchaolin.adhoc.storage.spi.StorageClient` 并注册为 Spring Bean（内置实现带 `@ConditionalOnMissingBean`，同容器内自定义 Bean 自动优先）。

OSS 生命周期等共享参数见 [configuration.md](configuration.md#共享参数)。

## 监控大盘

启动后访问 `http://<server-host>:8080/dashboard`（首次带 `?token=<adhoc.dashboard.access-token>`）。
token 由 `adhoc.dashboard.access-token` 配置（**生产必配长随机串**，空 = 不启用鉴权）。
功能：集群指标、Job/Task 趋势、每实例负载、JVM 时序、SQL 控制台。详见 [接口文档 - dashboard](api/dashboard.md)。

## 日常运维要点

- **日志**：默认输出 stdout（`logging.level.*` 可调）；Job 执行日志走平台自身链路（JobLogCollector → 存储），通过 `/api/job/log` 查询，终态后可从 OSS 下载。
- **健康检查**：executor 5s 心跳上报（30s 超时标 DOWN）、server 5s 心跳（15s 超时标 DOWN）；实例状态可在监控大盘或 `adhoc_executor_instance` / `adhoc_server_instance` 表查。
- **配置自检**：`GET /api/config` 返回当前生效配置清单（敏感值脱敏），Apollo 配完可调用核对。
- **排障 SQL**：`sql/troubleshooting-queries.sql` 收录了常用排查查询（卡住 Job、实例状态、失败分布等）。
- **升级**：幂等 DDL 可直接重跑；先升 executor 再升 server（executor 兼容旧协议更稳）。
