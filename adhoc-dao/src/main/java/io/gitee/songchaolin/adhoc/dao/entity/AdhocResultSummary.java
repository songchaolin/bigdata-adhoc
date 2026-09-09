package io.gitee.songchaolin.adhoc.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("adhoc_result_summary")
public class AdhocResultSummary {
    @TableId(type = IdType.ASSIGN_UUID)
    private String queryId;
    private Long resultRows;
    private Long resultBytes;
    private String persistentPath;
    private String storageType;
    private String resultStatus;
    private String ossUploadStatus;
    private Date createTime;
}