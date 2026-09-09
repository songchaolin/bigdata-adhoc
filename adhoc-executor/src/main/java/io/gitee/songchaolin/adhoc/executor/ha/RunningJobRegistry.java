package io.gitee.songchaolin.adhoc.executor.ha;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * executor 在跑 Job 的注册表（线程安全）：JobExecutionRunner.run() 进入时 add、finally 移除。
 * <p>用途：回答 server 的 getRunningJobs 探查（"job X 还在你这跑吗"）。executor 重启后内存清空 ->
 * server 探查发现 DB 里 RUNNING 的 job 不在此集合 -> 判定孤儿 -> 标 FAILED + 释放 server 侧 RunningJobRegistry。
 * <p>与 {@link RunningTaskRegistry}（task 级，心跳上报用）并列：job 级用于 job 失效探查，task 级用于 TASK_LOST 对账。
 */
@Component
public class RunningJobRegistry {

    private final Set<String> runningJobIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void add(String jobId) {
        runningJobIds.add(jobId);
    }

    public void remove(String jobId) {
        runningJobIds.remove(jobId);
    }

    /** 当前本 executor 正在跑的 jobId 快照（unmodifiable）。server 探查 RPC 返回此集合。 */
    public Set<String> getIds() {
        return Collections.unmodifiableSet(runningJobIds);
    }
}
