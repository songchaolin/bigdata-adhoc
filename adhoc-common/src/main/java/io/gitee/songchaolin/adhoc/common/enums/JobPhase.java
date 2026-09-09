package io.gitee.songchaolin.adhoc.common.enums;

/**
 * Job 执行阶段（进度时间线用）。相邻 DB 时间戳相减得各阶段耗时：
 * SUBMIT(提交) -> QUEUE(排队, submit->dispatch) -> DISPATCH(调度, dispatch->splitFinish)
 * -> RUNNING(运行, start->finish) -> FINISH(完成)。
 */
public enum JobPhase {
    SUBMIT("提交"),
    QUEUE("排队"),
    DISPATCH("调度"),
    RUNNING("运行"),
    FINISH("完成");

    private final String displayName;

    JobPhase(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean is(String value) {
        return name().equals(value);
    }

    public static JobPhase fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (JobPhase e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}