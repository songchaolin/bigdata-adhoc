package io.gitee.songchaolin.adhoc.sqlparser.engine;

import com.starrocks.sql.parser.StarRocksParser;
import io.gitee.songchaolin.adhoc.common.model.SqlParseResult;
import io.gitee.songchaolin.adhoc.common.spi.SqlParserEngine;
import io.gitee.songchaolin.adhoc.sqlparser.extractor.StarRocksSqlTypeExtractor;
import io.gitee.songchaolin.adhoc.sqlparser.extractor.StarRocksTableLineageExtractor;
import io.gitee.songchaolin.adhoc.sqlparser.parser.StarRocksSqlParserFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * StarRocks g4 解析引擎（参考 StarRocks 3.5.8 SqlParser.parseWithStarRocksDialect）。
 * SqlType 提取（visitor）+ 表级血缘 + 语法校验（getNumberOfSyntaxErrors）。
 */
public class StarRocksParserEngine implements SqlParserEngine {

    @Override
    public SqlParseResult parse(String sql) {
        StarRocksParser parser1 = null;
        try {
            parser1 = StarRocksSqlParserFactory.createParser(sql);
            String sqlType = StarRocksSqlTypeExtractor.extract(parser1);

            // ANTLR BailErrorStrategy 语法错误抛 ParseCancellationException；保险起见再检查 syntaxErrors
            if (parser1.getNumberOfSyntaxErrors() > 0) {
                return new SqlParseResult("UNKNOWN", Collections.emptyList(), Collections.emptyList(),
                        false, "syntax errors: " + parser1.getNumberOfSyntaxErrors());
            }

            StarRocksParser parser2 = StarRocksSqlParserFactory.createParser(sql);
            StarRocksTableLineageExtractor lineageExt = StarRocksTableLineageExtractor.extract(parser2);
            List<String> sources = new ArrayList<>(lineageExt.getSourceTables());
            List<String> sinks = new ArrayList<>(lineageExt.getSinkTables());

            return new SqlParseResult(sqlType, sources, sinks, true, null);
        } catch (Exception e) {
            // BailErrorStrategy 抛 ParseCancellationException（getMessage 常为 null），用 syntaxErrors 构造消息，兜底异常类名
            String msg;
            if (parser1 != null && parser1.getNumberOfSyntaxErrors() > 0) {
                msg = "syntax errors: " + parser1.getNumberOfSyntaxErrors();
            } else if (e.getMessage() != null) {
                msg = e.getMessage();
            } else {
                msg = e.getClass().getSimpleName();
            }
            return new SqlParseResult("UNKNOWN", Collections.emptyList(), Collections.emptyList(), false, msg);
        }
    }
}
