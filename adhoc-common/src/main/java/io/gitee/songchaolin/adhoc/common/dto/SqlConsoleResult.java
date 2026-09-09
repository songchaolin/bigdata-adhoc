package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SQL 控制台查询结果：列名 + 行集（单元格统一字符串化），含行数与是否截断标识。
 * <p>行数受 {@code SqlConsoleService#MAX_ROWS} 硬上限保护；{@code truncated=true} 表示
 * 实际命中行数超过上限，仅返回前 {@code MAX_ROWS} 行。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SqlConsoleResult {

    /** 列标签（取 ResultSetMetaData.getColumnLabel）。 */
    private List<String> columns;

    /** 行集：每行为单元格字符串列表，null 统一展示为空串。 */
    private List<List<String>> rows;

    /** 本次返回行数（<= MAX_ROWS）。 */
    private int totalRows;

    /** 是否因超出行数上限被截断。 */
    private boolean truncated;
}
