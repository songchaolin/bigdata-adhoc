package io.gitee.songchaolin.adhoc.sqlparser.preprocess;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 分段器增强测试（反斜杠转义 + 美元字符串）
 */
class SqlScriptSplitterEnhancedTest {

    @Test
    void splitBackslashEscapeSingleQuote() {
        // 反斜杠转义单引号
        List<String> stmts = SqlScriptSplitter.split("SELECT 'test\\'; DROP TABLE t; --'; SELECT 2");
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).contains("test\\'");
    }

    @Test
    void splitBackslashEscapeDoubleQuote() {
        // 反斜杠转义双引号
        List<String> stmts = SqlScriptSplitter.split("SELECT \"col;\\\"name\" FROM t; SELECT 2");
        assertThat(stmts).hasSize(2);
    }

    @Test
    void splitDollarString() {
        // PostgreSQL/Spark 美元字符串
        List<String> stmts = SqlScriptSplitter.split("SELECT $$multi;line$$; SELECT 2");
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).contains("$$multi;line$$");
    }

    @Test
    void splitDollarTagString() {
        // 带标签的美元字符串
        List<String> stmts = SqlScriptSplitter.split("SELECT $tag$multi;line$tag$; SELECT 2");
        assertThat(stmts).hasSize(2);
    }

    @Test
    void splitComplexEscapedSql() {
        // 复杂混合场景
        String sql = "SELECT 'it\\'s; a test', \"col;\\\"name\" FROM t; INSERT INTO t2 SELECT 1";
        List<String> stmts = SqlScriptSplitter.split(sql);
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).contains("it\\'s").contains("col;\\\"name");
        assertThat(stmts.get(1)).startsWith("INSERT");
    }
}