package io.gitee.songchaolin.adhoc.sdk;

import io.gitee.songchaolin.adhoc.common.dto.JobDetailResponse;
import io.gitee.songchaolin.adhoc.common.dto.JobResultResponse;
import io.gitee.songchaolin.adhoc.common.dto.JobStatusResponse;
import io.gitee.songchaolin.adhoc.common.dto.JobSubmitResponse;
import io.gitee.songchaolin.adhoc.common.dto.LogResponse;
import io.gitee.songchaolin.adhoc.common.dto.ResultResponse;
import io.gitee.songchaolin.adhoc.common.dto.TaskSummary;
import io.gitee.songchaolin.adhoc.common.dto.request.JobSubmitRequest;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;

/**
 * SDK 客户端使用示例
 * <p>
 * 本类演示 Adhoc SDK 的核心功能，包括：
 * <ul>
 *   <li>HTTP 协议客户端：提交 Job、轮询状态、查询详情、获取结果、获取日志</li>
 *   <li>gRPC 协议客户端：完整的双协议支持</li>
 *   <li>取消 Job：演示如何取消正在运行的查询</li>
 * </ul>
 * <p>
 * 运行方式：
 * <pre>
 * # 直接运行 main 方法
 * java io.gitee.songchaolin.adhoc.sdk.AdhocClientDemo
 *
 * # 或通过 Maven
 * mvn exec:java -Dexec.mainClass="io.gitee.songchaolin.adhoc.sdk.AdhocClientDemo" -pl adhoc-api-sdk
 * </pre>
 *
 * @see AdhocClient
 * @see AdhocClientBuilder
 */
public class AdhocClientDemo {

    public static void main(String[] args) {
        // 示例1：HTTP 协议客户端演示
         demoHttpClient();

        // 示例2：gRPC 协议客户端演示（需要 gRPC 服务端运行）
       // demoGrpcClient();

        // 示例3：取消 Job 演示
        // demoCancelJob();
    }

    /**
     * HTTP 协议客户端完整流程演示
     * <p>
     * 演示 HTTP 协议下的完整 Job 生命周期：
     * <ol>
     *   <li>创建 HTTP 客户端</li>
     *   <li>提交 Job</li>
     *   <li>轮询状态直到终态</li>
     *   <li>查询 Job 详情</li>
     *   <li>获取 Task 结果</li>
     *   <li>获取 Job 日志</li>
     *   <li>关闭客户端</li>
     * </ol>
     */
    private static void demoHttpClient() {
        System.out.println("========== HTTP 协议演示开始 ==========");

        // 1. 创建 HTTP 客户端
        AdhocClient httpClient = AdhocClientBuilder.builder()
                .endpoint("http://localhost:8080")
                .protocol(Protocol.HTTP)
                .build();

        try {
            // 2. 提交 Job
            JobSubmitRequest request = new JobSubmitRequest();
            request.setSqlContent("SELECT 1");
            request.setEngineType("KYUUBI");
            request.setUserId("user123");
            request.setUserName("测试用户");
            // request.setEngineInstance("kyuubi-02"); // 可选：指定引擎实例，不设则用默认实例

            System.out.println("【提交 Job】SQL: " + request.getSqlContent());
            JobSubmitResponse submitResp = httpClient.submitJob(request);
            String jobId = submitResp.getJobId();
            System.out.println("【提交成功】JobId: " + jobId);

            // 3. 轮询状态（每 2 秒轮询一次，最多 60 次）
            String userId = "user123";
            JobStatusResponse statusResp = pollJobStatus(httpClient, jobId, userId);
            System.out.println("【最终状态】" + statusResp.getStatus());

            // 4. 查询 Job 详情
            if (JobStatus.SUCCESS.is(statusResp.getStatus())
                    || JobStatus.PARTIAL_FAILED.is(statusResp.getStatus())) {
                JobDetailResponse jobDetail = httpClient.getJob(jobId, userId);
                System.out.println("【Job 详情】引擎: " + jobDetail.getEngineType()
                        + ", Task 数量: " + jobDetail.getTasks().size());

                // 5. 获取结果（遍历所有 Task）
                for (TaskSummary task : jobDetail.getTasks()) {
                    if (Boolean.TRUE.equals(task.getHasResultSet())) {
                        System.out.println("【Task " + task.getSegmentIndex() + "】"
                                + " 状态: " + task.getStatus()
                                + ", 类型: " + task.getSqlType());

                        // 获取第一页结果
                        ResultResponse result = httpClient.getTaskResult(task.getTaskId(), 1, 100, userId);
                        System.out.println("【结果】行数: " + result.getRows().size()
                                + ", 列数: " + result.getSchema().size());

                        // 打印前 5 行数据
                        printResultSample(result, 5);
                    }
                }

                // 6. 获取 Job 结果聚合（所有 Task 第一页）
                JobResultResponse jobResult = httpClient.getJobResult(jobId, 100, userId);
                System.out.println("【Job 结果聚合】Task 数量: " + jobResult.getTasks().size());
            }

            // 7. 获取日志
            LogResponse logResp = httpClient.getJobLog(jobId, 0, 1000, userId);
            System.out.println("【日志】行数: " + logResp.getLines().size());
            if (!logResp.getLines().isEmpty()) {
                System.out.println("【日志预览（前 10 行）】");
                logResp.getLines().stream().limit(10).forEach(System.out::println);
            }

        } catch (Exception e) {
            System.err.println("【演示失败】" + e.getMessage());
            e.printStackTrace();
        } finally {
            // 8. 关闭客户端
            try {
                httpClient.close();
                System.out.println("【客户端关闭】");
            } catch (Exception e) {
                System.err.println("【关闭失败】" + e.getMessage());
            }
        }

        System.out.println("========== HTTP 协议演示结束 ==========\n");
    }

    /**
     * gRPC 协议客户端完整流程演示
     * <p>
     * 演示 gRPC 协议下的完整 Job 生命周期，与 HTTP 演示类似。
     * <p>
     * 前置条件：gRPC 服务端已启动（默认端口 9090）
     */
    private static void demoGrpcClient() {
        System.out.println("========== gRPC 协议演示开始 ==========");

        // 1. 创建 gRPC 客户端
        AdhocClient grpcClient = AdhocClientBuilder.builder()
                .grpcEndpoint("localhost:9090")
                .protocol(Protocol.GRPC)
                .build();

        try {
            // 2. 提交 Job
            JobSubmitRequest request = new JobSubmitRequest();
            request.setSqlContent("use demo_db;SELECT * from demo_metrics limit 100;use demo_db;SELECT * from demo_metrics");
            request.setEngineType("KYUUBI");
            request.setUserId("user456");
            request.setUserName("gRPC测试用户");

            System.out.println("【提交 Job】SQL: " + request.getSqlContent());
            JobSubmitResponse submitResp = grpcClient.submitJob(request);
            String jobId = submitResp.getJobId();
            String userId = "user456";
            System.out.println("【提交成功】JobId: " + jobId);

            // 3. 轮询状态
            JobStatusResponse statusResp = pollJobStatus(grpcClient, jobId, userId);
            System.out.println("【最终状态】" + statusResp.getStatus());

            // 4. 查询详情和结果（同 HTTP）
            if (JobStatus.SUCCESS.is(statusResp.getStatus())) {
                JobDetailResponse jobDetail = grpcClient.getJob(jobId, userId);
                System.out.println("【Job 详情】Task 数量: " + jobDetail.getTasks().size());

                // 获取结果
                for (TaskSummary task : jobDetail.getTasks()) {
                    if (Boolean.TRUE.equals(task.getHasResultSet())) {
                        ResultResponse result = grpcClient.getTaskResult(task.getTaskId(), 1, 100, userId);
                        System.out.println("【Task " + task.getSegmentIndex() + " 结果】行数: " + result.getRows().size());
                    }
                }
            }

            // 5. 获取日志
            LogResponse logResp = grpcClient.getJobLog(jobId, 0, 500, userId);
            System.out.println("【日志】行数: " + logResp.getLines().size());

        } catch (Exception e) {
            System.err.println("【演示失败】" + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                grpcClient.close();
                System.out.println("【客户端关闭】");
            } catch (Exception e) {
                System.err.println("【关闭失败】" + e.getMessage());
            }
        }

        System.out.println("========== gRPC 协议演示结束 ==========\n");
    }

    /**
     * 取消 Job 演示
     * <p>
     * 演示如何取消正在运行的查询：
     * <ol>
     *   <li>提交一个长时间运行的查询</li>
     *   <li>等待一段时间后取消</li>
     *   <li>查询最终状态确认已取消</li>
     * </ol>
     */
    private static void demoCancelJob() {
        System.out.println("========== 取消 Job 演示开始 ==========");

        AdhocClient client = AdhocClientBuilder.builder()
                .endpoint("http://localhost:8080")
                .protocol(Protocol.HTTP)
                .build();

        try {
            // 1. 提交一个长时间运行的查询
            JobSubmitRequest request = new JobSubmitRequest();
            request.setSqlContent("SELECT COUNT(*) FROM large_table"); // 模拟长时间查询
            request.setEngineType("KYUUBI");
            request.setUserId("user789");

            JobSubmitResponse submitResp = client.submitJob(request);
            String jobId = submitResp.getJobId();
            String userId = "user789";
            System.out.println("【提交 Job】JobId: " + jobId);

            // 2. 等待 2 秒后取消
            Thread.sleep(2000);

            // 3. 取消 Job
            boolean canceled = client.cancelJob(jobId, userId);
            System.out.println("【取消结果】" + (canceled ? "成功" : "失败"));

            // 4. 查询最终状态
            JobStatusResponse statusResp = client.getJobStatus(jobId, userId);
            System.out.println("【最终状态】" + statusResp.getStatus());

        } catch (Exception e) {
            System.err.println("【演示失败】" + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                client.close();
            } catch (Exception e) {
                System.err.println("【关闭失败】" + e.getMessage());
            }
        }

        System.out.println("========== 取消 Job 演示结束 ==========\n");
    }

    // ==================== 辅助方法 ====================

    /**
     * 轮询 Job 状态直到终态
     *
     * @param client 客户端
     * @param jobId  Job ID
     * @param userId 用户ID
     * @return 最终状态
     */
    private static JobStatusResponse pollJobStatus(AdhocClient client, String jobId, String userId) {
        int maxAttempts = 60; // 最多轮询 60 次（2 分钟）
        int intervalMs = 2000; // 每 2 秒轮询一次

        for (int i = 0; i < maxAttempts; i++) {
            try {
                JobStatusResponse statusResp = client.getJobStatus(jobId, userId);
                String status = statusResp.getStatus();

                System.out.println("【轮询第 " + (i + 1) + " 次】状态: " + status);

                // 判断是否终态
                if (JobStatus.isTerminal(status)) {
                    return statusResp;
                }

                // 等待下一次轮询
                Thread.sleep(intervalMs);

            } catch (Exception e) {
                System.err.println("【轮询异常】" + e.getMessage());
                // 继续轮询
            }
        }

        // 超时返回当前状态
        try {
            return client.getJobStatus(jobId, userId);
        } catch (Exception e) {
            throw new RuntimeException("获取状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 打印结果样例（前 N 行）
     *
     * @param result 结果
     * @param rows   打印行数
     */
    private static void printResultSample(ResultResponse result, int rows) {
        if (result.getRows().isEmpty()) {
            System.out.println("【结果为空】");
            return;
        }

        System.out.println("【结果预览（前 " + rows + " 行）】");

        // 打印列名
        if (!result.getSchema().isEmpty()) {
            StringBuilder header = new StringBuilder("  | ");
            for (ResultResponse.ColumnDto col : result.getSchema()) {
                header.append(col.getColName()).append(" | ");
            }
            System.out.println(header);
            // Java 8 兼容：手动构建分隔线
            StringBuilder separator = new StringBuilder("  ");
            for (int i = 0; i < header.length() - 2; i++) {
                separator.append("-");
            }
            System.out.println(separator);
        }

        // 打印数据行（rows 是 JSON 字符串列表）
        int count = 0;
        for (String rowJson : result.getRows()) {
            if (count >= rows) break;
            System.out.println((count + 1) + " | " + rowJson);
            count++;
        }

        if (result.getRows().size() > rows) {
            System.out.println("  ... (共 " + result.getRows().size() + " 行)");
        }
    }
}