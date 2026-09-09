package io.gitee.songchaolin.adhoc.executor.ha;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** executor 在跑 Task 的注册表（线程安全），心跳上报 running_task_ids 用。JobExecutionRunner add/remove。 */
@Component
public class RunningTaskRegistry {

    private final Set<String> runningTaskIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void add(String taskId) {
        runningTaskIds.add(taskId);
    }

    public void remove(String taskId) {
        runningTaskIds.remove(taskId);
    }

    public Set<String> getIds() {
        return Collections.unmodifiableSet(runningTaskIds);
    }
}
