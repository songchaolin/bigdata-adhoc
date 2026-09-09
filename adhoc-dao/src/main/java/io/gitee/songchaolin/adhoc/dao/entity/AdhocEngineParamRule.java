package io.gitee.songchaolin.adhoc.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("adhoc_engine_param_rule")
public class AdhocEngineParamRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String engineType;
    private String paramKey;
    private String paramValueType;
    private String minValue;
    private String maxValue;
    private String allowedValues;
    private String defaultValue;
    private String description;
    private Integer enabled;
    private Date createTime;
    private Date updateTime;
}
