package io.gitee.songchaolin.adhoc.common.dto.request;

import lombok.Data;

/** POST /api/job/log 请求体。offset 0-based 行偏移。 */
@Data
public class JobLogRequest {
    private String jobId;
    private long offset = 0;
    private int limit = 1000;
}
