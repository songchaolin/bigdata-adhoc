package io.gitee.songchaolin.adhoc.server.web;

import io.gitee.songchaolin.adhoc.server.aspect.AdhocResponseAdvice;
import io.gitee.songchaolin.adhoc.server.auth.UserContextHolder;
import io.gitee.songchaolin.adhoc.server.service.ResultQueryService;
import io.gitee.songchaolin.adhoc.common.dto.JobResultResponse;
import io.gitee.songchaolin.adhoc.common.dto.request.JobResultRequest;
import io.gitee.songchaolin.adhoc.common.dto.request.ResultRequest;
import io.gitee.songchaolin.adhoc.common.dto.ResultResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 结果端点（controller 只转发，裸返回由 {@link AdhocResponseAdvice} 统一包 Result）：
 * - POST /api/task/result：单 task 结果分页（current/size，1-based）。
 * - POST /api/job/result：Job 结果聚合，一次返回所有 task 的结果第一页（按 segmentIndex 排列）。
 */
@Api(tags = "Adhoc Result")
@RestController
@RequestMapping("/api")
public class ResultController {

    private final ResultQueryService resultQueryService;

    public ResultController(ResultQueryService resultQueryService) {
        this.resultQueryService = resultQueryService;
    }

    @ApiOperation("Task 结果分页")
    @PostMapping("/task/result")
    public ResultResponse getResult(@RequestBody @Valid ResultRequest req) {
        return resultQueryService.getResult(req, UserContextHolder.getUserId());
    }

    @ApiOperation("Job 结果聚合（所有 task 第一页，按段序排列）")
    @PostMapping("/job/result")
    public JobResultResponse getJobResult(@RequestBody @Valid JobResultRequest req) {
        return resultQueryService.getJobResult(req.getJobId(), req.getSize(), UserContextHolder.getUserId());
    }
}
