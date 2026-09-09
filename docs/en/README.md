# bigdata-adhoc · Documentation Index

[English](README.md) | [中文](../zh/README.md)

## User Manual

| Doc | Description |
|---|---|
| [API reference](api/README.md) | REST API overview: unified response, authentication, error codes, pagination conventions; per-area detail for Job/Result/Log/File/Metadata/Dashboard endpoints |
| [Deployment guide](deployment.md) | Production deployment: multi-node topology, Apollo integration, nginx gateway & user-header injection, storage selection |
| [Configuration reference](configuration.md) | Config priority & effective mechanism, common config quick-reference |
| [SDK guide](sdk.md) | Java SDK: REST/gRPC dual-protocol client, method reference, full examples |
| [FAQ & glossary](faq.md) | Frequently asked questions, terminology table |

API reference sections:

| Doc | Content |
|---|---|
| [api/job.md](api/job.md) | Job submit / paginate / detail / status / progress / cancel, Task detail |
| [api/result.md](api/result.md) | Task result pagination, Job result aggregation |
| [api/log.md](api/log.md) | Task / Job execution log (incremental polling) |
| [api/file.md](api/file.md) | File nodes (SQL script directory tree) |
| [api/metadata.md](api/metadata.md) | Metadata autocomplete, runtime config query |
| [api/dashboard.md](api/dashboard.md) | Monitoring dashboard & SQL console endpoints |

## Design Docs (docs/design/)

For contributors and secondary developers; explains internal module mechanisms.

| Doc | Description |
|---|---|
| [00-architecture-overview.md](design/00-architecture-overview.md) | Overall architecture: module division, deployment topology, design principles |
| [01-task-scheduling-execution.md](design/01-task-scheduling-execution.md) | Scheduling & execution: QueueWorker, JobExecutionRunner, Task lifecycle |
| [02-sql-parsing-governance.md](design/02-sql-parsing-governance.md) | SQL parsing & governance: g4 parsing, SqlType classification, governance rules |
| [03-engine-routing.md](design/03-engine-routing.md) | Engine routing: Kyuubi/StarRocks selection, multi-instance, proxy user |
| [04-result-storage.md](design/04-result-storage.md) | Result storage: StorageClient SPI, local/aliyun implementations, serialization format |
| [05-high-availability.md](design/05-high-availability.md) | High availability: server/executor crash compensation, reconcile mechanism |
| [06-observability.md](design/06-observability.md) | Observability: metrics, tracing, log conventions |
| [08-directory-tree.md](design/08-directory-tree.md) | Directory structure: full project directory tree |
| [09-execution-log.md](design/09-execution-log.md) | Execution log: log format, collection mechanism, COMPLETE_MARKER |
| [10-limitations.md](design/10-limitations.md) | Limitations & constraints: known limits, future plans |
| [11-schema.md](design/11-schema.md) | Database schema: full DDL, index notes |
| [12-sdk.md](design/12-sdk.md) | SDK design: Java SDK interface, gRPC/REST protocol |

## Other References

| Path | Content |
|---|---|
| [../../sql/create.sql](../../sql/create.sql) | Table-creation DDL (idempotent, with indexes & comments) |
| [../../CONTRIBUTING.md](../../CONTRIBUTING.md) | Contributing guide |

## Reading Suggestions

- **Users / integration developers**: [Quick start](../../README.md) → [API reference](api/README.md) → [SDK guide](sdk.md)
- **Ops / deployment**: [Deployment guide](deployment.md) → [Configuration reference](configuration.md)
- **Source contributors / secondary development**: [design/00 architecture](design/00-architecture-overview.md) → [01 scheduling & execution](design/01-task-scheduling-execution.md) → [04 result storage](design/04-result-storage.md)
