package io.gitee.songchaolin.adhoc.server.web;

import io.gitee.songchaolin.adhoc.metadata.MetadataService;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataColumn;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataDatabase;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataTable;
import io.gitee.songchaolin.adhoc.server.auth.UserContextHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 元数据自动补全端点：库/表/列查询，供前端 SQL 编辑器自动补全。
 * <p>身份走 {@link io.gitee.songchaolin.adhoc.server.auth.GatewayUserInterceptor} 注入 {@link UserContextHolder}（仅审计，
 * 直连超管不做按用户过滤）；engineType 非法抛 {@link io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode#ADHOC_ENGINE_TYPE_INVALID}。
 * 裸返回由 {@link io.gitee.songchaolin.adhoc.server.aspect.AdhocResponseAdvice} 统一包 Result。
 */
@Api(tags = "Adhoc Metadata")
@RestController
@RequestMapping("/api/metadata")
public class MetadataController {

    private static final Logger log = LoggerFactory.getLogger(MetadataController.class);

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @ApiOperation("库列表（自动补全，keyword 前缀过滤；instance 不传走默认实例）")
    @GetMapping("/databases")
    public List<MetadataDatabase> databases(
            @RequestParam String engineType,
            @RequestParam(required = false) String instance,
            @RequestParam(required = false) String keyword) {
        log.info("【元数据-库】user={} engine={} instance={} keyword={}", UserContextHolder.getUserId(), engineType, instance, keyword);
        return metadataService.listDatabases(engineType, instance, keyword);
    }

    @ApiOperation("表列表（自动补全，keyword 前缀过滤；instance 不传走默认实例）")
    @GetMapping("/tables")
    public List<MetadataTable> tables(
            @RequestParam String engineType,
            @RequestParam(required = false) String instance,
            @RequestParam String database,
            @RequestParam(required = false) String keyword) {
        log.info("【元数据-表】user={} engine={} instance={} db={} keyword={}",
                UserContextHolder.getUserId(), engineType, instance, database, keyword);
        return metadataService.listTables(engineType, instance, database, keyword);
    }

    @ApiOperation("列列表（自动补全，含分区列；instance 不传走默认实例）")
    @GetMapping("/columns")
    public List<MetadataColumn> columns(
            @RequestParam String engineType,
            @RequestParam(required = false) String instance,
            @RequestParam String database,
            @RequestParam String table) {
        log.info("【元数据-列】user={} engine={} instance={} db={} table={}",
                UserContextHolder.getUserId(), engineType, instance, database, table);
        return metadataService.listColumns(engineType, instance, database, table);
    }
}
