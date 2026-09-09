package io.gitee.songchaolin.adhoc.common.util;

import io.gitee.songchaolin.adhoc.common.enums.SqlType;

/**
 * SQL 类型工具类（增强版）
 */
public final class SqlTypeUtils {

    private SqlTypeUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 判断 SQL 类型是否有结果集（增强版）。
     *
     * 支持降级判断：
     * 1. 如果 sqlType 为 DQL/AUX -> 有结果集
     * 2. 如果解析失败，从 SQL 文本推断
     *
     * @param sqlType SQL 类型字符串
     * @param sql SQL 文本（用于降级判断）
     * @param valid 是否解析成功
     * @return true 表示有结果集
     */
    public static boolean hasResultSet(String sqlType, String sql, boolean valid) {
        // 第一层：精确判断（解析成功）
        if (valid && (SqlType.DQL.is(sqlType) || SqlType.AUX.is(sqlType))) {
            return true;
        }

        // 第二层：模糊判断（解析失败，从 SQL 文本推断）
        if (!valid) {
            return inferHasResultSetFromText(sql);
        }

        return false;
    }

    /**
     * 旧版兼容方法
     */
    public static boolean hasResultSet(String sqlType) {
        return SqlType.DQL.is(sqlType) || SqlType.AUX.is(sqlType);
    }

    /**
     * 从 SQL 文本推断是否有结果集（降级策略）
     */
    private static boolean inferHasResultSetFromText(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }

        String upper = sql.trim().toUpperCase();

        // SELECT / WITH 开头 -> 有结果集
        if (upper.startsWith("SELECT") || upper.startsWith("WITH") || upper.startsWith("(SELECT")) {
            return true;
        }

        // SHOW / DESCRIBE / EXPLAIN -> 有结果集
        if (upper.startsWith("SHOW") || upper.startsWith("DESC") || upper.startsWith("EXPLAIN")) {
            return true;
        }

        // INSERT / UPDATE / DELETE -> 无结果集
        if (upper.startsWith("INSERT") || upper.startsWith("UPDATE") || upper.startsWith("DELETE")) {
            return false;
        }

        // CREATE / ALTER / DROP -> 无结果集
        if (upper.startsWith("CREATE") || upper.startsWith("ALTER") || upper.startsWith("DROP") || upper.startsWith("TRUNCATE")) {
            return false;
        }

        // SET / USE -> 无结果集
        if (upper.startsWith("SET") || upper.startsWith("USE")) {
            return false;
        }

        // 无法判断，保守返回 false
        return false;
    }
}