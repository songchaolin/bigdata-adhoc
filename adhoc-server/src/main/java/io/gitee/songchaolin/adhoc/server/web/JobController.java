package io.gitee.songchaolin.adhoc.server.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.gitee.songchaolin.adhoc.server.aspect.AdhocResponseAdvice;
import io.gitee.songchaolin.adhoc.server.auth.UserContextHolder;
import io.gitee.songchaolin.adhoc.server.service.JobQueryService;
import io.gitee.songchaolin.adhoc.server.service.JobService;
import io.gitee.songchaolin.adhoc.server.service.TaskQueryService;
import io.gitee.songchaolin.adhoc.common.dto.JobDetailResponse;
import io.gitee.songchaolin.adhoc.common.dto.JobProgressResponse;
import io.gitee.songchaolin.adhoc.common.dto.TaskDetailResponse;
import io.gitee.songchaolin.adhoc.common.dto.request.JobIdRequest;
import io.gitee.songchaolin.adhoc.common.dto.request.JobPageQueryRequest;
import io.gitee.songchaolin.adhoc.common.dto.JobStatusResponse;
import io.gitee.songchaolin.adhoc.common.dto.request.JobSubmitRequest;
import io.gitee.songchaolin.adhoc.common.dto.JobSubmitResponse;
import io.gitee.songchaolin.adhoc.common.dto.JobVO;
import io.gitee.songchaolin.adhoc.common.dto.request.TaskIdRequest;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * Job REST 接口。controller 只返回裸业务对象，由 {@link AdhocResponseAdvice} 统一包成 {@code Result}。
 * 认证：由 {@code GatewayUserInterceptor} 解析标准头 {@code X-Adhoc-User-Id}/{@code X-Adhoc-User-Name}
 * 注入 {@link UserContextHolder}，本类方法直接取 userId/userName（userName 即中文名，对应 service 层 userName 入参），不再用 @RequestHeader。
 * 入参统一 @RequestBody @Valid。
 */
@Api(tags = "Adhoc Job")
@RestController
@RequestMapping("/api")
public class JobController {

    private final JobService jobService;
    private final JobQueryService jobQueryService;
    private final TaskQueryService taskQueryService;

    public JobController(JobService jobService, JobQueryService jobQueryService, TaskQueryService taskQueryService) {
        this.jobService = jobService;
        this.jobQueryService = jobQueryService;
        this.taskQueryService = taskQueryService;
    }

    @ApiOperation("提交 Job")
    @PostMapping("/job")
    public JobSubmitResponse submit(@RequestBody @Valid JobSubmitRequest req) {
        return jobService.submit(req, UserContextHolder.getUserId(), UserContextHolder.getUserName());
    }

    @ApiOperation("Job 分页查询")
    @PostMapping("/job/page")
    public IPage<JobVO> page(@RequestBody @Valid JobPageQueryRequest req) {
        return jobQueryService.selectPage(req, UserContextHolder.getUserId());
    }

    @ApiOperation("Job 详情")
    @PostMapping("/job/detail")
    public JobDetailResponse getJob(@RequestBody @Valid JobIdRequest req) {
        return jobQueryService.getJobDetail(req.getJobId(), UserContextHolder.getUserId());
    }

    @ApiOperation("Job 状态")
    @PostMapping("/job/status")
    public JobStatusResponse getStatus(@RequestBody @Valid JobIdRequest req) {
        return jobQueryService.getJobStatus(req.getJobId(), UserContextHolder.getUserId());
    }

    @ApiOperation("Job 执行进度（阶段时间线 + 耗时）")
    @PostMapping("/job/progress")
    public JobProgressResponse getProgress(@RequestBody @Valid JobIdRequest req) {
        return jobQueryService.getJobProgress(req.getJobId(), UserContextHolder.getUserId());
    }

    @ApiOperation("取消 Job")
    @PostMapping("/job/cancel")
    public Boolean cancel(@RequestBody @Valid JobIdRequest req) {
        return jobService.cancel(req.getJobId(), UserContextHolder.getUserId());
    }

    @ApiOperation("Task 详情")
    @PostMapping("/task/detail")
    public TaskDetailResponse getTaskDetail(@RequestBody @Valid TaskIdRequest req) {
        return taskQueryService.getTaskDetail(req.getTaskId(), UserContextHolder.getUserId());
    }
}
