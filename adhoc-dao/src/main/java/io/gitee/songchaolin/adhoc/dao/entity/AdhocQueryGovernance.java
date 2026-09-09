package io.gitee.songchaolin.adhoc.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("adhoc_query_governance")
public class AdhocQueryGovernance {
    @TableId(type = IdType.ASSIGN_UUID)
    private String queryId;
    private String sqlType;
    private String riskItemsJson;
    private String executedSqlContent;
    private String governanceResult;
    private String denyReason;
    private Date createTime;
    private Date updateTime;
}
