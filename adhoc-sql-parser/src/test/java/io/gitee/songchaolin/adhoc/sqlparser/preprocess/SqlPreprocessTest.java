package io.gitee.songchaolin.adhoc.sqlparser.preprocess;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 预处理测试：去注释 + 字符串感知 `;` 分段。
 */
class SqlPreprocessTest {

    @Test
    void removeLineComment() {
        String sql = "SELECT 1 -- this is a comment\nFROM t";
        String result = SqlCommentRemover.removeComments(sql);
        assertThat(result).doesNotContain("this is a comment");
        assertThat(result).contains("SELECT 1").contains("FROM t");
    }

    @Test
    void removeBlockComment() {
        String sql = "SELECT /* block comment */ 1 FROM t";
        String result = SqlCommentRemover.removeComments(sql);
        assertThat(result).doesNotContain("block comment");
        assertThat(result).contains("SELECT").contains("1");
    }

    @Test
    void removeHashComment() {
        String sql = "SELECT 1 # hash comment\nFROM t";
        String result = SqlCommentRemover.removeComments(sql);
        assertThat(result).doesNotContain("hash comment");
    }

    @Test
    void preserveStringWithCommentSyntax() {
        String sql = "SELECT '-- not a comment' FROM t";
        String result = SqlCommentRemover.removeComments(sql);
        assertThat(result).contains("-- not a comment");
    }

    @Test
    void preserveHint() {
        String sql = "SELECT /*+ hint */ 1 FROM t";
        String result = SqlCommentRemover.removeComments(sql);
        assertThat(result).contains("/*+ hint */");
    }

    @Test
    void splitRespectsSemicolonInSingleQuote() {
        List<String> stmts = SqlScriptSplitter.split("SELECT ';'; SELECT 2");
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).isEqualTo("SELECT ';'");
        assertThat(stmts.get(1)).isEqualTo("SELECT 2");
    }

    @Test
    void splitRespectsSemicolonInDoubleQuote() {
        List<String> stmts = SqlScriptSplitter.split("SELECT \"col;name\" FROM t; SELECT 2");
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).contains("\"col;name\"");
    }

    @Test
    void splitMultiStatement() {
        List<String> stmts = SqlScriptSplitter.split("SELECT 1; SELECT 2; SELECT 3");
        assertThat(stmts).hasSize(3);
    }

    @Test
    void splitTrailingSemicolon() {
        List<String> stmts = SqlScriptSplitter.split("SELECT 1; ; ");
        assertThat(stmts).hasSize(1);
        assertThat(stmts.get(0)).isEqualTo("SELECT 1");
    }

    @Test
    void commentRemoveThenSplit() {
        // 注释里有 `;`，去注释后分段不应多分
        String sql = "SELECT 1 -- comment ;\nFROM t; SELECT 2";
        String cleaned = SqlCommentRemover.removeComments(sql);
        List<String> stmts = SqlScriptSplitter.split(cleaned);
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).contains("SELECT 1").contains("FROM t");
        assertThat(stmts.get(1)).isEqualTo("SELECT 2");
    }

    @Test
    void splitEscapedSingleQuote() {
        List<String> stmts = SqlScriptSplitter.split("SELECT 'a''b'; SELECT 2");
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).isEqualTo("SELECT 'a''b'");
    }
}
