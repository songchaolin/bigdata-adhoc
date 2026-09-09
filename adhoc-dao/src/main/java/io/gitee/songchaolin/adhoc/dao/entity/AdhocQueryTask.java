package io.gitee.songchaolin.adhoc.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("adhoc_query_task")
public class AdhocQueryTask {
    @TableId(type = IdType.ASSIGN_UUID)
    private String queryId;
    private String jobId;
    private Integer segmentIndex;
    private String userId;
    private String userName;
    private String prefixSql;
    private String sqlContent;
    private String sqlHash;
    private String sqlType;
    private Integer hasResultSet;
    private Long affectedRows;
    private String engineParams;
    private String status;
    private String stage;
    private Integer cancelRequested;
    private Date cancelRequestedTime;
    private String failStage;
    private String failReasonCategory;
    private String errorCode;
    private String errorMessage;
    private String engineType;
    private String engineInstance;
    private String executorInstance;
    private String processingServerInstance;
    private String reusedFromTaskId;
    private Long scanRows;
    private Long scanBytes;
    private String persistentLogPath;
    private Date enqueueTime;
    private Date startTime;
    private Date fetchStartTime;
    private Date writeStartTime;
    private Date localWriteFinishTime;
    private Date ossUploadTime;
    private Date finishTime;
    private Long durationMs;
    private Integer isDeleted;
    private Date createTime;
    private Date updateTime;
}
