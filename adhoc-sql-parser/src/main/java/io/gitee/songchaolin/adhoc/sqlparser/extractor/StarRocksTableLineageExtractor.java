package io.gitee.songchaolin.adhoc.sqlparser.extractor;

import com.starrocks.sql.parser.StarRocksBaseVisitor;
import com.starrocks.sql.parser.StarRocksParser;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * StarRocks 表级血缘提取器（Visitor）：
 * - visitCommonTableExpression -> 收集 CTE 名（WITH name AS (...)），CTE 不是物理表
 * - visitTableAtom（relationPrimary #tableAtom）-> 源表（FROM/JOIN 中的 qualifiedName），排除 CTE 名
 * - visitInsertStatement / visitCreateTableStatement / visitCreateTableAsSelectStatement /
 *   visitCreateViewStatement / visitUpdateStatement / visitDeleteStatement -> 目标表（ctx.qualifiedName()）
 * 对照 Spark TableLineageExtractor（visitNamedQuery 排除 CTE + visitTableName 收集 source）。
 */
public class StarRocksTableLineageExtractor extends StarRocksBaseVisitor<Void> {

    private final Set<String> sourceTables = new LinkedHashSet<>();
    private final Set<String> sinkTables = new LinkedHashSet<>();
    /** CTE 名（WITH name AS (...)），不是物理表，从 source 中排除。 */
    private final Set<String> cteNames = new LinkedHashSet<>();

    public static StarRocksTableLineageExtractor extract(StarRocksParser parser) {
        StarRocksTableLineageExtractor ext = new StarRocksTableLineageExtractor();
        ext.visit(parser.sqlStatements().singleStatement(0).statement());
        return ext;
    }

    /** CTE 定义：WITH name AS (...) -> 记录 CTE 名（先于 visitTableAtom，因 withClause 在 query 前）。 */
    @Override
    public Void visitCommonTableExpression(StarRocksParser.CommonTableExpressionContext ctx) {
        if (ctx.name != null) {
            cteNames.add(ctx.name.getText());
        }
        return super.visitCommonTableExpression(ctx);
    }

    /** 源表：FROM/JOIN 中的表（relationPrimary #tableAtom，qualifiedName）。排除 CTE 名（不是物理表）。 */
    @Override
    public Void visitTableAtom(StarRocksParser.TableAtomContext ctx) {
        if (ctx.qualifiedName() != null) {
            String name = ctx.qualifiedName().getText();
            if (!cteNames.contains(name)) {
                sourceTables.add(name);
            }
        }
        return null;
    }

    /** 目标表：INSERT。继续 visit 收集 SELECT 部分的 source。 */
    @Override
    public Void visitInsertStatement(StarRocksParser.InsertStatementContext ctx) {
        if (ctx.qualifiedName() != null) {
            sinkTables.add(ctx.qualifiedName().getText());
        }
        return super.visitInsertStatement(ctx);
    }

    /** 目标表：CREATE TABLE。 */
    @Override
    public Void visitCreateTableStatement(StarRocksParser.CreateTableStatementContext ctx) {
        if (ctx.qualifiedName() != null) {
            sinkTables.add(ctx.qualifiedName().getText());
        }
        return super.visitCreateTableStatement(ctx);
    }

    /** 目标表：CTAS。继续 visit 收集 SELECT 部分的 source。 */
    @Override
    public Void visitCreateTableAsSelectStatement(StarRocksParser.CreateTableAsSelectStatementContext ctx) {
        if (ctx.qualifiedName() != null) {
            sinkTables.add(ctx.qualifiedName().getText());
        }
        return super.visitCreateTableAsSelectStatement(ctx);
    }

    /** 目标表：CREATE VIEW。 */
    @Override
    public Void visitCreateViewStatement(StarRocksParser.CreateViewStatementContext ctx) {
        if (ctx.qualifiedName() != null) {
            sinkTables.add(ctx.qualifiedName().getText());
        }
        return super.visitCreateViewStatement(ctx);
    }

    /** 目标表：UPDATE。 */
    @Override
    public Void visitUpdateStatement(StarRocksParser.UpdateStatementContext ctx) {
        if (ctx.qualifiedName() != null) {
            sinkTables.add(ctx.qualifiedName().getText());
        }
        return super.visitUpdateStatement(ctx);
    }

    /** 目标表：DELETE。 */
    @Override
    public Void visitDeleteStatement(StarRocksParser.DeleteStatementContext ctx) {
        if (ctx.qualifiedName() != null) {
            sinkTables.add(ctx.qualifiedName().getText());
        }
        return super.visitDeleteStatement(ctx);
    }

    public Set<String> getSourceTables() {
        return sourceTables;
    }

    public Set<String> getSinkTables() {
        return sinkTables;
    }
}