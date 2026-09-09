package io.gitee.songchaolin.adhoc.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 危险 SQL 语句检测器：扫描清理后的 SQL 文本，拦截关键安全类语句。
 *
 * 拦截类别（用户指定）：
 * 1. 代码注入：ADD JAR / ADD FILE / CREATE FUNCTION
 * 2. 文件系统：LOAD DATA / INTO OUTFILE / INTO DUMPFILE
 * 3. 权限：GRANT / REVOKE / CREATE USER / DROP USER / SET PASSWORD / ALTER USER
 * 4. 系统管理：ALTER SYSTEM / ADMIN SET CONFIG / INSTALL PLUGIN
 * 5. 数据销毁-库级：CREATE DATABASE / DROP DATABASE
 *
 * 不拦截（用户明确允许）：表级 DDL/DML（DROP TABLE / TRUNCATE / DELETE / UPDATE / INSERT / ALTER TABLE 等）。
 */
public final class DangerousSqlChecker {

    private DangerousSqlChecker() {}

    /** 危险模式：regex + 类别 + 描述。 */
    private static final class DangerousPattern {
        final Pattern regex;
        final String category;
        final String description;
        DangerousPattern(String regex, String category, String description) {
            this.regex = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.category = category;
            this.description = description;
        }
    }

    private static final List<DangerousPattern> PATTERNS = new ArrayList<>();
    static {
        // 1. 代码注入
        PATTERNS.add(p("\\bADD\\s+JAR\\b", "代码注入", "ADD JAR"));
        PATTERNS.add(p("\\bADD\\s+FILE\\b", "代码注入", "ADD FILE"));
        PATTERNS.add(p("\\bCREATE\\s+FUNCTION\\b", "代码注入", "CREATE FUNCTION"));
        // 2. 文件系统
        PATTERNS.add(p("\\bLOAD\\s+DATA\\b", "文件系统", "LOAD DATA"));
        PATTERNS.add(p("\\bINTO\\s+OUTFILE\\b", "文件系统", "INTO OUTFILE"));
        PATTERNS.add(p("\\bINTO\\s+DUMPFILE\\b", "文件系统", "INTO DUMPFILE"));
        // 3. 权限
        PATTERNS.add(p("\\bGRANT\\b", "权限", "GRANT"));
        PATTERNS.add(p("\\bREVOKE\\b", "权限", "REVOKE"));
        PATTERNS.add(p("\\bCREATE\\s+USER\\b", "权限", "CREATE USER"));
        PATTERNS.add(p("\\bDROP\\s+USER\\b", "权限", "DROP USER"));
        PATTERNS.add(p("\\bSET\\s+PASSWORD\\b", "权限", "SET PASSWORD"));
        PATTERNS.add(p("\\bALTER\\s+USER\\b", "权限", "ALTER USER"));
        // 4. 系统管理
        PATTERNS.add(p("\\bALTER\\s+SYSTEM\\b", "系统管理", "ALTER SYSTEM"));
        PATTERNS.add(p("\\bADMIN\\s+SET\\s+CONFIG\\b", "系统管理", "ADMIN SET CONFIG"));
        PATTERNS.add(p("\\bINSTALL\\s+PLUGIN\\b", "系统管理", "INSTALL PLUGIN"));
        // 5. 数据销毁-库级
        PATTERNS.add(p("\\bCREATE\\s+DATABASE\\b", "数据销毁-库级", "CREATE DATABASE"));
        PATTERNS.add(p("\\bDROP\\s+DATABASE\\b", "数据销毁-库级", "DROP DATABASE"));
    }

    private static DangerousPattern p(String regex, String category, String description) {
        return new DangerousPattern(regex, category, description);
    }

    /**
     * 检测 SQL 是否包含危险语句。
     * 先剥离字符串字面量（单引号/双引号内容替换为占位符），再跑 regex，避免误杀字符串中的关键字。
     * @param sql 清理后的 SQL 文本（去注释后）
     * @return 命中的危险模式描述（如 "代码注入: ADD JAR"），未命中返回 null
     */
    public static String check(String sql) {
        if (sql == null || sql.isEmpty()) {
            return null;
        }
        // 剥离字符串字面量：单引号 '...' 和双引号 "..." 内容替换为 '' / ""（避免字符串里的 GRANT 等误杀）
        String stripped = sql.replaceAll("'(?:''|[^'])*'", "''")
                             .replaceAll("\"(?:\"\"|[^\"])*\"", "\"\"");
        for (DangerousPattern dp : PATTERNS) {
            if (dp.regex.matcher(stripped).find()) {
                return dp.category + ": " + dp.description;
            }
        }
        return null;
    }
}
