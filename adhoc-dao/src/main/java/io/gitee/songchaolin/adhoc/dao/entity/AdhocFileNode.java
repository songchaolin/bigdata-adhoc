package io.gitee.songchaolin.adhoc.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("adhoc_file_node")
public class AdhocFileNode {
    @TableId(type = IdType.ASSIGN_UUID)
    private String nodeId;
    private String userId;
    private String userName;
    private String parentNodeId;
    private String nodeType;
    private String nodeName;
    private String sqlContent;
    private String description;
    private Integer isDeleted;
    private Date createTime;
    private Date updateTime;
}
