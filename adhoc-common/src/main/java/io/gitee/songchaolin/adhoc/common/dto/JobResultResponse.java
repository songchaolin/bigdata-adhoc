package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** POST /api/job/result 响应：Job 下所有 task 按 segmentIndex 排列，每个含元信息 + 结果第一页。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobResultResponse {
    private String jobId;
    private String status;
    private List<TaskResultItem> tasks;
}
