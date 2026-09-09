# Terminology Glossary

> Bilingual term reference. Every English doc in `docs/en/` MUST use the English term in the right column for the corresponding Chinese concept. This prevents terminology drift (e.g. one doc saying "scheduling", another "dispatch" for the same `QueueWorker` concept).

| 中文 | English term | Notes |
|---|---|---|
| 即席查询 | ad-hoc query | core project noun |
| 调度 | scheduling | `QueueWorker.dispatcher()` performs dispatch, but the phase = scheduling |
| 抢占 | claim / CAS preemption | `claimJob()` method; concept = lock-free CAS preemption |
| 派发 / 下发 | dispatch | the gRPC act of sending a job to an executor |
| 拆段 / SQL 拆分 | SQL segmentation | splitting one SQL script into executable segments |
| 段 | segment | a single `ProcessedSqlSegment` |
| 结果复用 | result reuse | cache hit on identical SQL hash |
| 降级 | fallback / graceful degradation | |
| 代理用户 | proxy user | Kyuubi `hive.server2.proxy.user` |
| 引擎路由 | engine routing | `EngineSelector` |
| 结果存储 | result storage | |
| 高可用 | high availability (HA) | |
| 补偿 | reconciliation / compensation | executor-side log backfill = compensation |
| 兜底 | safety net / last-resort backfill | |
| 治理 | governance | SQL governance |
| 危险语句拦截 | dangerous-statement interception | |
| 结果落地 | persist results to | |
| 监控大盘 | monitoring dashboard | |
| 进度时间线 | progress timeline | Job/Task two-level DAG |
| 元数据补全 | metadata autocomplete | database/table/column |
| 归属校验 | ownership check | |
| 终态 | terminal state | |
| 失败传播 | failure propagation | priorFailed → skip downstream tasks |
| 跳过 | skip | `markSkipped` |
| 取消 | cancel | `cancel_requested` flag |
| 存储可插拔 | pluggable storage | `StorageClient` SPI |

**Verb forms are fine** (scheduling/dispatching/claiming). The lock is on the **noun concept**: when naming the `QueueWorker` phase, always "scheduling", never "dispatch"; when naming the gRPC send act, always "dispatch".
