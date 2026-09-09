package io.gitee.songchaolin.adhoc.common.dto.request;

import lombok.Data;

/** POST /api/task/log 请求体。offset 0-based 行偏移。 */
@Data
public class TaskLogRequest {
    private String taskId;
    private long offset = 0;
    private int limit = 100;
}
