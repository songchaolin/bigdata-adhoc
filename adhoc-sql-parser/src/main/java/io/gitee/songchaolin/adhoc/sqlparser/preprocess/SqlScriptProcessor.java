package io.gitee.songchaolin.adhoc.sqlparser.preprocess;

import io.gitee.songchaolin.adhoc.common.model.SqlParseResult;
import io.gitee.songchaolin.adhoc.common.spi.SqlParserEngine;
import io.gitee.songchaolin.adhoc.sqlparser.engine.SmartFallbackParserEngine;
import io.gitee.songchaolin.adhoc.sqlparser.model.ProcessedSqlSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 脚本预处理器（完整管线）：
 * 1. SqlCommentRemover 去注释
 * 2. SqlScriptSplitter 字符串感知按 ; 分段
 * 3. SmartFallbackParserEngine g4 解析 + 降级策略
 * 4. SESSION_CONFIG(SET/USE) 累积为 prefixSql，合并到下一个真实语句
 *
 * 参考 Spark AbstractSqlParser.parsePlan（g4 解析树 -> AstBuilder）+
 * Linkis SQLCommentHelper + SQLCodeParser（去注释 + 分段）。
 */
public class SqlScriptProcessor {

    private final SqlParserEngine parser;

    public SqlScriptProcessor() {
        // 使用智能降级解析器
        this.parser = new SmartFallbackParserEngine();
    }

    public SqlScriptProcessor(SqlParserEngine parser) {
        this.parser = parser;
    }

    public List<ProcessedSqlSegment> process(String sqlContent) {
        // 1. 去注释
        String cleaned = SqlCommentRemover.removeComments(sqlContent);

        // 2. 字符串感知分段
        List<String> statements = SqlScriptSplitter.split(cleaned);

        // 3. 逐段解析 + SET/USE prefix 合并
        List<ProcessedSqlSegment> result = new ArrayList<>();
        StringBuilder prefix = new StringBuilder();
        int index = 0;
        for (String stmt : statements) {
            SqlParseResult pr = parser.parse(stmt);
            if ("SESSION_CONFIG".equals(pr.getSqlType())) {
                // SET/USE -> 累积为 prefixSql（即使 valid=false 也要累积）
                if (prefix.length() > 0) {
                    prefix.append("\n");
                }
                prefix.append(stmt);
            } else {
                // 真实语句（或解析失败的语句）
                result.add(new ProcessedSqlSegment(
                        stmt,
                        sqlContent,
                        prefix.length() > 0 ? prefix.toString() : null,
                        pr.getSqlType(),
                        pr.isValid(),
                        pr.getErrorMessage(),
                        index++));
                prefix.setLength(0);
            }
        }
        return result;
    }
}
