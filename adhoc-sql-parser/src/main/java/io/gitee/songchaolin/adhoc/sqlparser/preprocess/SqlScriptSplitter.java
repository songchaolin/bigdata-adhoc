package io.gitee.songchaolin.adhoc.sqlparser.preprocess;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 脚本分段器（增强版）。
 * 按 `;` 分段，智能处理：
 * - 单引号字符串（'' 转义）
 * - 双引号标识符（"" 转义）
 * - 反斜杠转义（\' 和 \"）
 * - 美元符号字符串（$$...$$，PostgreSQL/Spark 特有）
 */
public final class SqlScriptSplitter {

    private SqlScriptSplitter() {
    }

    public static List<String> split(String sql) {
        List<String> result = new ArrayList<>();
        if (sql == null || sql.trim().isEmpty()) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inDollar = false;
        StringBuilder dollarTag = new StringBuilder();

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            // 处理美元符号字符串（$$ 或 $tag$...$tag$）
            if (!inSingle && !inDouble && c == '$') {
                if (!inDollar) {
                    String tag = readDollarTag(sql, i);
                    if (tag != null) {
                        inDollar = true;
                        dollarTag = new StringBuilder(tag);
                        current.append(tag);
                        i += tag.length() - 1;
                        continue;
                    }
                } else {
                    String tag = dollarTag.toString();
                    if (sql.substring(i).startsWith(tag)) {
                        inDollar = false;
                        current.append(tag);
                        i += tag.length() - 1;
                        continue;
                    }
                }
            }

            if (inDollar) {
                current.append(c);
                continue;
            }

            if (inSingle) {
                current.append(c);
                // 处理反斜杠转义（新增）
                if (c == '\\' && i + 1 < sql.length()) {
                    current.append(sql.charAt(i + 1));
                    i++;
                    continue;
                }
                // 处理 '' 转义
                if (c == '\'' && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    current.append('\'');
                    i++;
                } else if (c == '\'') {
                    inSingle = false;
                }
            } else if (inDouble) {
                current.append(c);
                // 处理反斜杠转义（新增）
                if (c == '\\' && i + 1 < sql.length()) {
                    current.append(sql.charAt(i + 1));
                    i++;
                    continue;
                }
                // 处理 "" 转义
                if (c == '"' && i + 1 < sql.length() && sql.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    inDouble = false;
                }
            } else {
                if (c == '\'') {
                    inSingle = true;
                    current.append(c);
                } else if (c == '"') {
                    inDouble = true;
                    current.append(c);
                } else if (c == ';') {
                    String stmt = current.toString().trim();
                    if (!stmt.isEmpty()) {
                        result.add(stmt);
                    }
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            result.add(last);
        }
        return result;
    }

    /**
     * 读取美元标签（新增）
     * 例如：$$ 或 $tag$
     */
    private static String readDollarTag(String sql, int start) {
        if (start >= sql.length() || sql.charAt(start) != '$') {
            return null;
        }

        int end = sql.indexOf('$', start + 1);
        if (end == -1) {
            return null;
        }

        String tag = sql.substring(start, end + 1);
        if (tag.equals("$$") || tag.matches("\\$[a-zA-Z_][a-zA-Z0-9_]*\\$")) {
            return tag;
        }

        return null;
    }
}