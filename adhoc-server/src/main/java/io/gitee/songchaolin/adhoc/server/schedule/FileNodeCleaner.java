package io.gitee.songchaolin.adhoc.server.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.songchaolin.adhoc.common.constant.FileNodeConstants;
import io.gitee.songchaolin.adhoc.common.enums.DeletedStatus;
import io.gitee.songchaolin.adhoc.common.enums.NodeType;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocFileNode;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocFileNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 文件节点清理任务
 * 定期清理软删除超过 N 天的节点
 */
@Component
public class FileNodeCleaner {

    private static final Logger log = LoggerFactory.getLogger(FileNodeCleaner.class);

    private final AdhocFileNodeMapper fileNodeMapper;

    public FileNodeCleaner(AdhocFileNodeMapper fileNodeMapper) {
        this.fileNodeMapper = fileNodeMapper;
    }

    /**
     * 每天凌晨 2 点执行清理
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanDeletedNodes() {
        long cutoffMs = System.currentTimeMillis() - (long) FileNodeConstants.CLEANUP_RETENTION_DAYS * 24 * 60 * 60 * 1000;
        Date cutoffTime = new Date(cutoffMs);

        // 先删文件
        int files = fileNodeMapper.delete(new LambdaQueryWrapper<AdhocFileNode>()
                .eq(AdhocFileNode::getIsDeleted, DeletedStatus.DELETED.getCode())
                .eq(AdhocFileNode::getNodeType, NodeType.FILE.name())
                .lt(AdhocFileNode::getUpdateTime, cutoffTime));

        // 再删目录
        int dirs = fileNodeMapper.delete(new LambdaQueryWrapper<AdhocFileNode>()
                .eq(AdhocFileNode::getIsDeleted, DeletedStatus.DELETED.getCode())
                .eq(AdhocFileNode::getNodeType, NodeType.DIRECTORY.name())
                .lt(AdhocFileNode::getUpdateTime, cutoffTime));

        log.info("【清理完成】删除过期文件节点：文件={}个，目录={}个", files, dirs);
    }
}