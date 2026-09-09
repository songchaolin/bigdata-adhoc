package io.gitee.songchaolin.adhoc.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** SQL 解析结果：SqlType + 源表/目标表（表级血缘）+ 是否合法 + 错误信息。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SqlParseResult {
    private String sqlType;          // DQL/DDL_CREATE/DDL_ALTER/DDL_DROP/DML_INSERT/DML_MODIFY/CTAS/AUX/DCL/SESSION_CONFIG/UNKNOWN
    private List<String> sourceTables;
    private List<String> sinkTables;
    private boolean valid;
    private String errorMessage;
}
