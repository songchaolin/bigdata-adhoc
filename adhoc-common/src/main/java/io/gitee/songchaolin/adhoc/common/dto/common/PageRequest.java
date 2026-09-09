package io.gitee.songchaolin.adhoc.common.dto.common;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

/**
 * 分页查询请求基类。所有分页请求 DTO 继承本类，统一 current/size 字段 + 校验。
 */
@Data
@ApiModel(description = "分页查询请求基类")
public class PageRequest {

    @ApiModelProperty(value = "当前页", required = true)
    @Range(min = 1, message = "当前页必须大于0")
    private Long current = 1L;

    @ApiModelProperty(value = "每页显示条数", required = true)
    @Range(min = 1, max = 100, message = "每页条数必须在1-100之间")
    private Long size = 10L;
}
