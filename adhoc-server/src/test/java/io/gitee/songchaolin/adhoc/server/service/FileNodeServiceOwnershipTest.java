package io.gitee.songchaolin.adhoc.server.service;

import io.gitee.songchaolin.adhoc.common.constant.FileNodeConstants;
import io.gitee.songchaolin.adhoc.common.dto.request.CreateNodeRequest;
import io.gitee.songchaolin.adhoc.common.dto.request.SubmitByFileRequest;
import io.gitee.songchaolin.adhoc.common.dto.response.FileNodeResponse;
import io.gitee.songchaolin.adhoc.common.enums.NodeType;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocFileNode;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocFileNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileNodeService 归属隔离 / 用户根 / 根不可变 的纯单测（Mockito，不依赖真实库）。
 * <p>
 * 重点验证"用户身份透传"引入的新行为：
 * <ul>
 *   <li>非本人节点一律 ADHOC_NODE_NOT_FOUND（不泄漏存在性），且不触发任何写操作</li>
 *   <li>用户根（USER_ROOT）自动创建并挂到全局根下，命名 root_+userId</li>
 *   <li>USER_ROOT / 全局根不可重命名/删除</li>
 * </ul>
 */
class FileNodeServiceOwnershipTest {

    private AdhocFileNodeMapper fileNodeMapper;
    private JobService jobService;
    private FileNodeService fileNodeService;

    @BeforeEach
    void setUp() {
        fileNodeMapper = Mockito.mock(AdhocFileNodeMapper.class);
        jobService = Mockito.mock(JobService.class);
        fileNodeService = new FileNodeService(fileNodeMapper, jobService);
    }

    // === 归属隔离：非本人节点按不存在处理，不泄漏 ===

    @Test
    void getNode_otherUserNode_throwsNotFound() {
        AdhocFileNode others = node("n1", "userB", NodeType.DIRECTORY, 0);
        when(fileNodeMapper.selectById("n1")).thenReturn(others);

        assertThatThrownBy(() -> fileNodeService.getNode("n1", "userA"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_NODE_NOT_FOUND));
        verify(fileNodeMapper, never()).selectList(any());
    }

    @Test
    void getNode_nonexistent_throwsNotFound() {
        when(fileNodeMapper.selectById("nope")).thenReturn(null);

        assertThatThrownBy(() -> fileNodeService.getNode("nope", "userA"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_NODE_NOT_FOUND));
    }

    @Test
    void getNode_ownNode_returns() {
        AdhocFileNode mine = node("n1", "userA", NodeType.DIRECTORY, 0);
        mine.setNodeName("d1");
        when(fileNodeMapper.selectById("n1")).thenReturn(mine);

        FileNodeResponse r = fileNodeService.getNode("n1", "userA");
        assertThat(r.getNodeId()).isEqualTo("n1");
        assertThat(r.getNodeName()).isEqualTo("d1");
    }

    @Test
    void deleteNode_otherUser_throwsNotFoundAndNoWrite() {
        AdhocFileNode others = node("n1", "userB", NodeType.DIRECTORY, 0);
        when(fileNodeMapper.selectById("n1")).thenReturn(others);

        assertThatThrownBy(() -> fileNodeService.deleteNode("n1", "userA"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_NODE_NOT_FOUND));
        verify(fileNodeMapper, never()).update(any(), any());
    }

    @Test
    void createNode_otherUserParent_throwsNotFoundAndNoInsert() {
        CreateNodeRequest req = new CreateNodeRequest();
        req.setNodeType(NodeType.DIRECTORY.name());
        req.setNodeName("d");
        req.setParentNodeId("n1");
        when(fileNodeMapper.selectById("n1")).thenReturn(node("n1", "userB", NodeType.DIRECTORY, 0));

        assertThatThrownBy(() -> fileNodeService.createNode(req, "userA", "A名"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_NODE_NOT_FOUND));
        verify(fileNodeMapper, never()).insert(any());
    }

    @Test
    void submitByFile_otherUser_throwsNotFoundAndNoSubmit() {
        SubmitByFileRequest req = new SubmitByFileRequest();
        req.setNodeId("n1");
        when(fileNodeMapper.selectById("n1")).thenReturn(node("n1", "userB", NodeType.FILE, 0));

        assertThatThrownBy(() -> fileNodeService.submitByFile(req, "userA", "A名"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_NODE_NOT_FOUND));
        verify(jobService, never()).submit(any(), anyString(), anyString());
    }

    // === 根不可变 ===

    @Test
    void renameNode_userRoot_throwsImmutable() {
        AdhocFileNode userRoot = node("ur1", "userA", NodeType.USER_ROOT, 0);
        userRoot.setParentNodeId(FileNodeConstants.GLOBAL_ROOT_NODE_ID);
        when(fileNodeMapper.selectById("ur1")).thenReturn(userRoot);

        assertThatThrownBy(() -> fileNodeService.renameNode("ur1", "new", "userA"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_ROOT_NODE_IMMUTABLE));
        verify(fileNodeMapper, never()).update(any(), any());
    }

    @Test
    void deleteNode_userRoot_throwsImmutable() {
        AdhocFileNode userRoot = node("ur1", "userA", NodeType.USER_ROOT, 0);
        userRoot.setParentNodeId(FileNodeConstants.GLOBAL_ROOT_NODE_ID);
        when(fileNodeMapper.selectById("ur1")).thenReturn(userRoot);

        assertThatThrownBy(() -> fileNodeService.deleteNode("ur1", "userA"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_ROOT_NODE_IMMUTABLE));
        verify(fileNodeMapper, never()).update(any(), any());
    }

    @Test
    void renameNode_globalRoot_throwsImmutable() {
        // 全局根：parent=NULL，属于 system，但 requireOwnedNode 会先按"非本人"拦掉。
        // 这里直接验证：对 system 的全局根，userA 操作 -> NOT_FOUND（不泄漏）。
        AdhocFileNode globalRoot = node(FileNodeConstants.GLOBAL_ROOT_NODE_ID,
                FileNodeConstants.SYSTEM_USER_ID, NodeType.DIRECTORY, 0);
        globalRoot.setParentNodeId(null);
        when(fileNodeMapper.selectById(FileNodeConstants.GLOBAL_ROOT_NODE_ID)).thenReturn(globalRoot);

        assertThatThrownBy(() -> fileNodeService.renameNode(FileNodeConstants.GLOBAL_ROOT_NODE_ID, "new", "userA"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_NODE_NOT_FOUND));
    }

    // === 用户根自动创建（挂全局根下）===

    @Test
    void getTree_createsUserRootUnderGlobalRoot() {
        AdhocFileNode globalRoot = new AdhocFileNode();
        globalRoot.setNodeId(FileNodeConstants.GLOBAL_ROOT_NODE_ID);
        globalRoot.setUserId(FileNodeConstants.SYSTEM_USER_ID);

        // getRootNode / 旧根迁移查询（selectOne）默认返回 null；全局根已存在（selectById 命中）
        when(fileNodeMapper.selectById(FileNodeConstants.GLOBAL_ROOT_NODE_ID)).thenReturn(globalRoot);
        when(fileNodeMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<FileNodeResponse> tree = fileNodeService.getTree("userA", "A名");

        // 用户根本身作为顶层返回（即使无子节点也返回这一层）
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getNodeType()).isEqualTo(NodeType.USER_ROOT.name());
        // 命名=显示名(userId)（见 ensureUserRootExists），展示友好且全局唯一
        assertThat(tree.get(0).getNodeName()).isEqualTo("A名(userA)");
        assertThat(tree.get(0).getChildren()).isEmpty();
        // 用户根对前端为顶层，parent 抹去（不暴露全局根）
        assertThat(tree.get(0).getParentNodeId()).isNull();

        // 应插入恰好一个用户根，且 parent=全局根、type=USER_ROOT、name=显示名(userId)
        ArgumentCaptor<AdhocFileNode> captor = ArgumentCaptor.forClass(AdhocFileNode.class);
        verify(fileNodeMapper).insert(captor.capture());
        AdhocFileNode inserted = captor.getValue();
        assertThat(inserted.getNodeType()).isEqualTo(NodeType.USER_ROOT.name());
        assertThat(inserted.getParentNodeId()).isEqualTo(FileNodeConstants.GLOBAL_ROOT_NODE_ID);
        assertThat(inserted.getNodeName()).isEqualTo("A名(userA)");
        assertThat(inserted.getUserId()).isEqualTo("userA");
        assertThat(inserted.getUserName()).isEqualTo("A名");
    }

    // === helper ===

    private AdhocFileNode node(String nodeId, String userId, NodeType type, int isDeleted) {
        AdhocFileNode n = new AdhocFileNode();
        n.setNodeId(nodeId);
        n.setUserId(userId);
        n.setNodeType(type.name());
        n.setIsDeleted(isDeleted);
        return n;
    }
}
