package io.gitee.songchaolin.adhoc.sqlparser.extractor;

import org.apache.spark.sql.catalyst.parser.SqlBaseParser;
import org.apache.spark.sql.catalyst.parser.SqlBaseParserBaseVisitor;

/**
 * SqlType 提取器（Visitor，覆盖 Spark 3.5 g4 statement 规则的全部 #label）。
 * 参考 Spark AbstractSqlParser.parsePlan -> astBuilder.visitSingleStatement 的思路。
 */
public class SqlTypeExtractor extends SqlBaseParserBaseVisitor<String> {

    public static String extract(SqlBaseParser parser) {
        return new SqlTypeExtractor().visit(parser.singleStatement().statement());
    }

    // === DQL ===
    @Override
    public String visitStatementDefault(SqlBaseParser.StatementDefaultContext ctx) {
        return "DQL";
    }

    // === CTAS / DDL_CREATE ===
    @Override
    public String visitCreateTable(SqlBaseParser.CreateTableContext ctx) {
        return ctx.query() != null ? "CTAS" : "DDL_CREATE";
    }

    @Override
    public String visitCreateTableLike(SqlBaseParser.CreateTableLikeContext ctx) { return "DDL_CREATE"; }

    @Override
    public String visitReplaceTable(SqlBaseParser.ReplaceTableContext ctx) {
        return ctx.query() != null ? "CTAS" : "DDL_CREATE";
    }

    @Override
    public String visitCreateNamespace(SqlBaseParser.CreateNamespaceContext ctx) { return "DDL_CREATE"; }

    @Override
    public String visitCreateView(SqlBaseParser.CreateViewContext ctx) { return "DDL_CREATE"; }

    @Override
    public String visitCreateTempViewUsing(SqlBaseParser.CreateTempViewUsingContext ctx) { return "DDL_CREATE"; }

    @Override
    public String visitCreateFunction(SqlBaseParser.CreateFunctionContext ctx) { return "DDL_CREATE"; }

    @Override
    public String visitCreateIndex(SqlBaseParser.CreateIndexContext ctx) { return "DDL_CREATE"; }

    // === DML ===
    @Override
    public String visitDmlStatement(SqlBaseParser.DmlStatementContext ctx) {
        return ctx.getText().toUpperCase().startsWith("INSERT") ? "DML_INSERT" : "DML_MODIFY";
    }

    // === DDL_ALTER ===
    @Override
    public String visitRenameTable(SqlBaseParser.RenameTableContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitAddTableColumns(SqlBaseParser.AddTableColumnsContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitDropTableColumns(SqlBaseParser.DropTableColumnsContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitRenameTableColumn(SqlBaseParser.RenameTableColumnContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitAlterTableAlterColumn(SqlBaseParser.AlterTableAlterColumnContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitSetTableProperties(SqlBaseParser.SetTablePropertiesContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitUnsetTableProperties(SqlBaseParser.UnsetTablePropertiesContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitHiveChangeColumn(SqlBaseParser.HiveChangeColumnContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitHiveReplaceColumns(SqlBaseParser.HiveReplaceColumnsContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitSetTableSerDe(SqlBaseParser.SetTableSerDeContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitAddTablePartition(SqlBaseParser.AddTablePartitionContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitRenameTablePartition(SqlBaseParser.RenameTablePartitionContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitDropTablePartitions(SqlBaseParser.DropTablePartitionsContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitSetTableLocation(SqlBaseParser.SetTableLocationContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitRecoverPartitions(SqlBaseParser.RecoverPartitionsContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitAlterViewQuery(SqlBaseParser.AlterViewQueryContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitSetNamespaceProperties(SqlBaseParser.SetNamespacePropertiesContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitSetNamespaceLocation(SqlBaseParser.SetNamespaceLocationContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitCommentTable(SqlBaseParser.CommentTableContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitCommentNamespace(SqlBaseParser.CommentNamespaceContext ctx) { return "DDL_ALTER"; }

    // === DDL_DROP ===
    @Override
    public String visitDropTable(SqlBaseParser.DropTableContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitDropView(SqlBaseParser.DropViewContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitDropNamespace(SqlBaseParser.DropNamespaceContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitDropFunction(SqlBaseParser.DropFunctionContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitDropIndex(SqlBaseParser.DropIndexContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitTruncateTable(SqlBaseParser.TruncateTableContext ctx) { return "DDL_DROP"; }

    // === SESSION_CONFIG ===
    @Override
    public String visitUse(SqlBaseParser.UseContext ctx) { return "SESSION_CONFIG"; }
    @Override
    public String visitUseNamespace(SqlBaseParser.UseNamespaceContext ctx) { return "SESSION_CONFIG"; }
    @Override
    public String visitSetCatalog(SqlBaseParser.SetCatalogContext ctx) { return "SESSION_CONFIG"; }
    @Override
    public String visitSetConfiguration(SqlBaseParser.SetConfigurationContext ctx) { return "SESSION_CONFIG"; }
    @Override
    public String visitSetQuotedConfiguration(SqlBaseParser.SetQuotedConfigurationContext ctx) { return "SESSION_CONFIG"; }
    @Override
    public String visitResetConfiguration(SqlBaseParser.ResetConfigurationContext ctx) { return "SESSION_CONFIG"; }
    @Override
    public String visitResetQuotedConfiguration(SqlBaseParser.ResetQuotedConfigurationContext ctx) { return "SESSION_CONFIG"; }
    @Override
    public String visitSetTimeZone(SqlBaseParser.SetTimeZoneContext ctx) { return "SESSION_CONFIG"; }

    // === AUX ===
    @Override
    public String visitExplain(SqlBaseParser.ExplainContext ctx) { return "AUX"; }
    @Override
    public String visitShowTables(SqlBaseParser.ShowTablesContext ctx) { return "AUX"; }
    @Override
    public String visitShowTableExtended(SqlBaseParser.ShowTableExtendedContext ctx) { return "AUX"; }
    @Override
    public String visitShowTblProperties(SqlBaseParser.ShowTblPropertiesContext ctx) { return "AUX"; }
    @Override
    public String visitShowColumns(SqlBaseParser.ShowColumnsContext ctx) { return "AUX"; }
    @Override
    public String visitShowViews(SqlBaseParser.ShowViewsContext ctx) { return "AUX"; }
    @Override
    public String visitShowPartitions(SqlBaseParser.ShowPartitionsContext ctx) { return "AUX"; }
    @Override
    public String visitShowFunctions(SqlBaseParser.ShowFunctionsContext ctx) { return "AUX"; }
    @Override
    public String visitShowCreateTable(SqlBaseParser.ShowCreateTableContext ctx) { return "AUX"; }
    @Override
    public String visitShowCurrentNamespace(SqlBaseParser.ShowCurrentNamespaceContext ctx) { return "AUX"; }
    @Override
    public String visitShowNamespaces(SqlBaseParser.ShowNamespacesContext ctx) { return "AUX"; }
    @Override
    public String visitShowCatalogs(SqlBaseParser.ShowCatalogsContext ctx) { return "AUX"; }
    @Override
    public String visitDescribeQuery(SqlBaseParser.DescribeQueryContext ctx) { return "AUX"; }
    @Override
    public String visitDescribeRelation(SqlBaseParser.DescribeRelationContext ctx) { return "AUX"; }
    @Override
    public String visitDescribeFunction(SqlBaseParser.DescribeFunctionContext ctx) { return "AUX"; }
    @Override
    public String visitDescribeNamespace(SqlBaseParser.DescribeNamespaceContext ctx) { return "AUX"; }
    @Override
    public String visitAnalyze(SqlBaseParser.AnalyzeContext ctx) { return "AUX"; }
    @Override
    public String visitAnalyzeTables(SqlBaseParser.AnalyzeTablesContext ctx) { return "AUX"; }
    @Override
    public String visitCacheTable(SqlBaseParser.CacheTableContext ctx) { return "AUX"; }
    @Override
    public String visitUncacheTable(SqlBaseParser.UncacheTableContext ctx) { return "AUX"; }
    @Override
    public String visitClearCache(SqlBaseParser.ClearCacheContext ctx) { return "AUX"; }
    @Override
    public String visitRefreshTable(SqlBaseParser.RefreshTableContext ctx) { return "AUX"; }
    @Override
    public String visitRefreshFunction(SqlBaseParser.RefreshFunctionContext ctx) { return "AUX"; }
    @Override
    public String visitRefreshResource(SqlBaseParser.RefreshResourceContext ctx) { return "AUX"; }
    @Override
    public String visitLoadData(SqlBaseParser.LoadDataContext ctx) { return "AUX"; }
    @Override
    public String visitRepairTable(SqlBaseParser.RepairTableContext ctx) { return "AUX"; }

    @Override
    protected String defaultResult() {
        return "UNKNOWN";
    }
}
