package io.gitee.songchaolin.adhoc.common.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 更新节点请求
 * <p>
 * 用于更新已保存的 SQL 脚本内容
 */
@Data
@ApiModel(description = "更新节点请求")
public class UpdateNodeRequest {

    @NotBlank(message = "节点ID不能为空")
    @ApiModelProperty(value = "节点ID", required = true)
    private String nodeId;

    @ApiModelProperty("新节点名称（可选）")
    private String nodeName;

    @ApiModelProperty("新SQL内容（FILE类型）")
    private String sqlContent;

    @ApiModelProperty("新描述")
    private String description;
}