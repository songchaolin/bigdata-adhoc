package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobDetailResponse {
    private String jobId;
    private String status;
    private String sqlContent;
    private String engineType;
    private Date submitTime;
    private List<TaskSummary> tasks;
}
