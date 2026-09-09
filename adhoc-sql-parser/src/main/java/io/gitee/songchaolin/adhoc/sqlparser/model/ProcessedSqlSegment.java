package io.gitee.songchaolin.adhoc.sqlparser.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 预处理后的 SQL 段。
 * - sql：清理后的段 SQL（去注释 + 按 ; 分段，g4 解析用此）
 * - originalSql：原始 SQL 全文（含注释，方便排查问题时对照行号/上下文）
 * - prefixSql：SET/USE 前缀（累积到下一个真实语句）
 * - sqlType + valid + errorMessage：g4 解析结果
 * - segmentIndex：段序号
 */
@Data
@AllArgsConstructor
public class ProcessedSqlSegment {
    private String sql;
    private String originalSql;
    private String prefixSql;
    private String sqlType;
    private boolean valid;
    private String errorMessage;
    private int segmentIndex;
}
