-- ============================================================================
-- 目录树全局根改造：用户身份透传 + 全局根 + USER_ROOT 标记
-- 背景：原设计每用户独立根（parent_node_id IS NULL, node_name='root', node_type='DIRECTORY'），
--       单用户占位 user_id='scl'。改造为一棵全局树：
--         全局根（node_id='root_global', parent=NULL, user_id='__system__'）
--           └── 各用户 USER_ROOT（node_type='USER_ROOT', node_name='root_'+userId）
--                 └── 用户内容（DIRECTORY/FILE）
-- 幂等：可重复执行，已迁移数据不会二次变更。
-- 说明：应用层 FileNodeService.ensureUserRootExists 亦做同样自愈（按用户懒触发），
--       本脚本供 DBA 一次性显式迁移存量数据，二者效果等价。
-- ============================================================================

-- 1. 创建全局根（固定 ID，单例）
INSERT INTO adhoc_file_node (node_id, user_id, user_name, parent_node_id, node_type, node_name, is_deleted, create_time, update_time)
SELECT 'root_global', '__system__', '系统', NULL, 'DIRECTORY', 'root', 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM adhoc_file_node WHERE node_id = 'root_global');

-- 2. 迁移旧单用户根：parent=NULL + name='root' + DIRECTORY
--    -> 挂到全局根下，类型改 USER_ROOT，命名 root_+userId
UPDATE adhoc_file_node
SET parent_node_id = 'root_global',
    node_type      = 'USER_ROOT',
    node_name      = CONCAT('root_', user_id),
    update_time    = NOW()
WHERE parent_node_id IS NULL
  AND node_name = 'root'
  AND node_type = 'DIRECTORY'
  AND user_id <> '__system__'
  AND node_id <> 'root_global'
  AND is_deleted = 0;
