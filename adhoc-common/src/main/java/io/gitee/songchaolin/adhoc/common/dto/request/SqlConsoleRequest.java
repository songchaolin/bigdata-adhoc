package io.gitee.songchaolin.adhoc.common.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * SQL 控制台查询请求（运维大盘内嵌，免登录态）。
 * <p>仅允许只读 SELECT/SHOW/DESCRIBE/EXPLAIN，范围限平台元数据库（adhoc_* 表）。
 */
@Data
public class SqlConsoleRequest {

    /** 待执行的只读查询 SQL（已去注释/拆分，仅接受单条 SELECT 系语句）。 */
    @NotBlank(message = "SQL 不能为空")
    @Size(max = 16000, message = "SQL 长度超出限制")
    private String sql;
}
