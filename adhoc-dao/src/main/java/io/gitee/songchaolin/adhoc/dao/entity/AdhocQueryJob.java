package io.gitee.songchaolin.adhoc.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("adhoc_query_job")
public class AdhocQueryJob {
    @TableId(type = IdType.ASSIGN_UUID)
    private String jobId;
    private String userId;
    private String userName;
    private String sqlContent;
    private String engineType;
    private String engineInstance;
    private String engineParams;
    private String executorInstance;
    private String status;
    private Integer cancelRequested;
    private Date cancelRequestedTime;
    private String processingServerInstance;
    private String clientIp;
    private String clientUserAgent;
    private String clientRequestId;
    private String sourceFileNodeId;
    private Date submitTime;
    private Date validateFinishTime;
    private Date dispatchTime;
    private Date splitFinishTime;
    private Date startTime;
    private Date finishTime;
    private Long durationMs;
    private String persistentLogPath;
    private Integer isDeleted;
    private Date createTime;
    private Date updateTime;
}
