package io.gitee.songchaolin.adhoc.sqlparser.extractor;

import com.starrocks.sql.parser.StarRocksBaseVisitor;
import com.starrocks.sql.parser.StarRocksParser;

/**
 * StarRocks SqlType 提取器（Visitor，仿 StarRocks AstBuilder.visitSingleStatement 自动分发）。
 * 不重写 visitStatement，靠 visitChildren 自动分发到各 visitXxxStatement。
 * 参考 StarRocks 3.5.8 fe/fe-core/.../sql/parser/AstBuilder.java。
 */
public class StarRocksSqlTypeExtractor extends StarRocksBaseVisitor<String> {

    public static String extract(StarRocksParser parser) {
        return new StarRocksSqlTypeExtractor()
                .visit(parser.sqlStatements().singleStatement(0).statement());
    }

    // === DQL ===
    @Override
    public String visitQueryStatement(StarRocksParser.QueryStatementContext ctx) { return "DQL"; }

    // === CTAS ===
    @Override
    public String visitCreateTableAsSelectStatement(StarRocksParser.CreateTableAsSelectStatementContext ctx) { return "CTAS"; }

    // === DML ===
    @Override
    public String visitInsertStatement(StarRocksParser.InsertStatementContext ctx) { return "DML_INSERT"; }
    @Override
    public String visitUpdateStatement(StarRocksParser.UpdateStatementContext ctx) { return "DML_MODIFY"; }
    @Override
    public String visitDeleteStatement(StarRocksParser.DeleteStatementContext ctx) { return "DML_MODIFY"; }

    // === SESSION_CONFIG ===
    @Override
    public String visitSetStatement(StarRocksParser.SetStatementContext ctx) { return "SESSION_CONFIG"; }
    @Override
    public String visitUseDatabaseStatement(StarRocksParser.UseDatabaseStatementContext ctx) { return "SESSION_CONFIG"; }
    @Override
    public String visitUseCatalogStatement(StarRocksParser.UseCatalogStatementContext ctx) { return "SESSION_CONFIG"; }
    @Override
    public String visitSetCatalogStatement(StarRocksParser.SetCatalogStatementContext ctx) { return "SESSION_CONFIG"; }

    // === DDL_CREATE ===
    @Override
    public String visitCreateTableStatement(StarRocksParser.CreateTableStatementContext ctx) { return "DDL_CREATE"; }
    @Override
    public String visitCreateTableLikeStatement(StarRocksParser.CreateTableLikeStatementContext ctx) { return "DDL_CREATE"; }
    @Override
    public String visitCreateViewStatement(StarRocksParser.CreateViewStatementContext ctx) { return "DDL_CREATE"; }
    @Override
    public String visitCreateMaterializedViewStatement(StarRocksParser.CreateMaterializedViewStatementContext ctx) { return "DDL_CREATE"; }
    @Override
    public String visitCreateDbStatement(StarRocksParser.CreateDbStatementContext ctx) { return "DDL_CREATE"; }
    @Override
    public String visitCreateIndexStatement(StarRocksParser.CreateIndexStatementContext ctx) { return "DDL_CREATE"; }
    @Override
    public String visitCreateExternalCatalogStatement(StarRocksParser.CreateExternalCatalogStatementContext ctx) { return "DDL_CREATE"; }
    @Override
    public String visitCreateDictionaryStatement(StarRocksParser.CreateDictionaryStatementContext ctx) { return "DDL_CREATE"; }

    // === DDL_ALTER ===
    @Override
    public String visitAlterTableStatement(StarRocksParser.AlterTableStatementContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitAlterViewStatement(StarRocksParser.AlterViewStatementContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitAlterDbQuotaStatement(StarRocksParser.AlterDbQuotaStatementContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitAlterDatabaseRenameStatement(StarRocksParser.AlterDatabaseRenameStatementContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitAlterMaterializedViewStatement(StarRocksParser.AlterMaterializedViewStatementContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitAlterCatalogStatement(StarRocksParser.AlterCatalogStatementContext ctx) { return "DDL_ALTER"; }
    @Override
    public String visitAlterSystemStatement(StarRocksParser.AlterSystemStatementContext ctx) { return "DDL_ALTER"; }

    // === DDL_DROP ===
    @Override
    public String visitDropTableStatement(StarRocksParser.DropTableStatementContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitCleanTemporaryTableStatement(StarRocksParser.CleanTemporaryTableStatementContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitTruncateTableStatement(StarRocksParser.TruncateTableStatementContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitDropViewStatement(StarRocksParser.DropViewStatementContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitDropMaterializedViewStatement(StarRocksParser.DropMaterializedViewStatementContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitDropDbStatement(StarRocksParser.DropDbStatementContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitDropIndexStatement(StarRocksParser.DropIndexStatementContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitDropExternalCatalogStatement(StarRocksParser.DropExternalCatalogStatementContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitDropDictionaryStatement(StarRocksParser.DropDictionaryStatementContext ctx) { return "DDL_DROP"; }
    @Override
    public String visitDropFunctionStatement(StarRocksParser.DropFunctionStatementContext ctx) { return "DDL_DROP"; }

    // === AUX（show* / desc* / help*） ===
    @Override
    public String visitShowDatabasesStatement(StarRocksParser.ShowDatabasesStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowCreateDbStatement(StarRocksParser.ShowCreateDbStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowCreateTableStatement(StarRocksParser.ShowCreateTableStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowTableStatement(StarRocksParser.ShowTableStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowTemporaryTablesStatement(StarRocksParser.ShowTemporaryTablesStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowTableStatusStatement(StarRocksParser.ShowTableStatusStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowColumnStatement(StarRocksParser.ShowColumnStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowAlterStatement(StarRocksParser.ShowAlterStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowPartitionsStatement(StarRocksParser.ShowPartitionsStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowTabletStatement(StarRocksParser.ShowTabletStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowIndexStatement(StarRocksParser.ShowIndexStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowMaterializedViewsStatement(StarRocksParser.ShowMaterializedViewsStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowCreateExternalCatalogStatement(StarRocksParser.ShowCreateExternalCatalogStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowCatalogsStatement(StarRocksParser.ShowCatalogsStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowFunctionsStatement(StarRocksParser.ShowFunctionsStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowPrivilegesStatement(StarRocksParser.ShowPrivilegesStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowVariablesStatement(StarRocksParser.ShowVariablesStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowAnalyzeStatement(StarRocksParser.ShowAnalyzeStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowStatsMetaStatement(StarRocksParser.ShowStatsMetaStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowHistogramMetaStatement(StarRocksParser.ShowHistogramMetaStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowComputeNodesStatement(StarRocksParser.ShowComputeNodesStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowResourceGroupStatement(StarRocksParser.ShowResourceGroupStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowResourceGroupUsageStatement(StarRocksParser.ShowResourceGroupUsageStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowResourceStatement(StarRocksParser.ShowResourceStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowLoadStatement(StarRocksParser.ShowLoadStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowLoadWarningsStatement(StarRocksParser.ShowLoadWarningsStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowRoutineLoadStatement(StarRocksParser.ShowRoutineLoadStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowRoutineLoadTaskStatement(StarRocksParser.ShowRoutineLoadTaskStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowCreateRoutineLoadStatement(StarRocksParser.ShowCreateRoutineLoadStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowStreamLoadStatement(StarRocksParser.ShowStreamLoadStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowStorageVolumesStatement(StarRocksParser.ShowStorageVolumesStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowDictionaryStatement(StarRocksParser.ShowDictionaryStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowExportStatement(StarRocksParser.ShowExportStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowSmallFilesStatement(StarRocksParser.ShowSmallFilesStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowFailPointStatement(StarRocksParser.ShowFailPointStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowWarehousesStatement(StarRocksParser.ShowWarehousesStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowClustersStatement(StarRocksParser.ShowClustersStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowNodesStatement(StarRocksParser.ShowNodesStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowPlanAdvisorStatement(StarRocksParser.ShowPlanAdvisorStatementContext ctx) { return "AUX"; }
    @Override
    public String visitShowPipeStatement(StarRocksParser.ShowPipeStatementContext ctx) { return "AUX"; }
    @Override
    public String visitDescTableStatement(StarRocksParser.DescTableStatementContext ctx) { return "AUX"; }
    @Override
    public String visitDescStorageVolumeStatement(StarRocksParser.DescStorageVolumeStatementContext ctx) { return "AUX"; }
    @Override
    public String visitDescPipeStatement(StarRocksParser.DescPipeStatementContext ctx) { return "AUX"; }
    @Override
    public String visitHelpStatement(StarRocksParser.HelpStatementContext ctx) { return "AUX"; }

    @Override
    protected String defaultResult() {
        return "UNKNOWN";
    }
}
