package io.gitee.songchaolin.adhoc.server.metadata;

import io.gitee.songchaolin.adhoc.common.enums.EngineType;
import io.gitee.songchaolin.adhoc.metadata.MetadataService;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataColumn;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataDatabase;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * SR 元数据查询调试：经 server 完整 Spring 上下文调 {@link MetadataService}，
 * 验证 /api/metadata/* 的真实后端路径（ConfigHolder 读 yml -> StarRocksMetadataFetcher -> Druid lazy 池 -> SR）。
 *
 * <p>跑法：
 * <pre>mvn -pl adhoc-server -am test -Dtest=StarRocksMetadataDebugTest -Dsurefire.useFile=false -DfailIfNoTests=false</pre>
 * 调试时可在 IDE 直接运行本类。改 fetcher/SQL 后跑此测试即可端到端验证。
 * 需 ADHOC_SR_META_* 与 ADHOC_MYSQL_* 环境变量（未设置 SR 地址时自动跳过）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "ADHOC_SR_META_HOST", matches = ".+")
class StarRocksMetadataDebugTest {

    private static final String ENGINE = EngineType.STARROCKS.name();

    @Autowired
    private MetadataService metadataService;

    @Test
    void debugStarRocks() {
        // 1. 库
        List<MetadataDatabase> dbs = metadataService.listDatabases(ENGINE, null);
        System.out.println("=== databases ===");
        dbs.forEach(d -> System.out.println("    " + d.getName()));
        if (dbs.isEmpty()) {
            System.out.println("=== 无库 ===");
            return;
        }
        String db = dbs.get(0).getName();

        // 2. 表
        List<MetadataTable> tables = metadataService.listTables(ENGINE, db, null);
        System.out.println("=== tables db=" + db + " ===");
        tables.forEach(t -> System.out.println("    " + t.getName() + " | " + t.getType() + " | " + t.getComment()));
        if (tables.isEmpty()) {
            return;
        }
        String tbl = tables.get(0).getName();

        // 3. 列
        List<MetadataColumn> cols = metadataService.listColumns(ENGINE, db, tbl);
        System.out.println("=== columns db=" + db + " tbl=" + tbl + " ===");
        cols.forEach(c -> System.out.println("    " + c.getName() + " | " + c.getType() + " | " + c.getComment() + " | part=" + c.isPartition()));
        System.out.println("=== done ===");
    }
}
