# bigdata-adhoc

面向数据开发者的即席查询平台：一条 SQL，秒级提交，双引擎执行，结果落地对象存储。

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-8-green.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.5.6-green.svg)]()

## 特性

- **双引擎**：Kyuubi（Hive 协议）/ StarRocks（MySQL 协议），多实例路由、每实例独立账号
- **提交即走**：PENDING 秒级调度，CAS 无锁抢占，多 server 水平扩展
- **高可用**：session 绑定 executor，server 宕机不影响 RUNNING 任务；executor 宕机自动补偿
- **SQL 治理**：g4 解析拆段、危险语句拦截、SELECT 自动 LIMIT、结果复用
- **全链路可观测**：执行日志实时拉取、Job/Task 进度时间线、JVM/任务监控大盘
- **元数据补全**：库/表/列三级自动补全（Hive metastore / StarRocks）
- **存储可插拔**：内置 local / 阿里云 OSS，实现 `StorageClient` 接口即可扩展
- **Java SDK**：gRPC + REST 双协议

## 架构

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

| 模块 | 说明 |
|------|------|
| adhoc-server | 调度与任务分发（QueueWorker、JobLogCollector） |
| adhoc-executor | SQL 执行引擎（JobExecutionRunner、Kyuubi/StarRocks EngineExecutor） |
| adhoc-common | 公共组件（枚举、异常、工具类、DTO） |
| adhoc-dao | 数据访问层（Entity、Mapper） |
| adhoc-storage | 结果存储（StorageClient SPI：local / aliyun） |
| adhoc-sql-parser | SQL 解析与多段拆分 |
| adhoc-protocol | gRPC 协议定义 |
| adhoc-observability | 可观测性（指标、追踪） |
| adhoc-metadata | 元数据补全（Hive metastore / StarRocks） |
| adhoc-api-sdk | Java SDK（gRPC + REST） |
| adhoc-dependencies | 依赖管理 BOM |

## 快速开始

### 环境要求

- JDK 8、Maven 3.6+
- MySQL 8
- Apollo 配置中心（可选——不接 Apollo 时直接用本地 `application.yml`）
- Kyuubi 或 StarRocks 集群（执行引擎）

### 步骤

1. 建库：

```bash
mysql -e "CREATE DATABASE adhoc DEFAULT CHARACTER SET utf8mb4"
mysql adhoc < sql/create.sql
```

2. Apollo（可选）：创建应用 `bigdata-adhoc-server` / `bigdata-adhoc-executor`，
   或跳过 Apollo 直接用本地配置 + 环境变量。

3. 配置环境变量（全部有默认值，按实际环境覆盖）：

```bash
export ADHOC_MYSQL_HOST=localhost      # 元数据库
export ADHOC_MYSQL_PORT=3306
export ADHOC_MYSQL_DB=adhoc
export ADHOC_MYSQL_USER=root
export ADHOC_MYSQL_PASSWORD=change-me
export ADHOC_KYUUBI_ENDPOINTS=kyuubi-host:10009   # 引擎实例（executor 侧）
export ADHOC_SR_ENDPOINTS=starrocks-fe:9030
export ADHOC_SR_USER=root
export ADHOC_SR_PASSWORD=change-me
```

4. 启动：

```bash
mvn spring-boot:run -pl adhoc-server    # REST 8080 / gRPC 9090
mvn spring-boot:run -pl adhoc-executor  # REST 8081 / gRPC 9091
```

5. 提交第一条查询：

```bash
curl -X POST http://localhost:8080/api/job \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: user001" \
  -H "X-Adhoc-User-Name: 张三" \
  -d '{"sqlContent": "SELECT 1", "engineType": "STARROCKS"}'
# → {"code":1,"msg":"操作成功","data":{"jobId":"Job_xxx"}}

curl -X POST http://localhost:8080/api/job/status \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: user001" \
  -d '{"jobId": "Job_xxx"}'
```

SDK 方式：

```java
// gRPC
AdhocGrpcClient client = new AdhocGrpcClient("server-host", 9090);
JobSubmitResponse resp = client.submitJob(JobSubmitRequest.newBuilder()
        .setSqlContent("SELECT 1")
        .setEngineType("STARROCKS")
        .setUserId("user001")
        .setUserName("张三")
        .build());

// REST
HttpAdhocClient http = new HttpAdhocClient("http://server-host:8080", "user001", "张三");
JobSubmitResponse resp2 = http.submitJob("SELECT 1", "STARROCKS");
```

## 用户身份接入

平台自身不带登录体系，由前置网关/反向代理完成认证后注入标准身份头：

```nginx
location /adhoc/ {
    proxy_set_header X-Adhoc-User-Id   $remote_user_id;   # 网关侧解析后的用户标识
    proxy_set_header X-Adhoc-User-Name $remote_user_name; # 展示名（如中文姓名）
    proxy_pass http://adhoc-server:8080/;
}
```

⚠️ 生产必须保证 server 端口不对外网直连暴露，否则身份头可伪造。

## 存储

```yaml
adhoc.storage:
  type: local            # local（默认，开箱即用）
  local.base-dir: ./data/storage

  # 或 aliyun（生产推荐，对象存储服务化）
  # type: aliyun
  # aliyun.endpoint: https://oss-cn-hangzhou.aliyuncs.com
  # aliyun.bucket: bigdata-adhoc
  # aliyun.access-key: xxx
  # aliyun.secret-key: xxx
```

第三方存储自行实现 `io.gitee.songchaolin.adhoc.storage.spi.StorageClient` 并注册为 Spring Bean
（内置实现带 `@ConditionalOnMissingBean`，同容器内自定义 Bean 自动优先生效）。

## 配置

核心配置（各模块配置项说明见 [docs/design/](docs/design/)）：

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `spring.datasource.*` | 环境变量模板 | 元数据库（MySQL） |
| `adhoc.engine.KYUUBI.<instance>.endpoints` | `localhost:10009` | Kyuubi 实例，多实例各自账号 |
| `adhoc.engine.STARROCKS.<instance>.endpoints` | `localhost:9030` | StarRocks FE |
| `adhoc.engine.<ENGINE>.auth-mode` | FIXED | FIXED=统一账号 / USER=按提交人身份 |
| `adhoc.storage.type` | local | local / aliyun |
| `adhoc.server.schedule.interval-ms` | 2000 | 调度扫描间隔 |
| `adhoc.executor.max-concurrent-tasks` | - | executor 并发上限 |
| `adhoc.dashboard.access-token` | 空 | 监控大盘访问令牌（生产必改） |
| `adhoc.admin.user-ids` | 空 | 管理员用户 ID（cancel/归属校验放行） |

配置优先级：Apollo > 环境变量 > 本地 yml > 代码默认值，多数配置修改后无需重启。

## 监控大盘

启动后访问 `http://server-host:8080/dashboard`（首次带 `?token=<adhoc.dashboard.access-token>`），
提供集群指标、Job/Task 趋势、每实例负载、JVM 时序等视图。

## 文档

完整文档见 [docs/](docs/README.md)，核心入口：

- [接口文档](docs/api/README.md)：REST 接口总览（统一响应 / 鉴权 / 错误码）+ Job / 结果 / 日志 / 文件 / 元数据 / 大盘分篇详述
- [部署指南](docs/deployment.md)：生产多节点拓扑、Apollo、nginx 网关与用户头注入
- [配置参考](docs/configuration.md) 与 [Apollo 生产配置清单](docs/apollo-prod-config.md)
- [SDK 指南](docs/sdk.md) · [FAQ 与术语](docs/faq.md)
- [设计文档](docs/design/)：架构 / 调度执行 / SQL 解析 / 引擎路由 / 结果存储 / 高可用 / 可观测性 / 日志 / Schema

## Roadmap

- Kyuubi 查询超时控制
- 结果分块下载
- 更多存储后端

## 贡献

见 [CONTRIBUTING.md](./CONTRIBUTING.md)。Apache-2.0 开源协议，见 [LICENSE](./LICENSE)。
