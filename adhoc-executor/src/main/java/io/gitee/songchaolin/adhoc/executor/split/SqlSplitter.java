package io.gitee.songchaolin.adhoc.executor.split;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 拆分器（P3 MVP 用正则/字符串识别，P6 换 g4）。
 * 按 ';' 拆分；识别 SET/USE 语句作为 prefix_sql 累积到下一个真实语句（不独立成 Task）。
 * 真实语句（非 SET/USE）成为一个 Task，segment_index 递增。
 * 全是 SET/USE（无可执行 SQL）返回空列表（调用方判 ADHOC_JOB_NO_EXECUTABLE_SQL）。
 */
public class SqlSplitter {

    /**
     * 拆分整段 SQL。
     *
     * @param sqlContent 原始 SQL（多语句，';' 分隔）
     * @return 可执行段列表（segment_index 从 0 递增）；全 SET/USE 则空
     */
    public List<SqlSegment> split(String sqlContent) {
        List<SqlSegment> result = new ArrayList<>();
        if (sqlContent == null || sqlContent.trim().isEmpty()) {
            return result;
        }
        StringBuilder prefix = new StringBuilder();
        int index = 0;
        for (String part : sqlContent.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isPrefixStatement(trimmed)) {
                if (prefix.length() > 0) {
                    prefix.append("\n");
                }
                prefix.append(trimmed);
            } else {
                result.add(new SqlSegment(
                        prefix.length() > 0 ? prefix.toString() : null,
                        trimmed,
                        index++));
                prefix.setLength(0);
            }
        }
        return result;
    }

    /** 是否为 SET/USE 前缀语句（取首个 token 判断，忽略大小写）。 */
    private boolean isPrefixStatement(String sql) {
        String trimmed = sql.trim();
        int sp = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c)) {
                sp = i;
                break;
            }
        }
        String firstToken = sp < 0 ? trimmed : trimmed.substring(0, sp);
        String upper = firstToken.toUpperCase();
        return "SET".equals(upper) || "USE".equals(upper);
    }
}
