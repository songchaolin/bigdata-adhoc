package io.gitee.songchaolin.adhoc.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.songchaolin.adhoc.common.config.AdhocCommonConfig;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.dto.JobSubmitResponse;
import io.gitee.songchaolin.adhoc.common.enums.TaskStatus;
import io.gitee.songchaolin.adhoc.common.util.SqlHashUtil;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.server.ha.JobLogRegistry;
import io.gitee.songchaolin.adhoc.sqlparser.model.ProcessedSqlSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 结果复用服务
 * <p>
 * 检查同一用户在 TTL 内是否执行过相同 SQL，命中则直接复用结果
 */
@Service
public class ResultReuseService {

    private static final Logger log = LoggerFactory.getLogger(ResultReuseService.class);

    private final AdhocQueryTaskMapper taskMapper;
    private final JobLogRegistry jobLogRegistry;
    private final ConfigHolder cfg;

    public ResultReuseService(AdhocQueryTaskMapper taskMapper, JobLogRegistry jobLogRegistry, ConfigHolder cfg) {
        this.taskMapper = taskMapper;
        this.jobLogRegistry = jobLogRegistry;
        this.cfg = cfg;
    }

    /**
     * 检查是否可完全复用
     *
     * @param userId   用户ID
     * @param segments SQL 段列表
     * @return 所有段都命中返回复用的 Task ID 列表；否则返回 null
     */
    public List<String> checkFullReuse(String userId, List<ProcessedSqlSegment> segments) {
        int ttlSeconds = cfg.get(AdhocCommonConfig.RESULT_REUSE_TTL_SECONDS);
        List<String> reusedTaskIds = new ArrayList<>();

        for (ProcessedSqlSegment segment : segments) {
            String sqlHash = SqlHashUtil.hash(segment.getSql());
            String existingTaskId = taskMapper.selectSuccessTaskBySqlHash(userId, sqlHash, ttlSeconds);

            if (existingTaskId == null) {
                // 未命中，不能完全复用
                log.debug("【结果复用检查】sqlHash={} 未命中", sqlHash);
                return null;
            }

            reusedTaskIds.add(existingTaskId);
            log.debug("【结果复用检查】sqlHash={} 命中 existingTaskId={}", sqlHash, existingTaskId);
        }

        return reusedTaskIds;
    }

    /**
     * 创建复用 Job（直接 SUCCESS）
     *
     * @param jobId          Job ID
     * @param userId         用户ID
     * @param userName       用户名
     * @param segments       SQL 段列表
     * @param reusedTaskIds  复用的 Task ID 列表
     */
    public void createReusedTasks(String jobId, String userId, String userName,
                                   List<ProcessedSqlSegment> segments, List<String> reusedTaskIds) {
        for (int i = 0; i < segments.size(); i++) {
            ProcessedSqlSegment segment = segments.get(i);
            String existingTaskId = reusedTaskIds.get(i);

            // 创建新 Task
            AdhocQueryTask task = new AdhocQueryTask();
            task.setQueryId("Task_" + UUID.randomUUID().toString().replace("-", ""));
            task.setJobId(jobId);
            task.setSegmentIndex(i);
            task.setUserId(userId);
            task.setUserName(userName);
            task.setSqlContent(segment.getSql());
            task.setSqlHash(SqlHashUtil.hash(segment.getSql()));
            task.setSqlType(segment.getSqlType());
            task.setStatus(TaskStatus.SUCCESS.name());
            task.setReusedFromTaskId(existingTaskId);
            task.setCreateTime(new Date());
            task.setFinishTime(new Date());
            task.setIsDeleted(0);

            taskMapper.insert(task);

            // 复制结果元信息（result_summary + result_schema）
            copyResultMeta(existingTaskId, task.getQueryId());

            log.info("【结果复用】taskId={} reused from taskId={}", task.getQueryId(), existingTaskId);
        }
    }

    /**
     * 复制结果元信息
     */
    private void copyResultMeta(String fromTaskId, String toTaskId) {
        // TODO: 实现 result_summary 和 result_schema 的复制
        // 暂时省略，因为测试环境可能没有真实结果数据
    }
}