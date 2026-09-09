package io.gitee.songchaolin.adhoc.server.web;

import io.gitee.songchaolin.adhoc.common.dto.JobSubmitResponse;
import io.gitee.songchaolin.adhoc.common.dto.request.*;
import io.gitee.songchaolin.adhoc.common.dto.response.FileNodeResponse;
import io.gitee.songchaolin.adhoc.server.auth.UserContextHolder;
import io.gitee.songchaolin.adhoc.server.service.FileNodeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 目录树 REST 接口。用户保存的 SQL 脚本收藏夹。
 * <p>
 * 身份透传：由 {@code GatewayUserInterceptor} 从标准头 {@code X-Adhoc-User-Id}/{@code X-Adhoc-User-Name}
 * 注入 {@link UserContextHolder}，本类取 userId（鉴权隔离，仅可见/操作自己的 USER_ROOT 子树）
 * 与 userName（中文名，冗余存储）。与 {@code JobController} 一致。
 */
@Api(tags = "Adhoc FileNode")
@RestController
@RequestMapping("/api/file")
public class FileNodeController {

    private final FileNodeService fileNodeService;

    public FileNodeController(FileNodeService fileNodeService) {
        this.fileNodeService = fileNodeService;
    }

    @ApiOperation("创建节点")
    @PostMapping("/node/create")
    public FileNodeResponse createNode(@RequestBody CreateNodeRequest req) {
        return fileNodeService.createNode(req, UserContextHolder.getUserId(), UserContextHolder.getUserName());
    }

    @ApiOperation("获取完整目录树")
    @PostMapping("/tree")
    public List<FileNodeResponse> getTree() {
        return fileNodeService.getTree(UserContextHolder.getUserId(), UserContextHolder.getUserName());
    }

    @ApiOperation("列出子节点")
    @PostMapping("/nodes")
    public List<FileNodeResponse> listNodes(@RequestBody ListNodesRequest req) {
        return fileNodeService.listNodes(req.getParentNodeId(), UserContextHolder.getUserId(), UserContextHolder.getUserName());
    }

    @ApiOperation("查询节点详情")
    @PostMapping("/node/get")
    public FileNodeResponse getNode(@RequestBody GetNodeRequest req) {
        return fileNodeService.getNode(req.getNodeId(), UserContextHolder.getUserId());
    }

    @ApiOperation("重命名节点")
    @PostMapping("/node/rename")
    public Boolean renameNode(@RequestBody RenameNodeRequest req) {
        return fileNodeService.renameNode(req.getNodeId(), req.getNewName(), UserContextHolder.getUserId());
    }

    @ApiOperation("移动节点")
    @PostMapping("/node/move")
    public Boolean moveNode(@RequestBody MoveNodeRequest req) {
        return fileNodeService.moveNode(req.getNodeId(), req.getNewParentNodeId(), UserContextHolder.getUserId());
    }

    @ApiOperation("删除节点")
    @PostMapping("/node/delete")
    public Boolean deleteNode(@RequestBody DeleteNodeRequest req) {
        return fileNodeService.deleteNode(req.getNodeId(), UserContextHolder.getUserId());
    }

    @ApiOperation("恢复节点")
    @PostMapping("/node/restore")
    public Boolean restoreNode(@RequestBody RestoreNodeRequest req) {
        return fileNodeService.restoreNode(req.getNodeId(), UserContextHolder.getUserId());
    }

    @ApiOperation("搜索节点")
    @PostMapping("/nodes/search")
    public List<FileNodeResponse> searchNodes(@RequestBody SearchNodesRequest req) {
        return fileNodeService.searchNodes(req.getKeyword(), req.getType(), UserContextHolder.getUserId());
    }

    @ApiOperation("更新节点内容")
    @PostMapping("/node/update")
    public Boolean updateNode(@RequestBody UpdateNodeRequest req) {
        return fileNodeService.updateNode(req, UserContextHolder.getUserId());
    }

    @ApiOperation("从文件提交查询")
    @PostMapping("/submit")
    public JobSubmitResponse submitByFile(@RequestBody SubmitByFileRequest req) {
        return fileNodeService.submitByFile(req, UserContextHolder.getUserId(), UserContextHolder.getUserName());
    }
}
