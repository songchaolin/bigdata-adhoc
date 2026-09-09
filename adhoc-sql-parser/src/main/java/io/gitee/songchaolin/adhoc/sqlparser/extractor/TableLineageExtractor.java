package io.gitee.songchaolin.adhoc.sqlparser.extractor;

import org.apache.spark.sql.catalyst.parser.SqlBaseParser;
import org.apache.spark.sql.catalyst.parser.SqlBaseParserBaseVisitor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 表级血缘提取器（Visitor 模式）：
 * - visitNamedQuery -> 收集 CTE 名（WITH name AS (...)），CTE 不是物理表
 * - visitTableName（relationPrimary #tableName）-> 源表（FROM/JOIN 中的表），排除 CTE 名
 * - visitCreateTable / visitReplaceTable -> 目标表（CREATE TABLE 的表名）
 */
public class TableLineageExtractor extends SqlBaseParserBaseVisitor<Void> {

    private final Set<String> sourceTables = new LinkedHashSet<>();
    private final Set<String> sinkTables = new LinkedHashSet<>();
    /** CTE 名（WITH name AS (...)），不是物理表，从 source 中排除。 */
    private final Set<String> cteNames = new LinkedHashSet<>();

    public static TableLineageExtractor extract(SqlBaseParser parser) {
        TableLineageExtractor ext = new TableLineageExtractor();
        ext.visit(parser.singleStatement().statement());
        return ext;
    }

    /** CTE 定义：WITH name AS (...) -> 记录 CTE 名（先于后续 visitTableName 收集，因 withClause 在 query 前）。 */
    @Override
    public Void visitNamedQuery(SqlBaseParser.NamedQueryContext ctx) {
        if (ctx.name != null) {
            cteNames.add(ctx.name.getText());
        }
        return super.visitNamedQuery(ctx);
    }

    /** 源表：FROM/JOIN 中的表（relationPrimary #tableName）。排除 CTE 名（不是物理表）。 */
    @Override
    public Void visitTableName(SqlBaseParser.TableNameContext ctx) {
        if (ctx.identifierReference() != null) {
            String name = ctx.identifierReference().getText();
            if (!cteNames.contains(name)) {
                sourceTables.add(name);
            }
        }
        return null;
    }

    /** 目标表：CREATE TABLE。 */
    @Override
    public Void visitCreateTable(SqlBaseParser.CreateTableContext ctx) {
        if (ctx.createTableHeader() != null && ctx.createTableHeader().identifierReference() != null) {
            sinkTables.add(ctx.createTableHeader().identifierReference().getText());
        }
        return super.visitCreateTable(ctx);
    }

    /** 目标表：CREATE VIEW。 */
    @Override
    public Void visitCreateView(SqlBaseParser.CreateViewContext ctx) {
        if (ctx.identifierReference() != null) {
            sinkTables.add(ctx.identifierReference().getText());
        }
        return super.visitCreateView(ctx);
    }

    /** 目标表：REPLACE TABLE。 */
    @Override
    public Void visitReplaceTable(SqlBaseParser.ReplaceTableContext ctx) {
        if (ctx.replaceTableHeader() != null && ctx.replaceTableHeader().identifierReference() != null) {
            sinkTables.add(ctx.replaceTableHeader().identifierReference().getText());
        }
        return super.visitReplaceTable(ctx);
    }

    public Set<String> getSourceTables() {
        return sourceTables;
    }

    public Set<String> getSinkTables() {
        return sinkTables;
    }
}
