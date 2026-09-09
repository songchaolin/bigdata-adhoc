package io.gitee.songchaolin.adhoc.sqlparser.engine;

import io.gitee.songchaolin.adhoc.common.model.SqlParseResult;
import io.gitee.songchaolin.adhoc.common.spi.SqlParserEngine;
import io.gitee.songchaolin.adhoc.sqlparser.extractor.SqlTypeExtractor;
import io.gitee.songchaolin.adhoc.sqlparser.extractor.TableLineageExtractor;
import io.gitee.songchaolin.adhoc.sqlparser.parser.CollectingErrorListener;
import io.gitee.songchaolin.adhoc.sqlparser.parser.SqlParserFactory;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.apache.spark.sql.catalyst.parser.SqlBaseParser;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kyuubi（Spark SQL）g4 解析引擎：SqlType 提取 + 表级血缘 + 语法校验。
 * Spark 3.5 SqlBaseLexer/Parser.g4，ANTLR 4.9.3。
 * 语法错误用 CollectingErrorListener 捕获详情（行/列/token），构造 visual marker。
 */
public class KyuubiSparkParserEngine implements SqlParserEngine {

    // INSERT 目标表提取（g4 visitor 对 INSERT 的目标表提取较复杂，MVP 用 regex 补充）
    private static final Pattern INSERT_TARGET = Pattern.compile(
            "INSERT\\s+(INTO|OVERWRITE)\\s+(TABLE\\s+)?(\\S+)", Pattern.CASE_INSENSITIVE);

    @Override
    public SqlParseResult parse(String sql) {
        CollectingErrorListener errorListener = new CollectingErrorListener();
        try {
            SqlBaseParser parser1 = SqlParserFactory.createParser(sql);
            parser1.removeErrorListeners(); // 去 ConsoleErrorListener（避免 stderr 噪声）
            parser1.setErrorHandler(new DefaultErrorStrategy()); // 覆盖 BailErrorStrategy -> 默认（通知 listener + 不抛，收集错误详情）
            parser1.addErrorListener(errorListener);
            String sqlType = SqlTypeExtractor.extract(parser1);

            if (parser1.getNumberOfSyntaxErrors() > 0 || errorListener.hasError()) {
                return new SqlParseResult("UNKNOWN", java.util.Collections.emptyList(),
                        java.util.Collections.emptyList(), false,
                        buildVisualError(sql, errorListener, "syntax errors: " + parser1.getNumberOfSyntaxErrors()));
            }

            SqlBaseParser parser2 = SqlParserFactory.createParser(sql);
            TableLineageExtractor lineageExt = TableLineageExtractor.extract(parser2);

            java.util.List<String> sources = new ArrayList<>(lineageExt.getSourceTables());
            java.util.List<String> sinks = new ArrayList<>(lineageExt.getSinkTables());

            // INSERT 目标表（regex 补充，g4 visitor 不直接提取）
            if ("DML_INSERT".equals(sqlType)) {
                Matcher m = INSERT_TARGET.matcher(sql.trim());
                if (m.find()) {
                    sinks.add(m.group(3).replaceAll(";$", ""));
                }
            }

            return new SqlParseResult(sqlType, sources, sinks, true, null);
        } catch (Exception e) {
            String msg;
            if (errorListener.hasError()) {
                msg = buildVisualError(sql, errorListener, null);
            } else {
                msg = e.getMessage();
                if (msg == null || msg.isEmpty()) {
                    msg = e.getClass().getSimpleName() + " at: " + sql.substring(0, Math.min(sql.length(), 80));
                }
            }
            return new SqlParseResult("UNKNOWN", java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(), false, msg);
        }
    }

    /**
     * 构造带 visual marker 的错误消息：
     *   sSELECT * from demo_metrics limit 100
     *   ^------ line 1:0 mismatched input 'sSELECT' expecting {SELECT, INSERT, ...}
     *
     * 用原始 SQL 的 token（保留大小写），替换 ANTLR msg 里的大写 token。
     */
    private static String buildVisualError(String sql, CollectingErrorListener listener, String fallback) {
        if (!listener.hasError()) {
            return fallback != null ? fallback : "unknown syntax error";
        }
        int line = listener.getLine();
        int col = listener.getCol();
        String[] lines = sql.split("\n", -1);
        String errorLine = line >= 1 && line <= lines.length ? lines[line - 1] : sql;
        int tokenLen = listener.getOffendingToken() != null ? listener.getOffendingToken().length() : 1;

        // 用原始 SQL 的 token（保留大小写），替换 msg 里的（UpperCaseCharStream 导致大写）
        String originalToken = errorLine.length() >= col + tokenLen
                ? errorLine.substring(col, col + tokenLen)
                : listener.getOffendingToken();
        String msg = listener.getMsg();
        if (listener.getOffendingToken() != null && !originalToken.equals(listener.getOffendingToken())) {
            msg = msg.replace(listener.getOffendingToken(), originalToken);
        }

        // visual marker: col 个空格 + ^ + (tokenLen-1) 个 -
        StringBuilder marker = new StringBuilder();
        for (int i = 0; i < col; i++) {
            marker.append(' ');
        }
        marker.append('^');
        for (int i = 1; i < tokenLen; i++) {
            marker.append('-');
        }

        return "  " + errorLine + "\n"
                + "  " + marker.toString() + " line " + line + ":" + col + " " + msg;
    }
}
