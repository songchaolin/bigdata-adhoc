package io.gitee.songchaolin.adhoc.server.web;

import io.gitee.songchaolin.adhoc.server.aspect.AdhocResponseAdvice;
import io.gitee.songchaolin.adhoc.server.auth.UserContextHolder;
import io.gitee.songchaolin.adhoc.server.service.LogQueryService;
import io.gitee.songchaolin.adhoc.common.dto.request.JobLogRequest;
import io.gitee.songchaolin.adhoc.common.dto.LogResponse;
import io.gitee.songchaolin.adhoc.common.dto.request.TaskLogRequest;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 日志端点（转发 LogQueryService，裸返回由 {@link AdhocResponseAdvice} 统一包 Result）：
 * - POST /api/task/log：running 走 gRPC fetchLog（executor 内存实时）/ terminal 兜底 OSS。
 * - POST /api/job/log：读 OSS（job.persistent_log_path，终态上传）。
 */
@Api(tags = "Adhoc Log")
@RestController
@RequestMapping("/api")
public class LogController {

    private final LogQueryService logQueryService;

    public LogController(LogQueryService logQueryService) {
        this.logQueryService = logQueryService;
    }

    @ApiOperation("Task 实时日志")
    @PostMapping("/task/log")
    public LogResponse getTaskLog(@RequestBody @Valid TaskLogRequest req) {
        return logQueryService.getTaskLog(req.getTaskId(), req.getOffset(), req.getLimit(), UserContextHolder.getUserId());
    }

    @ApiOperation("Job 完整日志")
    @PostMapping("/job/log")
    public LogResponse getJobLog(@RequestBody @Valid JobLogRequest req) {
        return logQueryService.getJobLog(req.getJobId(), req.getOffset(), req.getLimit(), UserContextHolder.getUserId());
    }
}
