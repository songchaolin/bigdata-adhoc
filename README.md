# bigdata-adhoc

[English](README.md) | [中文](README.zh-CN.md)

An ad-hoc query platform for data developers: one SQL, submitted in seconds, executed by a dual-engine, with results persisted to object storage.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-8-green.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.5.6-green.svg)]()

## Features

- **Dual engine**: Kyuubi (Hive protocol) / StarRocks (MySQL protocol), multi-instance routing, per-instance independent credentials
- **Submit and go**: PENDING scheduled in seconds, lock-free CAS preemption, multi-server horizontal scaling
- **High availability**: session bound to executor, server crash does not affect RUNNING tasks; executor crash auto-compensated
- **SQL governance**: g4 parsing & segmentation, dangerous-statement interception, auto LIMIT on SELECT, result reuse
- **Full-link observability**: real-time execution log fetching, Job/Task progress timeline, JVM/task monitoring dashboard
- **Metadata autocomplete**: database/table/column three-level autocomplete (Hive metastore / StarRocks)
- **Pluggable storage**: built-in local / Aliyun OSS; implement the `StorageClient` interface to extend
- **Java SDK**: gRPC + REST dual protocol

## Architecture

```
                 ┌──────────────────────────┐
   REST / SDK ──▶│  adhoc-server (scalable)   │──▶ MySQL (metadata)
                 │  scheduling / CAS preemption / log collection │
                 └───────────┬──────────────┘
                             │ gRPC (dispatch / fetchJobLog / heartbeat)
                 ┌───────────▼──────────────┐
                 │ adhoc-executor (scalable)  │──▶ Kyuubi / StarRocks
                 │  SQL segmentation / execution / result storage │
                 └───────────┬──────────────┘
                             │ StorageClient
                    local directory  /  Aliyun OSS
```

| Module | Description |
|------|------|
| adhoc-server | Scheduling & task dispatch (QueueWorker, JobLogCollector) |
| adhoc-executor | SQL execution engine (JobExecutionRunner, Kyuubi/StarRocks EngineExecutor) |
| adhoc-common | Common components (enums, exceptions, utilities, DTO) |
| adhoc-dao | Data access layer (Entity, Mapper) |
| adhoc-storage | Result storage (StorageClient SPI: local / aliyun) |
| adhoc-sql-parser | SQL parsing & multi-segment splitting |
| adhoc-protocol | gRPC protocol definitions |
| adhoc-observability | Observability (metrics, tracing) |
| adhoc-metadata | Metadata autocomplete (Hive metastore / StarRocks) |
| adhoc-api-sdk | Java SDK (gRPC + REST) |
| adhoc-dependencies | Dependency management BOM |

## Quick Start

### Prerequisites

- JDK 8, Maven 3.6+
- MySQL 8
- Apollo config center (optional — without Apollo, use local `application.yml` directly)
- Kyuubi or StarRocks cluster (execution engine)

### Steps

1. Create the database:

```bash
mysql -e "CREATE DATABASE adhoc DEFAULT CHARACTER SET utf8mb4"
mysql adhoc < sql/create.sql
```

2. Apollo (optional): create applications `bigdata-adhoc-server` / `bigdata-adhoc-executor`,
   or skip Apollo and use local config + environment variables directly.

3. Configure environment variables (all have defaults, override per your environment):

```bash
export ADHOC_MYSQL_HOST=localhost      # metadata database
export ADHOC_MYSQL_PORT=3306
export ADHOC_MYSQL_DB=adhoc
export ADHOC_MYSQL_USER=root
export ADHOC_MYSQL_PASSWORD=change-me
export ADHOC_KYUUBI_ENDPOINTS=kyuubi-host:10009   # engine instances (executor side)
export ADHOC_SR_ENDPOINTS=starrocks-fe:9030
export ADHOC_SR_USER=root
export ADHOC_SR_PASSWORD=change-me
```

4. Start:

```bash
mvn spring-boot:run -pl adhoc-server    # REST 8080 / gRPC 9090
mvn spring-boot:run -pl adhoc-executor  # REST 8081 / gRPC 9091
```

5. Submit your first query:

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

Via SDK:

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

## User Identity Integration

The platform itself has no login system; authentication is done by a fronting gateway / reverse proxy, which then injects standard identity headers:

```nginx
location /adhoc/ {
    proxy_set_header X-Adhoc-User-Id   $remote_user_id;   # user id resolved by the gateway
    proxy_set_header X-Adhoc-User-Name $remote_user_name; # display name (e.g. full name)
    proxy_pass http://adhoc-server:8080/;
}
```

⚠️ In production, the server port must not be exposed directly to the public internet, otherwise the identity headers can be forged.

## Storage

```yaml
adhoc.storage:
  type: local            # local (default, out-of-the-box)
  local.base-dir: ./data/storage

  # or aliyun (recommended for production, object-storage as a service)
  # type: aliyun
  # aliyun.endpoint: https://oss-cn-hangzhou.aliyuncs.com
  # aliyun.bucket: bigdata-adhoc
  # aliyun.access-key: xxx
  # aliyun.secret-key: xxx
```

For third-party storage, implement `io.gitee.songchaolin.adhoc.storage.spi.StorageClient` and register it as a Spring Bean
(the built-in implementations carry `@ConditionalOnMissingBean`, so a custom Bean in the same container takes precedence automatically).

## Configuration

Core config (per-module config items are documented in [docs/design/](docs/en/design/)):

| Config | Default | Description |
|------|--------|------|
| `spring.datasource.*` | env var template | Metadata database (MySQL) |
| `adhoc.engine.KYUUBI.<instance>.endpoints` | `localhost:10009` | Kyuubi instance, each with its own credentials |
| `adhoc.engine.STARROCKS.<instance>.endpoints` | `localhost:9030` | StarRocks FE |
| `adhoc.engine.<ENGINE>.auth-mode` | FIXED | FIXED=unified account / USER=by submitter identity |
| `adhoc.storage.type` | local | local / aliyun |
| `adhoc.server.schedule.interval-ms` | 2000 | Scheduling scan interval |
| `adhoc.executor.max-concurrent-tasks` | - | Executor concurrency limit |
| `adhoc.dashboard.access-token` | empty | Monitoring dashboard access token (must change in production) |
| `adhoc.admin.user-ids` | empty | Admin user IDs (cancel/ownership-check bypass) |

Config priority: Apollo > env vars > local yml > code defaults; most configs take effect without restart.

## Monitoring Dashboard

After startup, visit `http://server-host:8080/dashboard` (first time with `?token=<adhoc.dashboard.access-token>`),
providing cluster metrics, Job/Task trends, per-instance load, JVM time series, and more.

## Documentation

Full docs at [docs/](docs/en/README.md). Key entry points:

- [API reference](docs/en/api/README.md): REST endpoint overview (unified response / auth / error codes) + per-area detail for Job / Result / Log / File / Metadata / Dashboard
- [Deployment guide](docs/en/deployment.md): production multi-node topology, Apollo, nginx gateway & user-header injection
- [Configuration reference](docs/en/configuration.md)
- [SDK guide](docs/en/sdk.md) · [FAQ & glossary](docs/en/faq.md)
- [Design docs](docs/en/design/): architecture / scheduling & execution / SQL parsing / engine routing / result storage / high availability / observability / logs / schema

## Roadmap

- Kyuubi query timeout control
- Chunked result download
- More storage backends

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md). Licensed under Apache-2.0, see [LICENSE](./LICENSE).
