package io.gitee.songchaolin.adhoc.sqlparser.preprocess;

/**
 * SQL 注释清除器（参考 Linkis SQLCommentHelper）。
 * 移除 -- 行注释、块注释(保留 hint)、# 行注释，保留字符串字面量。
 *
 * <p>用 char-by-char 状态机替代 regex（regex 的 (?:''|[^'])* 在长 SQL 上会 StackOverflowError，
 * Java regex 引擎是递归 NFA，长匹配栈溢出）。状态机 O(n) 无递归，任意长度安全。
 */
public final class SqlCommentRemover {

    private SqlCommentRemover() {
    }

    public static String removeComments(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        StringBuilder out = new StringBuilder(sql.length());
        int len = sql.length();
        int i = 0;
        while (i < len) {
            char c = sql.charAt(i);

            // 单引号字符串 -> 原样保留（含 '' 转义）
            if (c == '\'') {
                i = copyQuoted(sql, i, len, out, '\'');
                continue;
            }
            // 双引号标识符 -> 原样保留（含 "" 转义）
            if (c == '"') {
                i = copyQuoted(sql, i, len, out, '"');
                continue;
            }
            // 行注释 -- -> 跳过到行尾
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                i += 2;
                while (i < len && sql.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            // # 行注释 -> 跳过到行尾
            if (c == '#') {
                i++;
                while (i < len && sql.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            // 块注释 /* ... */（但 /*+ hint */ 保留）
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                if (i + 2 < len && sql.charAt(i + 2) == '+') {
                    // hint /*+ ... */ -> 保留（逐字符追加，不用跳过）
                    out.append(c);
                    i++;
                    continue;
                } else {
                    // 块注释 -> 跳过到 */
                    i += 2;
                    while (i + 1 < len && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                        i++;
                    }
                    i = Math.min(i + 2, len); // 跳过 */
                    continue;
                }
            }
            // 普通字符
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** 复制引号字符串（单引号/双引号），处理 '' / "" / \' / \" 转义。返回下一个未处理位置。 */
    private static int copyQuoted(String sql, int i, int len, StringBuilder out, char quote) {
        out.append(quote);
        i++;
        while (i < len) {
            char c = sql.charAt(i);
            out.append(c);
            i++;

            // 处理反斜杠转义（新增）
            if (c == '\\' && i < len) {
                out.append(sql.charAt(i));
                i++;
                continue;
            }

            // 处理引号转义（'' 或 ""）
            if (c == quote) {
                if (i < len && sql.charAt(i) == quote) {
                    // 转义（'' 或 ""）-> 追加第二个引号，继续
                    out.append(sql.charAt(i));
                    i++;
                } else {
                    break; // 字符串结束
                }
            }
        }
        return i;
    }
}
