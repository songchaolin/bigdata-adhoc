package io.gitee.songchaolin.adhoc.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("adhoc_query_table_ref")
public class AdhocQueryTableRef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String queryId;
    private String sourceTablesJson;
    private String sinkTablesJson;
    private Date createTime;
    private Date updateTime;
}
