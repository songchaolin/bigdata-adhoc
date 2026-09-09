package io.gitee.songchaolin.adhoc.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.songchaolin.adhoc.common.dto.request.*;
import io.gitee.songchaolin.adhoc.common.dto.response.FileNodeResponse;
import io.gitee.songchaolin.adhoc.common.enums.DeletedStatus;
import io.gitee.songchaolin.adhoc.common.enums.NodeType;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocFileNode;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocFileNodeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FileNodeService 测试：目录树 CRUD + 用户根自动创建 + 校验规则。连真实库
 * （ADHOC_MYSQL_* 环境变量提供连接，未设置自动跳过）。
 * <p>
 * 用户身份由参数传入（TEST_USER_ID/NAME），用户根为 USER_ROOT，挂全局根下。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
class FileNodeServiceTest {

    private static final String TEST_USER_ID = "test_user_001";
    private static final String TEST_USER_NAME = "测试用户";

    @Autowired
    private FileNodeService fileNodeService;

    @Autowired
    private AdhocFileNodeMapper fileNodeMapper;

    // === getTree ===

    @Test
    void getTree_createsRootIfNotExists() {
        // 首次获取目录树，应自动创建用户根并返回用户根本身（即使无子节点也返回这一层）
        List<FileNodeResponse> tree = fileNodeService.getTree(TEST_USER_ID, TEST_USER_NAME);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getNodeType()).isEqualTo(NodeType.USER_ROOT.name());
        assertThat(tree.get(0).getNodeName()).isEqualTo("root_" + TEST_USER_ID);
        // 无子节点时 children 为空，但用户根这一层必须返回
        assertThat(tree.get(0).getChildren()).isNotNull();
        // 用户根对前端为顶层，不暴露全局根（parent 置 null）
        assertThat(tree.get(0).getParentNodeId()).isNull();

        // 清理：删除测试数据
        cleanupTestNodes();
    }

    // === createNode ===

    @Test
    void createNode_directory_success() {
        // 先获取树确保用户根存在
        fileNodeService.getTree(TEST_USER_ID, TEST_USER_NAME);

        CreateNodeRequest req = new CreateNodeRequest();
        req.setNodeType(NodeType.DIRECTORY.name());
        req.setNodeName("测试目录_" + System.nanoTime());

        FileNodeResponse resp = fileNodeService.createNode(req, TEST_USER_ID, TEST_USER_NAME);
        assertThat(resp.getNodeId()).isNotBlank();
        assertThat(resp.getNodeType()).isEqualTo(NodeType.DIRECTORY.name());
        assertThat(resp.getNodeName()).startsWith("测试目录_");

        // 验证：getTree 顶层为用户根，其 children 应包含新创建的目录
        List<FileNodeResponse> tree = fileNodeService.getTree(TEST_USER_ID, TEST_USER_NAME);
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getNodeType()).isEqualTo(NodeType.USER_ROOT.name());
        assertThat(tree.get(0).getChildren().stream()
                .anyMatch(n -> n.getNodeName().equals(resp.getNodeName()))).isTrue();

        // 清理
        fileNodeMapper.deleteById(resp.getNodeId());
    }

    @Test
    void createNode_file_success() {
        // 先创建父目录
        CreateNodeRequest dirReq = new CreateNodeRequest();
        dirReq.setNodeType(NodeType.DIRECTORY.name());
        dirReq.setNodeName("父目录_" + System.nanoTime());
        FileNodeResponse dirResp = fileNodeService.createNode(dirReq, TEST_USER_ID, TEST_USER_NAME);

        // 创建文件
        CreateNodeRequest fileReq = new CreateNodeRequest();
        fileReq.setParentNodeId(dirResp.getNodeId());
        fileReq.setNodeType(NodeType.FILE.name());
        fileReq.setNodeName("测试文件.sql");
        fileReq.setSqlContent("SELECT * FROM table_a LIMIT 100");
        fileReq.setDescription("测试描述");

        FileNodeResponse fileResp = fileNodeService.createNode(fileReq, TEST_USER_ID, TEST_USER_NAME);
        assertThat(fileResp.getNodeId()).isNotBlank();
        assertThat(fileResp.getNodeType()).isEqualTo(NodeType.FILE.name());
        assertThat(fileResp.getSqlContent()).isEqualTo("SELECT * FROM table_a LIMIT 100");

        // 清理
        fileNodeMapper.deleteById(fileResp.getNodeId());
        fileNodeMapper.deleteById(dirResp.getNodeId());
    }

    @Test
    void createNode_emptySqlFile_allowed() {
        // 先创建父目录
        CreateNodeRequest dirReq = new CreateNodeRequest();
        dirReq.setNodeType(NodeType.DIRECTORY.name());
        dirReq.setNodeName("父目录_" + System.nanoTime());
        FileNodeResponse dirResp = fileNodeService.createNode(dirReq, TEST_USER_ID, TEST_USER_NAME);

        // 创建文件但不提供 SQL：允许（空文件，后续可在编辑器中补充）
        CreateNodeRequest fileReq = new CreateNodeRequest();
        fileReq.setParentNodeId(dirResp.getNodeId());
        fileReq.setNodeType(NodeType.FILE.name());
        fileReq.setNodeName("空SQL文件_" + System.nanoTime() + ".sql");

        FileNodeResponse fileResp = fileNodeService.createNode(fileReq, TEST_USER_ID, TEST_USER_NAME);
        assertThat(fileResp.getNodeId()).isNotNull();
        assertThat(fileResp.getNodeType()).isEqualTo(NodeType.FILE.name());

        // 清理
        fileNodeMapper.deleteById(fileResp.getNodeId());
        fileNodeMapper.deleteById(dirResp.getNodeId());
    }

    @Test
    void createNode_rootLevelFile_allowed() {
        // 用户根下可直接创建文件，无需先建目录
        CreateNodeRequest fileReq = new CreateNodeRequest();
        fileReq.setNodeType(NodeType.FILE.name());
        fileReq.setNodeName("根目录下文件_" + System.nanoTime() + ".sql");
        fileReq.setSqlContent("SELECT 1");

        FileNodeResponse fileResp = fileNodeService.createNode(fileReq, TEST_USER_ID, TEST_USER_NAME);
        assertThat(fileResp.getNodeId()).isNotNull();
        assertThat(fileResp.getNodeType()).isEqualTo(NodeType.FILE.name());
        assertThat(fileResp.getSqlContent()).isEqualTo("SELECT 1");

        // 清理
        fileNodeMapper.deleteById(fileResp.getNodeId());
    }

    @Test
    void createNode_duplicateName_throws() {
        // 先创建目录
        String dirName = "重复目录_" + System.nanoTime();
        CreateNodeRequest req1 = new CreateNodeRequest();
        req1.setNodeType(NodeType.DIRECTORY.name());
        req1.setNodeName(dirName);
        FileNodeResponse resp1 = fileNodeService.createNode(req1, TEST_USER_ID, TEST_USER_NAME);

        // 同名目录
        CreateNodeRequest req2 = new CreateNodeRequest();
        req2.setNodeType(NodeType.DIRECTORY.name());
        req2.setNodeName(dirName);

        assertThatThrownBy(() -> fileNodeService.createNode(req2, TEST_USER_ID, TEST_USER_NAME))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_NODE_NAME_DUPLICATE));

        // 清理
        fileNodeMapper.deleteById(resp1.getNodeId());
    }

    @Test
    void createNode_invalidName_throws() {
        CreateNodeRequest req = new CreateNodeRequest();
        req.setNodeType(NodeType.DIRECTORY.name());
        req.setNodeName("测试/目录");  // 含非法字符

        assertThatThrownBy(() -> fileNodeService.createNode(req, TEST_USER_ID, TEST_USER_NAME))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_NODE_NAME_INVALID));
    }

    // === listNodes ===

    @Test
    void listNodes_returnsChildren() {
        // 创建父目录
        CreateNodeRequest dirReq = new CreateNodeRequest();
        dirReq.setNodeType(NodeType.DIRECTORY.name());
        dirReq.setNodeName("父目录_列表_" + System.nanoTime());
        FileNodeResponse dirResp = fileNodeService.createNode(dirReq, TEST_USER_ID, TEST_USER_NAME);

        // 创建子节点
        CreateNodeRequest childReq = new CreateNodeRequest();
        childReq.setParentNodeId(dirResp.getNodeId());
        childReq.setNodeType(NodeType.DIRECTORY.name());
        childReq.setNodeName("子目录");
        FileNodeResponse childResp = fileNodeService.createNode(childReq, TEST_USER_ID, TEST_USER_NAME);

        // 列出子节点
        List<FileNodeResponse> children = fileNodeService.listNodes(dirResp.getNodeId(), TEST_USER_ID, TEST_USER_NAME);
        assertThat(children).hasSize(1);
        assertThat(children.get(0).getNodeName()).isEqualTo("子目录");

        // 清理
        fileNodeMapper.deleteById(childResp.getNodeId());
        fileNodeMapper.deleteById(dirResp.getNodeId());
    }

    // === getNode ===

    @Test
    void getNode_success() {
        // 创建节点
        CreateNodeRequest req = new CreateNodeRequest();
        req.setNodeType(NodeType.DIRECTORY.name());
        req.setNodeName("查询测试_" + System.nanoTime());
        FileNodeResponse created = fileNodeService.createNode(req, TEST_USER_ID, TEST_USER_NAME);

        // 查询
        FileNodeResponse fetched = fileNodeService.getNode(created.getNodeId(), TEST_USER_ID);
        assertThat(fetched.getNodeId()).isEqualTo(created.getNodeId());
        assertThat(fetched.getNodeName()).isEqualTo(created.getNodeName());

        // 清理
        fileNodeMapper.deleteById(created.getNodeId());
    }

    @Test
    void getNode_notFound_throws() {
        assertThatThrownBy(() -> fileNodeService.getNode("non_existent_node_id", TEST_USER_ID))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_NODE_NOT_FOUND));
    }

    // === renameNode ===

    @Test
    void renameNode_success() {
        // 创建节点
        CreateNodeRequest req = new CreateNodeRequest();
        req.setNodeType(NodeType.DIRECTORY.name());
        req.setNodeName("重命名前_" + System.nanoTime());
        FileNodeResponse created = fileNodeService.createNode(req, TEST_USER_ID, TEST_USER_NAME);

        // 重命名
        String newName = "重命名后_" + System.nanoTime();
        Boolean result = fileNodeService.renameNode(created.getNodeId(), newName, TEST_USER_ID);
        assertThat(result).isTrue();

        // 验证
        FileNodeResponse fetched = fileNodeService.getNode(created.getNodeId(), TEST_USER_ID);
        assertThat(fetched.getNodeName()).isEqualTo(newName);

        // 清理
        fileNodeMapper.deleteById(created.getNodeId());
    }

    @Test
    void renameNode_rootImmutable_throws() {
        // 获取树以创建用户根
        fileNodeService.getTree(TEST_USER_ID, TEST_USER_NAME);

        // 查找用户根（USER_ROOT）
        AdhocFileNode userRoot = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<AdhocFileNode>()
                        .eq(AdhocFileNode::getUserId, TEST_USER_ID)
                        .eq(AdhocFileNode::getNodeType, NodeType.USER_ROOT.name())
                        .eq(AdhocFileNode::getIsDeleted, DeletedStatus.NOT_DELETED.getCode()));
        assertThat(userRoot).isNotNull();

        assertThatThrownBy(() -> fileNodeService.renameNode(userRoot.getNodeId(), "new_root", TEST_USER_ID))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_ROOT_NODE_IMMUTABLE));
    }

    // === moveNode ===

    @Test
    void moveNode_success() {
        // 创建两个目录
        CreateNodeRequest dirReq1 = new CreateNodeRequest();
        dirReq1.setNodeType(NodeType.DIRECTORY.name());
        dirReq1.setNodeName("源目录_" + System.nanoTime());
        FileNodeResponse dir1 = fileNodeService.createNode(dirReq1, TEST_USER_ID, TEST_USER_NAME);

        CreateNodeRequest dirReq2 = new CreateNodeRequest();
        dirReq2.setNodeType(NodeType.DIRECTORY.name());
        dirReq2.setNodeName("目标目录_" + System.nanoTime());
        FileNodeResponse dir2 = fileNodeService.createNode(dirReq2, TEST_USER_ID, TEST_USER_NAME);

        // 移动
        Boolean result = fileNodeService.moveNode(dir1.getNodeId(), dir2.getNodeId(), TEST_USER_ID);
        assertThat(result).isTrue();

        // 验证
        FileNodeResponse fetched = fileNodeService.getNode(dir1.getNodeId(), TEST_USER_ID);
        assertThat(fetched.getParentNodeId()).isEqualTo(dir2.getNodeId());

        // 清理
        fileNodeMapper.deleteById(dir1.getNodeId());
        fileNodeMapper.deleteById(dir2.getNodeId());
    }

    @Test
    void moveNode_toSelf_throws() {
        CreateNodeRequest dirReq = new CreateNodeRequest();
        dirReq.setNodeType(NodeType.DIRECTORY.name());
        dirReq.setNodeName("自移动测试_" + System.nanoTime());
        FileNodeResponse dir = fileNodeService.createNode(dirReq, TEST_USER_ID, TEST_USER_NAME);

        assertThatThrownBy(() -> fileNodeService.moveNode(dir.getNodeId(), dir.getNodeId(), TEST_USER_ID))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_MOVE_TO_SELF_OR_CHILD));

        fileNodeMapper.deleteById(dir.getNodeId());
    }

    // === deleteNode ===

    @Test
    void deleteNode_success() {
        CreateNodeRequest req = new CreateNodeRequest();
        req.setNodeType(NodeType.DIRECTORY.name());
        req.setNodeName("删除测试_" + System.nanoTime());
        FileNodeResponse created = fileNodeService.createNode(req, TEST_USER_ID, TEST_USER_NAME);

        // 删除
        Boolean result = fileNodeService.deleteNode(created.getNodeId(), TEST_USER_ID);
        assertThat(result).isTrue();

        // 验证已删除
        AdhocFileNode deleted = fileNodeMapper.selectById(created.getNodeId());
        assertThat(deleted.getIsDeleted()).isEqualTo(DeletedStatus.DELETED.getCode());

        // 清理
        fileNodeMapper.deleteById(created.getNodeId());
    }

    @Test
    void deleteNode_directoryNotEmpty_throws() {
        // 创建父目录
        CreateNodeRequest dirReq = new CreateNodeRequest();
        dirReq.setNodeType(NodeType.DIRECTORY.name());
        dirReq.setNodeName("非空目录_" + System.nanoTime());
        FileNodeResponse dir = fileNodeService.createNode(dirReq, TEST_USER_ID, TEST_USER_NAME);

        // 创建子节点
        CreateNodeRequest childReq = new CreateNodeRequest();
        childReq.setParentNodeId(dir.getNodeId());
        childReq.setNodeType(NodeType.DIRECTORY.name());
        childReq.setNodeName("子目录");
        FileNodeResponse child = fileNodeService.createNode(childReq, TEST_USER_ID, TEST_USER_NAME);

        // 删除非空目录
        assertThatThrownBy(() -> fileNodeService.deleteNode(dir.getNodeId(), TEST_USER_ID))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_DIRECTORY_NOT_EMPTY));

        // 清理
        fileNodeMapper.deleteById(child.getNodeId());
        fileNodeMapper.deleteById(dir.getNodeId());
    }

    @Test
    void deleteNode_rootImmutable_throws() {
        fileNodeService.getTree(TEST_USER_ID, TEST_USER_NAME);

        AdhocFileNode userRoot = fileNodeMapper.selectOne(
                new LambdaQueryWrapper<AdhocFileNode>()
                        .eq(AdhocFileNode::getUserId, TEST_USER_ID)
                        .eq(AdhocFileNode::getNodeType, NodeType.USER_ROOT.name())
                        .eq(AdhocFileNode::getIsDeleted, DeletedStatus.NOT_DELETED.getCode()));
        assertThat(userRoot).isNotNull();

        assertThatThrownBy(() -> fileNodeService.deleteNode(userRoot.getNodeId(), TEST_USER_ID))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_ROOT_NODE_IMMUTABLE));
    }

    // === restoreNode ===

    @Test
    void restoreNode_success() {
        // 创建并删除
        CreateNodeRequest req = new CreateNodeRequest();
        req.setNodeType(NodeType.DIRECTORY.name());
        req.setNodeName("恢复测试_" + System.nanoTime());
        FileNodeResponse created = fileNodeService.createNode(req, TEST_USER_ID, TEST_USER_NAME);
        fileNodeService.deleteNode(created.getNodeId(), TEST_USER_ID);

        // 恢复
        Boolean result = fileNodeService.restoreNode(created.getNodeId(), TEST_USER_ID);
        assertThat(result).isTrue();

        // 验证已恢复
        AdhocFileNode restored = fileNodeMapper.selectById(created.getNodeId());
        assertThat(restored.getIsDeleted()).isEqualTo(DeletedStatus.NOT_DELETED.getCode());

        // 清理
        fileNodeMapper.deleteById(created.getNodeId());
    }

    // === searchNodes ===

    @Test
    void searchNodes_byKeyword() {
        // 创建节点
        CreateNodeRequest req = new CreateNodeRequest();
        req.setNodeType(NodeType.DIRECTORY.name());
        String uniqueName = "搜索测试_" + System.nanoTime();
        req.setNodeName(uniqueName);
        FileNodeResponse created = fileNodeService.createNode(req, TEST_USER_ID, TEST_USER_NAME);

        // 搜索
        List<FileNodeResponse> results = fileNodeService.searchNodes("搜索测试", null, TEST_USER_ID);
        assertThat(results).isNotEmpty();
        assertThat(results.stream().anyMatch(n -> n.getNodeName().equals(uniqueName))).isTrue();

        // 清理
        fileNodeMapper.deleteById(created.getNodeId());
    }

    @Test
    void searchNodes_byType() {
        // 创建目录和文件
        CreateNodeRequest dirReq = new CreateNodeRequest();
        dirReq.setNodeType(NodeType.DIRECTORY.name());
        dirReq.setNodeName("搜索目录_" + System.nanoTime());
        FileNodeResponse dir = fileNodeService.createNode(dirReq, TEST_USER_ID, TEST_USER_NAME);

        CreateNodeRequest fileReq = new CreateNodeRequest();
        fileReq.setParentNodeId(dir.getNodeId());
        fileReq.setNodeType(NodeType.FILE.name());
        fileReq.setNodeName("搜索文件_" + System.nanoTime());
        fileReq.setSqlContent("SELECT 1");
        FileNodeResponse file = fileNodeService.createNode(fileReq, TEST_USER_ID, TEST_USER_NAME);

        // 仅搜索文件
        List<FileNodeResponse> results = fileNodeService.searchNodes("搜索", NodeType.FILE.name(), TEST_USER_ID);
        assertThat(results).allMatch(n -> NodeType.FILE.is(n.getNodeType()));

        // 清理
        fileNodeMapper.deleteById(file.getNodeId());
        fileNodeMapper.deleteById(dir.getNodeId());
    }

    // === 综合结构 ===

    @Test
    void getTree_multipleDirsAndFiles_nestedStructure() {
        // 构建多层结构：用户根 -> 多个目录 -> 各目录下多个文件
        // 目录A
        CreateNodeRequest dirAReq = new CreateNodeRequest();
        dirAReq.setNodeType(NodeType.DIRECTORY.name());
        dirAReq.setNodeName("测试目录A_" + System.nanoTime());
        FileNodeResponse dirA = fileNodeService.createNode(dirAReq, TEST_USER_ID, TEST_USER_NAME);

        // 目录A 下挂两个文件
        FileNodeResponse fileA1 = createFileUnder(dirA.getNodeId(), "测试文件A1.sql", "SELECT 1");
        FileNodeResponse fileA2 = createFileUnder(dirA.getNodeId(), "测试文件A2.sql", "SELECT 2");

        // 目录B
        CreateNodeRequest dirBReq = new CreateNodeRequest();
        dirBReq.setNodeType(NodeType.DIRECTORY.name());
        dirBReq.setNodeName("测试目录B_" + System.nanoTime());
        FileNodeResponse dirB = fileNodeService.createNode(dirBReq, TEST_USER_ID, TEST_USER_NAME);

        // 目录B 下挂一个文件
        FileNodeResponse fileB1 = createFileUnder(dirB.getNodeId(), "测试文件B1.sql", "SELECT 3");

        // 获取目录树
        List<FileNodeResponse> tree = fileNodeService.getTree(TEST_USER_ID, TEST_USER_NAME);

        // 顶层为用户根
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getNodeType()).isEqualTo(NodeType.USER_ROOT.name());

        // 用户根下应包含目录A 和目录B
        List<FileNodeResponse> topChildren = tree.get(0).getChildren();
        assertThat(topChildren).extracting(FileNodeResponse::getNodeName)
                .contains(dirA.getNodeName(), dirB.getNodeName());

        // 目录A 下应有两个文件
        FileNodeResponse dirAResp = topChildren.stream()
                .filter(n -> n.getNodeName().equals(dirA.getNodeName()))
                .findFirst().orElseThrow(() -> new AssertionError("目录A 不存在"));
        assertThat(dirAResp.getNodeType()).isEqualTo(NodeType.DIRECTORY.name());
        assertThat(dirAResp.getChildren()).hasSize(2);
        assertThat(dirAResp.getChildren()).extracting(FileNodeResponse::getNodeName)
                .containsExactlyInAnyOrder("测试文件A1.sql", "测试文件A2.sql");
        assertThat(dirAResp.getChildren()).allMatch(n -> NodeType.FILE.is(n.getNodeType()));

        // 目录B 下应有一个文件
        FileNodeResponse dirBResp = topChildren.stream()
                .filter(n -> n.getNodeName().equals(dirB.getNodeName()))
                .findFirst().orElseThrow(() -> new AssertionError("目录B 不存在"));
        assertThat(dirBResp.getChildren()).hasSize(1);
        assertThat(dirBResp.getChildren().get(0).getNodeName()).isEqualTo("测试文件B1.sql");

        // 清理
        fileNodeMapper.deleteById(fileA1.getNodeId());
        fileNodeMapper.deleteById(fileA2.getNodeId());
        fileNodeMapper.deleteById(fileB1.getNodeId());
        fileNodeMapper.deleteById(dirA.getNodeId());
        fileNodeMapper.deleteById(dirB.getNodeId());
    }

    // === helper methods ===

    private FileNodeResponse createFileUnder(String parentNodeId, String name, String sql) {
        CreateNodeRequest req = new CreateNodeRequest();
        req.setParentNodeId(parentNodeId);
        req.setNodeType(NodeType.FILE.name());
        req.setNodeName(name);
        req.setSqlContent(sql);
        return fileNodeService.createNode(req, TEST_USER_ID, TEST_USER_NAME);
    }

    private void cleanupTestNodes() {
        // 清理测试数据
        fileNodeMapper.delete(
                new LambdaQueryWrapper<AdhocFileNode>()
                        .likeRight(AdhocFileNode::getNodeName, "测试")
                        .or()
                        .likeRight(AdhocFileNode::getNodeName, "搜索"));
    }
}
