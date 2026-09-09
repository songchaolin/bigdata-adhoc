package io.gitee.songchaolin.adhoc.server.web;

import io.gitee.songchaolin.adhoc.common.dto.SqlConsoleResult;
import io.gitee.songchaolin.adhoc.common.dto.request.SqlConsoleRequest;
import io.gitee.songchaolin.adhoc.server.aspect.AdhocResponseAdvice;
import io.gitee.songchaolin.adhoc.server.service.SqlConsoleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * SQL 控制台端点：对平台主数据源（默认 adhoc 库）执行只读查询。
 * <p>挂 {@code /api/metrics} 前缀 -> 复用 nginx 白名单（{@code /gw/adhoc/api/metrics/**}）
 * 与 {@code GatewayUserInterceptor} 排除（{@code /api/metrics/**}），运维大盘内嵌前端免登录态调用。
 * 仅接受只读 SELECT/SHOW/DESCRIBE/EXPLAIN，详见 {@link SqlConsoleService}。
 */
@Api(tags = "Adhoc Metrics")
@RestController
@RequestMapping("/api/metrics")
public class SqlConsoleController {

    private final SqlConsoleService sqlConsoleService;

    public SqlConsoleController(SqlConsoleService sqlConsoleService) {
        this.sqlConsoleService = sqlConsoleService;
    }

    @ApiOperation("只读 SQL 查询控制台（本地元数据库，SELECT-only，自动 LIMIT 1000，30s 超时）")
    @PostMapping("/sql")
    public SqlConsoleResult query(@Valid @RequestBody SqlConsoleRequest req) {
        return sqlConsoleService.query(req.getSql());
    }
}
