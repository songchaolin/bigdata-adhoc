-- ============================================================================
-- adhoc_server_instance 加 grpc_port 列（executor DB 发现活 server 心跳目标用）
-- 背景：server 表原本只有 http_port，executor 无法从 DB 拿 server 的 gRPC 地址做动态心跳。
-- 生产一次性执行；dev 亦用此脚本（或重建表）。
-- ============================================================================

ALTER TABLE adhoc_server_instance
  ADD COLUMN grpc_port INT NOT NULL DEFAULT 9090 COMMENT 'gRPC 端口';