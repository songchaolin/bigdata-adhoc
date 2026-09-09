package io.gitee.songchaolin.adhoc.common.model;

/**
 * Task 统计摘要（用于 Job 完成日志）
 */
public class TaskStats {
    private Long total;      // 总数
    private Long success;    // 成功数
    private Long failed;     // 失败数（含跳过的）
    private Long canceled;   // 取消数
    private Long skipped;    // 跳过的（上游失败导致，status=FAILED + fail_reason_category=SKIPPED_DUE_TO_PRIOR_FAILURE）

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }

    public Long getSuccess() { return success; }
    public void setSuccess(Long success) { this.success = success; }

    public Long getFailed() { return failed; }
    public void setFailed(Long failed) { this.failed = failed; }

    public Long getCanceled() { return canceled; }
    public void setCanceled(Long canceled) { this.canceled = canceled; }

    public Long getSkipped() { return skipped; }
    public void setSkipped(Long skipped) { this.skipped = skipped; }

    /**
     * 获取真正执行失败的数（不含跳过的）
     */
    public Long getRealFailed() {
        if (failed == null || skipped == null) {
            return failed;
        }
        return failed - skipped;
    }
}
