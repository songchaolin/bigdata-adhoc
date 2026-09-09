package io.gitee.songchaolin.adhoc.sdk;

import io.gitee.songchaolin.adhoc.common.dto.*;
import io.gitee.songchaolin.adhoc.common.dto.request.JobSubmitRequest;

/**
 * Adhoc SDK 客户端接口，支持 HTTP 和 gRPC 双协议。
 * <p>
 * 用法示例：
 * <pre>
 * AdhocClient client = AdhocClientBuilder.builder()
 *     .endpoint("http://server:8080/adhoc")
 *     .protocol(Protocol.HTTP)
 *     .build();
 *
 * JobSubmitRequest request = new JobSubmitRequest();
 * request.setSqlContent("SELECT 1");
 * request.setEngineType("KYUUBI");
 * request.setUserId("user123");
 * // request.setEngineInstance("kyuubi-02"); // 可选，指定引擎实例，空则用默认实例
 *
 * JobSubmitResponse resp = client.submitJob(request);
 *
 * // 其他方法需要传递 userId
 * JobStatusResponse status = client.getJobStatus(jobId, "user123");
 * </pre>
 */
public interface AdhocClient extends AutoCloseable {

    // ==================== Job 提交与查询 ====================

    /**
     * 提交查询 Job。
     *
     * @param request 请求参数（sqlContent、engineType、userId、userName、clientRequestId、fileId、engineInstance）
     * @return Job ID
     * @throws AdhocClientException 提交失败
     */
    JobSubmitResponse submitJob(JobSubmitRequest request);

    /**
     * 查询 Job 详情（含 Task 列表）。
     *
     * @param jobId  Job ID
     * @param userId 用户ID
     * @return Job 详情
     * @throws AdhocClientException 查询失败
     */
    JobDetailResponse getJob(String jobId, String userId);

    /**
     * 查询 Job 状态。
     *
     * @param jobId  Job ID
     * @param userId 用户ID
     * @return Job 状态
     * @throws AdhocClientException 查询失败
     */
    JobStatusResponse getJobStatus(String jobId, String userId);

    /**
     * 取消 Job。
     *
     * @param jobId  Job ID
     * @param userId 用户ID
     * @return 是否取消成功
     * @throws AdhocClientException 取消失败
     */
    boolean cancelJob(String jobId, String userId);

    // ==================== 结果与日志 ====================

    /**
     * 获取 Task 结果分页。
     *
     * @param taskId  Task ID
     * @param current 当前页码（1-based）
     * @param size    每页行数
     * @param userId  用户ID
     * @return 结果分页数据
     * @throws AdhocClientException 查询失败
     */
    ResultResponse getTaskResult(String taskId, long current, long size, String userId);

    /**
     * 获取 Job 结果聚合（所有 Task 第一页）。
     *
     * @param jobId  Job ID
     * @param size   每个 Task 的行数
     * @param userId 用户ID
     * @return Job 结果聚合
     * @throws AdhocClientException 查询失败
     */
    JobResultResponse getJobResult(String jobId, long size, String userId);

    /**
     * 获取 Job 日志分页。
     *
     * @param jobId  Job ID
     * @param offset 行偏移
     * @param limit  行数
     * @param userId 用户ID
     * @return 日志分页数据
     * @throws AdhocClientException 查询失败
     */
    LogResponse getJobLog(String jobId, long offset, int limit, String userId);

    /**
     * 关闭客户端，释放资源。
     */
    @Override
    void close();
}