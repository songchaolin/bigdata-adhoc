package io.gitee.songchaolin.adhoc.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.songchaolin.adhoc.common.constant.FileNodeConstants;
import io.gitee.songchaolin.adhoc.common.dto.JobSubmitResponse;
import io.gitee.songchaolin.adhoc.common.dto.request.*;
import io.gitee.songchaolin.adhoc.common.dto.response.FileNodeResponse;
import io.gitee.songchaolin.adhoc.common.enums.DeletedStatus;
import io.gitee.songchaolin.adhoc.common.enums.NodeType;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocFileNode;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocFileNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 目录树服务。
 * <p>
 * 拓扑：一棵全局树。全局根（{@link FileNodeConstants#GLOBAL_ROOT_NODE_ID}，parent=NULL，
 * 系统用户）下挂每个用户的 USER_ROOT（node_type=USER_ROOT，命名 root_+userId），用户内容
 * 在各自 USER_ROOT 之下。每用户仅可见/操作自己 USER_ROOT 子树：by-nodeId 操作做归属校验，
 * 非本人节点一律按 {@link AdhocErrorCode#ADHOC_NODE_NOT_FOUND} 处理（不泄漏存在性）。
 * <p>
 * 为未来"分享"打地基：分享表与可见性策略独立于本拓扑，后续叠加（被分享节点在归属校验处放行即可）。
 */
@Service
public class FileNodeService {

    private static final Logger log = LoggerFactory.getLogger(FileNodeService.class);

    private final AdhocFileNodeMapper fileNodeMapper;
    private final JobService jobService;

    public FileNodeService(AdhocFileNodeMapper fileNodeMapper, JobService jobService) {
        this.fileNodeMapper = fileNodeMapper;
        this.jobService = jobService;
    }

    // === 公共方法 ===

    /**
     * 获取当前用户的完整目录树。
     * <p>
     * 返回用户根（USER_ROOT）本身作为顶层节点（即使无子节点也返回这一层），子节点递归挂载在其 children 下。
     */
    public List<FileNodeResponse> getTree(String userId, String userName) {
        AdhocFileNode userRoot = ensureUserRootExists(userId, userName);

        // 查询用户所有未删除节点
        List<AdhocFileNode> allNodes = fileNodeMapper.selectList(
                new LambdaQueryWrapper<AdhocFileNode>()
                        .eq(AdhocFileNode::getUserId, userId)
                        .eq(AdhocFileNode::getIsDeleted, DeletedStatus.NOT_DELETED.getCode()));

        // 返回用户根本身作为顶层，子节点递归挂载
        FileNodeResponse rootResponse = toResponse(userRoot);
        rootResponse.setChildren(buildTree(allNodes, userRoot.getNodeId()));
        // 用户根对前端即为顶层根：抹去其 parent（全局根）引用，不向用户暴露全局树结构
        rootResponse.setParentNodeId(null);

        List<FileNodeResponse> result = new ArrayList<>();
        result.add(rootResponse);
        return result;
    }

    /**
     * 列出子节点（单层）。parentNodeId 为空则列出 USER_ROOT 下的直接子节点。
     */
    public List<FileNodeResponse> listNodes(String parentNodeId, String userId, String userName) {
        // 如果 parentNodeId 为空，查找用户根
        if (parentNodeId == null || parentNodeId.isEmpty()) {
            AdhocFileNode userRoot = ensureUserRootExists(userId, userName);
            parentNodeId = userRoot.getNodeId();
        }

        // 按 userId 隔离：非本人节点下的查询天然返回空（其子节点 user_id 不匹配）
        List<AdhocFileNode> nodes = fileNodeMapper.selectList(
                new LambdaQueryWrapper<AdhocFileNode>()
                        .eq(AdhocFileNode::getUserId, userId)
                        .eq(AdhocFileNode::getParentNodeId, parentNodeId)
                        .eq(AdhocFileNode::getIsDeleted, DeletedStatus.NOT_DELETED.getCode())
                        .orderByAsc(AdhocFileNode::getNodeType)
                        .orderByAsc(AdhocFileNode::getNodeName));

        return nodes.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 查询节点详情（仅本人节点可见）。
     */
    public FileNodeResponse getNode(String nodeId, String userId) {
        AdhocFileNode node = requireOwnedNode(nodeId, userId);
        if (DeletedStatus.DELETED.is(node.getIsDeleted())) {
            throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NOT_FOUND);
        }
        return toResponse(node);
    }

    /**
     * 创建节点
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeResponse createNode(CreateNodeRequest req, String userId, String userName) {
        // 参数校验
        validateCreateNodeRequest(req);

        // 处理父节点
        String parentNodeId = req.getParentNodeId();
        AdhocFileNode parent;
        if (parentNodeId == null || parentNodeId.isEmpty()) {
            // 挂到 USER_ROOT 下：目录或文件均可直接创建，无需先建目录
            parent = ensureUserRootExists(userId, userName);
        } else {
            // 校验父节点存在且属于本人，且为容器类型（文件/目录均可挂载其下）
            parent = requireOwnedNode(parentNodeId, userId);
            if (!NodeType.isContainer(parent.getNodeType())) {
                throw new AdhocException(AdhocErrorCode.ADHOC_PARENT_NOT_DIRECTORY);
            }
        }

        // 检查同名节点
        if (existsSameNameNode(userId, parent.getNodeId(), req.getNodeName(), null)) {
            throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NAME_DUPLICATE);
        }

        // 创建节点
        AdhocFileNode node = new AdhocFileNode();
        node.setNodeId("node_" + UUID.randomUUID().toString().replace("-", ""));
        node.setUserId(userId);
        node.setUserName(userName);
        node.setParentNodeId(parent.getNodeId());
        node.setNodeType(req.getNodeType());
        node.setNodeName(req.getNodeName());
        node.setSqlContent(req.getSqlContent());
        node.setDescription(req.getDescription());
        node.setIsDeleted(DeletedStatus.NOT_DELETED.getCode());
        node.setCreateTime(new Date());
        node.setUpdateTime(new Date());

        fileNodeMapper.insert(node);

        log.info("【创建节点】userId={} nodeId={} 类型={} 名称={}", userId, node.getNodeId(), node.getNodeType(), node.getNodeName());

        return toResponse(node);
    }

    /**
     * 重命名节点
     */
    @Transactional(rollbackFor = Exception.class)
    public Boolean renameNode(String nodeId, String newName, String userId) {
        AdhocFileNode node = requireOwnedNode(nodeId, userId);

        // 根目录不可重命名
        if (isRootNode(node)) {
            throw new AdhocException(AdhocErrorCode.ADHOC_ROOT_NODE_IMMUTABLE);
        }

        // 校验名称格式
        validateNodeName(newName);

        // 检查同名节点
        if (existsSameNameNode(userId, node.getParentNodeId(), newName, nodeId)) {
            throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NAME_DUPLICATE);
        }

        fileNodeMapper.update(null, new LambdaUpdateWrapper<AdhocFileNode>()
                .eq(AdhocFileNode::getNodeId, nodeId)
                .set(AdhocFileNode::getNodeName, newName)
                .set(AdhocFileNode::getUpdateTime, new Date()));

        log.info("【重命名节点】nodeId={} 新名称={}", nodeId, newName);

        return true;
    }

    /**
     * 移动节点
     */
    @Transactional(rollbackFor = Exception.class)
    public Boolean moveNode(String nodeId, String newParentNodeId, String userId) {
        AdhocFileNode node = requireOwnedNode(nodeId, userId);

        // 根目录不可移动
        if (isRootNode(node)) {
            throw new AdhocException(AdhocErrorCode.ADHOC_ROOT_NODE_IMMUTABLE);
        }

        // 校验目标父节点存在且属于本人
        AdhocFileNode newParent = requireOwnedNode(newParentNodeId, userId);
        if (!NodeType.isContainer(newParent.getNodeType())) {
            throw new AdhocException(AdhocErrorCode.ADHOC_PARENT_NOT_DIRECTORY);
        }

        // 不能移动到自己或子目录
        if (nodeId.equals(newParentNodeId) || isChildOf(newParentNodeId, nodeId)) {
            throw new AdhocException(AdhocErrorCode.ADHOC_MOVE_TO_SELF_OR_CHILD);
        }

        // 检查目标目录下同名节点
        if (existsSameNameNode(userId, newParentNodeId, node.getNodeName(), nodeId)) {
            throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NAME_DUPLICATE);
        }

        fileNodeMapper.update(null, new LambdaUpdateWrapper<AdhocFileNode>()
                .eq(AdhocFileNode::getNodeId, nodeId)
                .set(AdhocFileNode::getParentNodeId, newParentNodeId)
                .set(AdhocFileNode::getUpdateTime, new Date()));

        log.info("【移动节点】nodeId={} 新父节点={}", nodeId, newParentNodeId);

        return true;
    }

    /**
     * 删除节点（软删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteNode(String nodeId, String userId) {
        AdhocFileNode node = requireOwnedNode(nodeId, userId);

        // 根目录不可删除
        if (isRootNode(node)) {
            throw new AdhocException(AdhocErrorCode.ADHOC_ROOT_NODE_IMMUTABLE);
        }

        // 容器类型：检查是否有子节点
        if (NodeType.isContainer(node.getNodeType())) {
            Long childCount = fileNodeMapper.selectCount(
                    new LambdaQueryWrapper<AdhocFileNode>()
                            .eq(AdhocFileNode::getParentNodeId, nodeId)
                            .eq(AdhocFileNode::getIsDeleted, DeletedStatus.NOT_DELETED.getCode()));
            if (childCount > 0) {
                throw new AdhocException(AdhocErrorCode.ADHOC_DIRECTORY_NOT_EMPTY);
            }
        }

        fileNodeMapper.update(null, new LambdaUpdateWrapper<AdhocFileNode>()
                .eq(AdhocFileNode::getNodeId, nodeId)
                .set(AdhocFileNode::getIsDeleted, DeletedStatus.DELETED.getCode())
                .set(AdhocFileNode::getUpdateTime, new Date()));

        log.info("【删除节点】nodeId={}", nodeId);

        return true;
    }

    /**
     * 恢复节点
     */
    @Transactional(rollbackFor = Exception.class)
    public Boolean restoreNode(String nodeId, String userId) {
        // restoreNode 需操作已删除节点，放行 deleted 状态
        AdhocFileNode node = requireOwnedNode(nodeId, userId, true);
        if (DeletedStatus.NOT_DELETED.is(node.getIsDeleted())) {
            throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NOT_FOUND, "节点未删除或不存在");
        }

        // 校验父节点未删除
        if (node.getParentNodeId() != null) {
            AdhocFileNode parent = fileNodeMapper.selectById(node.getParentNodeId());
            if (parent != null && DeletedStatus.DELETED.is(parent.getIsDeleted())) {
                throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NOT_FOUND, "父节点已删除，无法恢复");
            }
        }

        fileNodeMapper.update(null, new LambdaUpdateWrapper<AdhocFileNode>()
                .eq(AdhocFileNode::getNodeId, nodeId)
                .set(AdhocFileNode::getIsDeleted, DeletedStatus.NOT_DELETED.getCode())
                .set(AdhocFileNode::getUpdateTime, new Date()));

        log.info("【恢复节点】nodeId={}", nodeId);

        return true;
    }

    /**
     * 更新节点内容
     * <p>
     * 支持更新：
     * - 节点名称（可选）
     * - SQL 内容（FILE 类型）
     * - 描述信息
     *
     * @param req    更新请求
     * @param userId 调用者用户 ID
     * @return 更新成功返回 true
     */
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateNode(UpdateNodeRequest req, String userId) {
        AdhocFileNode node = requireOwnedNode(req.getNodeId(), userId);

        // 根目录不可更新
        if (isRootNode(node)) {
            throw new AdhocException(AdhocErrorCode.ADHOC_ROOT_NODE_IMMUTABLE);
        }

        // 更新节点名称（如果提供）
        if (req.getNodeName() != null && !req.getNodeName().isEmpty()) {
            validateNodeName(req.getNodeName());

            // 检查同名节点
            if (existsSameNameNode(userId, node.getParentNodeId(), req.getNodeName(), req.getNodeId())) {
                throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NAME_DUPLICATE);
            }

            fileNodeMapper.update(null, new LambdaUpdateWrapper<AdhocFileNode>()
                    .eq(AdhocFileNode::getNodeId, req.getNodeId())
                    .set(AdhocFileNode::getNodeName, req.getNodeName())
                    .set(AdhocFileNode::getUpdateTime, new Date()));
        }

        // 更新 SQL 内容（仅 FILE 类型）
        if (req.getSqlContent() != null) {
            if (!NodeType.FILE.is(node.getNodeType())) {
                throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NOT_FOUND, "只有 FILE 类型节点可以更新 SQL 内容");
            }

            fileNodeMapper.update(null, new LambdaUpdateWrapper<AdhocFileNode>()
                    .eq(AdhocFileNode::getNodeId, req.getNodeId())
                    .set(AdhocFileNode::getSqlContent, req.getSqlContent())
                    .set(AdhocFileNode::getUpdateTime, new Date()));
        }

        // 更新描述
        if (req.getDescription() != null) {
            fileNodeMapper.update(null, new LambdaUpdateWrapper<AdhocFileNode>()
                    .eq(AdhocFileNode::getNodeId, req.getNodeId())
                    .set(AdhocFileNode::getDescription, req.getDescription())
                    .set(AdhocFileNode::getUpdateTime, new Date()));
        }

        log.info("【更新节点】nodeId={} 名称={} SQL长度={}", req.getNodeId(), req.getNodeName(),
                req.getSqlContent() != null ? req.getSqlContent().length() : 0);

        return true;
    }

    /**
     * 搜索节点（仅本人节点）
     */
    public List<FileNodeResponse> searchNodes(String keyword, String type, String userId) {
        LambdaQueryWrapper<AdhocFileNode> wrapper = new LambdaQueryWrapper<AdhocFileNode>()
                .eq(AdhocFileNode::getUserId, userId)
                .eq(AdhocFileNode::getIsDeleted, DeletedStatus.NOT_DELETED.getCode())
                .like(AdhocFileNode::getNodeName, keyword);

        if (type != null && !type.isEmpty()) {
            wrapper.eq(AdhocFileNode::getNodeType, type);
        }

        wrapper.orderByDesc(AdhocFileNode::getUpdateTime);

        List<AdhocFileNode> nodes = fileNodeMapper.selectList(wrapper);

        return nodes.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 从文件节点提交查询（以调用者身份提交 Job）
     */
    public JobSubmitResponse submitByFile(SubmitByFileRequest req, String userId, String userName) {
        // 校验节点存在、属于本人且为文件类型
        AdhocFileNode node = requireOwnedNode(req.getNodeId(), userId);
        if (!NodeType.FILE.is(node.getNodeType())) {
            throw new AdhocException(AdhocErrorCode.ADHOC_PARENT_NOT_DIRECTORY, "只能从文件节点提交查询");
        }

        // 构造 JobSubmitRequest
        JobSubmitRequest jobReq = new JobSubmitRequest();
        jobReq.setSqlContent(req.getSqlContent());
        jobReq.setEngineType(req.getEngineType());
        jobReq.setEngineInstance(req.getEngineInstance());  // 透传实例名（空则 executor 走默认实例）

        // 调用 JobService 提交（用调用者身份，而非占位用户）
        return jobService.submit(jobReq, userId, userName);
    }

    // === 私有方法 ===

    /**
     * 取本人节点：不存在或不属于本人一律按 {@link AdhocErrorCode#ADHOC_NODE_NOT_FOUND} 处理（不泄漏存在性）。
     *
     * @param allowDeleted true=放行已删除节点（restoreNode 用）；false=已删除同样视为不存在
     */
    private AdhocFileNode requireOwnedNode(String nodeId, String userId, boolean allowDeleted) {
        AdhocFileNode node = fileNodeMapper.selectById(nodeId);
        if (node == null || !userId.equals(node.getUserId())) {
            throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NOT_FOUND);
        }
        if (!allowDeleted && DeletedStatus.DELETED.is(node.getIsDeleted())) {
            throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NOT_FOUND);
        }
        return node;
    }

    /** {@link #requireOwnedNode(String, String, boolean)} 的默认重载：已删除视为不存在 */
    private AdhocFileNode requireOwnedNode(String nodeId, String userId) {
        return requireOwnedNode(nodeId, userId, false);
    }

    /**
     * 确保全局根存在（单例，固定 ID）。
     */
    private AdhocFileNode ensureGlobalRootExists() {
        AdhocFileNode globalRoot = fileNodeMapper.selectById(FileNodeConstants.GLOBAL_ROOT_NODE_ID);
        if (globalRoot != null) {
            return globalRoot;
        }
        // 兜底：按 parent IS NULL + 系统用户查找（兼容非固定 ID 的历史数据）
        globalRoot = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<AdhocFileNode>()
                        .isNull(AdhocFileNode::getParentNodeId)
                        .eq(AdhocFileNode::getUserId, FileNodeConstants.SYSTEM_USER_ID)
                        .eq(AdhocFileNode::getIsDeleted, DeletedStatus.NOT_DELETED.getCode()));
        if (globalRoot != null) {
            return globalRoot;
        }

        // 创建全局根
        globalRoot = new AdhocFileNode();
        globalRoot.setNodeId(FileNodeConstants.GLOBAL_ROOT_NODE_ID);
        globalRoot.setUserId(FileNodeConstants.SYSTEM_USER_ID);
        globalRoot.setUserName(FileNodeConstants.SYSTEM_USER_NAME);
        globalRoot.setParentNodeId(null);
        globalRoot.setNodeType(NodeType.DIRECTORY.name());
        globalRoot.setNodeName(FileNodeConstants.ROOT_NODE_NAME);
        globalRoot.setIsDeleted(DeletedStatus.NOT_DELETED.getCode());
        globalRoot.setCreateTime(new Date());
        globalRoot.setUpdateTime(new Date());

        try {
            fileNodeMapper.insert(globalRoot);
            log.info("【创建全局根】nodeId={}", globalRoot.getNodeId());
        } catch (DuplicateKeyException e) {
            // 并发：另一线程已创建全局根，回查返回已存在记录（幂等，不抛给前端）
            log.warn("【创建全局根并发冲突】回查已存在记录");
            AdhocFileNode existing = fileNodeMapper.selectById(FileNodeConstants.GLOBAL_ROOT_NODE_ID);
            if (existing != null) {
                return existing;
            }
            throw e;
        }

        return globalRoot;
    }

    /**
     * 确保用户根（USER_ROOT）存在。
     * <p>
     * 兼容旧数据：旧 root 为 parent=NULL + name=root + DIRECTORY，在此一次性迁移为 USER_ROOT
     * 并挂到全局根下（与应用层迁移脚本等价，按用户懒触发）。
     */
    private AdhocFileNode ensureUserRootExists(String userId, String userName) {
        AdhocFileNode userRoot = getRootNode(userId);
        if (userRoot != null) {
            return userRoot;
        }

        AdhocFileNode globalRoot = ensureGlobalRootExists();
//        String userRootName = FileNodeConstants.USER_ROOT_NAME_PREFIX + userId;
//        String userRootName = userName + userId;
        String userRootName = String.format("%s(%s)", userName, userId);
        // 旧数据迁移：parent=NULL + name=root + DIRECTORY 的单用户根
        AdhocFileNode legacy = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<AdhocFileNode>()
                        .eq(AdhocFileNode::getUserId, userId)
                        .isNull(AdhocFileNode::getParentNodeId)
                        .eq(AdhocFileNode::getNodeName, FileNodeConstants.ROOT_NODE_NAME)
                        .eq(AdhocFileNode::getNodeType, NodeType.DIRECTORY.name())
                        .eq(AdhocFileNode::getIsDeleted, DeletedStatus.NOT_DELETED.getCode()));
        if (legacy != null) {
            fileNodeMapper.update(null, new LambdaUpdateWrapper<AdhocFileNode>()
                    .eq(AdhocFileNode::getNodeId, legacy.getNodeId())
                    .set(AdhocFileNode::getParentNodeId, globalRoot.getNodeId())
                    .set(AdhocFileNode::getNodeType, NodeType.USER_ROOT.name())
                    .set(AdhocFileNode::getNodeName, userRootName)
                    .set(AdhocFileNode::getUserName, userName)
                    .set(AdhocFileNode::getUpdateTime, new Date()));
            log.info("【迁移用户根】userId={} nodeId={} 挂到全局根下", userId, legacy.getNodeId());
            return fileNodeMapper.selectById(legacy.getNodeId());
        }

        // 新建用户根
        userRoot = new AdhocFileNode();
        userRoot.setNodeId("root_" + UUID.randomUUID().toString().replace("-", ""));
        userRoot.setUserId(userId);
        userRoot.setUserName(userName);
        userRoot.setParentNodeId(globalRoot.getNodeId());
        userRoot.setNodeType(NodeType.USER_ROOT.name());
        userRoot.setNodeName(userRootName);
        userRoot.setIsDeleted(DeletedStatus.NOT_DELETED.getCode());
        userRoot.setCreateTime(new Date());
        userRoot.setUpdateTime(new Date());

        try {
            fileNodeMapper.insert(userRoot);
            log.info("【创建用户根】userId={} nodeId={}", userId, userRoot.getNodeId());
        } catch (DuplicateKeyException e) {
            // 并发或历史脏数据（同名节点类型非 USER_ROOT）触发唯一键冲突：回查并归一为 USER_ROOT
            log.warn("【创建用户根冲突】userId={} 回查已存在记录", userId);
            AdhocFileNode existing = fileNodeMapper.selectOne(
                    new LambdaQueryWrapper<AdhocFileNode>()
                            .eq(AdhocFileNode::getUserId, userId)
                            .eq(AdhocFileNode::getParentNodeId, globalRoot.getNodeId())
                            .eq(AdhocFileNode::getNodeName, userRootName)
                            .eq(AdhocFileNode::getIsDeleted, DeletedStatus.NOT_DELETED.getCode()));
            if (existing != null) {
                // 历史脏数据：同名但类型非 USER_ROOT，归一为 USER_ROOT（自愈，无需人工清数据）
                if (!NodeType.USER_ROOT.is(existing.getNodeType())) {
                    fileNodeMapper.update(null, new LambdaUpdateWrapper<AdhocFileNode>()
                            .eq(AdhocFileNode::getNodeId, existing.getNodeId())
                            .set(AdhocFileNode::getNodeType, NodeType.USER_ROOT.name())
                            .set(AdhocFileNode::getUserName, userName)
                            .set(AdhocFileNode::getUpdateTime, new Date()));
                    log.info("【归一用户根】userId={} nodeId={} 原 type={} -> USER_ROOT",
                            userId, existing.getNodeId(), existing.getNodeType());
                    existing.setNodeType(NodeType.USER_ROOT.name());
                }
                return existing;
            }
            throw e;
        }

        return userRoot;
    }

    /**
     * 获取用户根（USER_ROOT）
     */
    private AdhocFileNode getRootNode(String userId) {
        return fileNodeMapper.selectOne(
                new LambdaQueryWrapper<AdhocFileNode>()
                        .eq(AdhocFileNode::getUserId, userId)
                        .eq(AdhocFileNode::getNodeType, NodeType.USER_ROOT.name())
                        .eq(AdhocFileNode::getIsDeleted, DeletedStatus.NOT_DELETED.getCode()));
    }

    /**
     * 是否为根节点：全局根（parent=NULL）或用户根（USER_ROOT）
     */
    private boolean isRootNode(AdhocFileNode node) {
        return node.getParentNodeId() == null
                || NodeType.USER_ROOT.is(node.getNodeType());
    }

    /**
     * 判断 target 是否是 parent 的子节点（递归）
     */
    private boolean isChildOf(String targetNodeId, String parentNodeId) {
        AdhocFileNode target = fileNodeMapper.selectById(targetNodeId);
        while (target != null && target.getParentNodeId() != null) {
            if (parentNodeId.equals(target.getParentNodeId())) {
                return true;
            }
            target = fileNodeMapper.selectById(target.getParentNodeId());
        }
        return false;
    }

    /**
     * 检查同名节点是否存在
     */
    private boolean existsSameNameNode(String userId, String parentNodeId, String nodeName, String excludeNodeId) {
        LambdaQueryWrapper<AdhocFileNode> wrapper = new LambdaQueryWrapper<AdhocFileNode>()
                .eq(AdhocFileNode::getUserId, userId)
                .eq(AdhocFileNode::getParentNodeId, parentNodeId)
                .eq(AdhocFileNode::getNodeName, nodeName)
                .eq(AdhocFileNode::getIsDeleted, DeletedStatus.NOT_DELETED.getCode());

        if (excludeNodeId != null) {
            wrapper.ne(AdhocFileNode::getNodeId, excludeNodeId);
        }

        return fileNodeMapper.selectCount(wrapper) > 0;
    }

    /**
     * 校验创建节点请求
     */
    private void validateCreateNodeRequest(CreateNodeRequest req) {
        // nodeType 必填
        if (req.getNodeType() == null || req.getNodeType().isEmpty()) {
            throw new AdhocException(AdhocErrorCode.ADHOC_ENGINE_TYPE_REQUIRED, "节点类型不能为空");
        }

        // nodeType 合法性（USER_ROOT 由系统创建，用户不可直接创建）
        if (!NodeType.DIRECTORY.is(req.getNodeType()) && !NodeType.FILE.is(req.getNodeType())) {
            throw new AdhocException(AdhocErrorCode.ADHOC_ENGINE_TYPE_INVALID, "节点类型非法");
        }

        // nodeName 格式校验
        validateNodeName(req.getNodeName());
    }

    /**
     * 校验节点名称
     */
    private void validateNodeName(String name) {
        if (name == null || name.isEmpty()) {
            throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NAME_INVALID, "节点名称不能为空");
        }

        if (name.length() < FileNodeConstants.NODE_NAME_MIN_LENGTH
                || name.length() > FileNodeConstants.NODE_NAME_MAX_LENGTH) {
            throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NAME_INVALID,
                    "节点名称长度需在 " + FileNodeConstants.NODE_NAME_MIN_LENGTH + "-" + FileNodeConstants.NODE_NAME_MAX_LENGTH + " 之间");
        }

        for (char c : FileNodeConstants.INVALID_NAME_CHARS.toCharArray()) {
            if (name.indexOf(c) >= 0) {
                throw new AdhocException(AdhocErrorCode.ADHOC_NODE_NAME_INVALID,
                        "节点名称不能包含非法字符: " + FileNodeConstants.INVALID_NAME_CHARS);
            }
        }
    }

    /**
     * 构建树形结构
     */
    private List<FileNodeResponse> buildTree(List<AdhocFileNode> allNodes, String rootNodeId) {
        List<FileNodeResponse> result = new ArrayList<>();

        for (AdhocFileNode node : allNodes) {
            if (rootNodeId.equals(node.getParentNodeId())) {
                FileNodeResponse response = toResponse(node);
                // 递归构建子节点
                response.setChildren(buildTree(allNodes, node.getNodeId()));
                result.add(response);
            }
        }

        return result;
    }

    /**
     * 实体转响应
     */
    private FileNodeResponse toResponse(AdhocFileNode node) {
        FileNodeResponse response = new FileNodeResponse();
        response.setNodeId(node.getNodeId());
        response.setParentNodeId(node.getParentNodeId());
        response.setNodeType(node.getNodeType());
        response.setNodeName(node.getNodeName());
        response.setSqlContent(node.getSqlContent());
        response.setDescription(node.getDescription());
        response.setCreateTime(node.getCreateTime());
        response.setUpdateTime(node.getUpdateTime());
        return response;
    }
}
