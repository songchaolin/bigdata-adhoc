package io.gitee.songchaolin.adhoc.executor.runner;

import io.gitee.songchaolin.adhoc.common.enums.EngineType;
import io.gitee.songchaolin.adhoc.executor.endpoint.EngineInstanceConfigResolver;
import io.gitee.songchaolin.adhoc.executor.endpoint.EngineInstanceConfigResolver.ResolvedInstance;
import io.gitee.songchaolin.adhoc.executor.engine.EngineExecutor;
import io.gitee.songchaolin.adhoc.executor.engine.KyuubiEngineExecutor;
import io.gitee.songchaolin.adhoc.executor.engine.StarRocksEngineExecutor;
import io.gitee.songchaolin.adhoc.sqlparser.engine.SmartFallbackParserEngine;
import io.gitee.songchaolin.adhoc.sqlparser.engine.StarRocksParserEngine;
import io.gitee.songchaolin.adhoc.sqlparser.preprocess.SqlScriptProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 引擎路由：按 engineType 选 SqlScriptProcessor + EngineExecutor，并委托
 * {@link EngineInstanceConfigResolver} 解析每实例的 url/user/password/proxyUser。
 *
 * <p>多实例：job 可在 engine_instance 指定实例（如 kyuubi-02），空则走默认实例
 * （{@code adhoc.engine.{ENGINE}.default_instance}）。每实例独立 endpoints/账号密码，由 resolver 动态读 + 懒注册。
 */
@Component
public class EngineSelector {

    private static final Logger log = LoggerFactory.getLogger(EngineSelector.class);

    private final KyuubiEngineExecutor kyuubi;
    private final StarRocksEngineExecutor starrocks;
    private final SqlScriptProcessor kyuubiProcessor = new SqlScriptProcessor();
    private final EngineInstanceConfigResolver resolver;

    public EngineSelector(KyuubiEngineExecutor kyuubi, StarRocksEngineExecutor starrocks,
                          EngineInstanceConfigResolver resolver) {
        this.kyuubi = kyuubi;
        this.starrocks = starrocks;
        this.resolver = resolver;
    }

    /**
     * 选引擎上下文。
     * <p>Kyuubi：FIXED=实例统一账号 + job userId 作 proxyUser；USER=工号直连无密码无代理。
     * <p>StarRocks：FIXED=实例统一账号；USER=工号 + 实例统一密码。
     *
     * @param engineType     引擎类型（KYUUBI/STARROCKS）
     * @param engineInstance 引擎实例名（如 kyuubi-01/starrocks-02），为空则使用默认实例
     * @param userId         用户ID（FIXED 模式 Kyuubi 代理用户 / USER 模式直连用户名）
     * @return 引擎上下文
     */
    public EngineContext select(String engineType, String engineInstance, String userId) {
        boolean isStarRocks = EngineType.STARROCKS.is(engineType);
        SqlScriptProcessor processor = isStarRocks
                ? new SqlScriptProcessor(new SmartFallbackParserEngine(new StarRocksParserEngine())) : kyuubiProcessor;
        ResolvedInstance ri = resolver.resolve(engineType, engineInstance, userId);
        log.info("【选引擎】engine={} instance={} user={} url={}", engineType, ri.instance, ri.user, ri.url);
        return new EngineContext(
                ri.instance,
                processor,
                isStarRocks ? starrocks : kyuubi,
                ri.url,
                ri.user,
                ri.password,
                ri.proxyUser);
    }

    /** 引擎执行上下文：拆分器 + 执行器 + 连接 url + user */
    public static final class EngineContext {
        public final String engineInstance;
        public final SqlScriptProcessor processor;
        public final EngineExecutor executor;
        public final String url;
        public final String user;
        public final String password;
        /** 代理用户：Kyuubi=job userId；StarRocks=null */
        public final String proxyUser;

        public EngineContext(String engineInstance, SqlScriptProcessor processor, EngineExecutor executor,
                             String url, String user, String password, String proxyUser) {
            this.engineInstance = engineInstance;
            this.processor = processor;
            this.executor = executor;
            this.url = url;
            this.user = user;
            this.password = password;
            this.proxyUser = proxyUser;
        }
    }
}
