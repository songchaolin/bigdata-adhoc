package io.gitee.songchaolin.adhoc.executor.endpoint;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.endpoint.EngineEndpoint;
import io.gitee.songchaolin.adhoc.common.enums.EngineAuthMode;
import io.gitee.songchaolin.adhoc.common.enums.EngineType;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.executor.config.AdhocExecutorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 引擎实例配置解析器：按 {@code (engineType, instance)} 动态读每实例的 endpoints/user/password/database/params，
 * 全部来自 yml/Apollo 的每实例键，无引擎级回退、无代码默认凭据，并向 {@link EndpointManager} 懒注册 endpoint 列表。
 *
 * <p>每实例配置键（实例名作 key 中段，{@code adhoc.engine.{ENGINE}.{instance}.*}）：
 * <ul>
 *   <li>{@code endpoints}   host:port 列表（逗号分隔，HA 多 host 共用同一组账号）--必填</li>
 *   <li>{@code user/password}   该实例 JDBC 凭据--每实例独立，不再回退引擎级</li>
 *   <li>{@code database/params}（仅 STARROCKS）   该实例默认库/连接参数--可选；database 配了则追加默认库，未配不追加（库由 SQL 的 USE 切换）</li>
 * </ul>
 *
 * <p>动态刷新：每次 resolve 经 {@link ConfigHolder#getString} 读 Environment 活值，Apollo auto-update 后下次 resolve 即新值，
 * 新增实例无需重启（首次 resolve 懒注册）。
 *
 * <p>鉴权模式（引擎级 {@code adhoc.engine.{ENGINE}.auth-mode}，默认 FIXED）：
 * <ul>
 *   <li>{@code FIXED}：实例统一账号直连（Kyuubi 另以 job userId 作 proxyUser 代理）</li>
 *   <li>{@code USER}：用户工号直连（用户名=job userId；Kyuubi 无密码无代理，StarRocks 密码仍用实例统一密码）</li>
 * </ul>
 *
 * <p>实例不存在（endpoints 未配）-> 抛 {@link AdhocErrorCode#ADHOC_ENGINE_INSTANCE_NOT_FOUND}，不静默回退默认。
 */
@Component
public class EngineInstanceConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(EngineInstanceConfigResolver.class);

    /** StarRocks JDBC scheme（MySQL 协议，固定常量，非配置项）。 */
    private static final String STARROCKS_JDBC_SCHEME = "jdbc:mysql://";

    private final ConfigHolder cfg;
    private final EndpointManager endpointManager;

    public EngineInstanceConfigResolver(ConfigHolder cfg, EndpointManager endpointManager) {
        this.cfg = cfg;
        this.endpointManager = endpointManager;
    }

    /**
     * 解析引擎实例为完整连接规格。
     *
     * @param engineType        KYUUBI/STARROCKS
     * @param requestedInstance 实例名，空则用默认实例
     * @param userId            job userId（FIXED 模式 Kyuubi 作 proxyUser；USER 模式作直连用户名）
     * @return 连接规格（instance + url + user + password + proxyUser）
     */
    public ResolvedInstance resolve(String engineType, String requestedInstance, String userId) {
        boolean isStarRocks = EngineType.STARROCKS.is(engineType);
        String engine = isStarRocks ? "STARROCKS" : "KYUUBI";
        String defaultInstance = isStarRocks
                ? cfg.get(AdhocExecutorConfig.STARROCKS_DEFAULT_INSTANCE)
                : cfg.get(AdhocExecutorConfig.KYUUBI_DEFAULT_INSTANCE);
        String instance = (requestedInstance == null || requestedInstance.isEmpty())
                ? defaultInstance : requestedInstance;

        // endpoints（每实例必填，未配抛实例不存在）
        String endpoints = readPerInstance(engine, instance, "endpoints");
        if (endpoints == null || endpoints.trim().isEmpty()) {
            throw new AdhocException(AdhocErrorCode.ADHOC_ENGINE_INSTANCE_NOT_FOUND,
                    engineType + " 实例未配置 endpoints: " + instance);
        }
        // 懒注册 + Apollo 动态变更检测
        endpointManager.ensureRegistered(instance, endpoints);
        EngineEndpoint endpoint = endpointManager.selectEndpoint(instance);

        // 鉴权模式（引擎级开关，活读，Apollo 改后下次 resolve 即生效）
        boolean userMode = EngineAuthMode.USER.is(isStarRocks
                ? cfg.get(AdhocExecutorConfig.STARROCKS_AUTH_MODE)
                : cfg.get(AdhocExecutorConfig.KYUUBI_AUTH_MODE));

        // user/password（每实例独立，无引擎级回退）
        String instanceUser = readPerInstance(engine, instance, "user");
        String instancePassword = readPerInstance(engine, instance, "password");

        // 鉴权分支：FIXED=实例统一账号（现状）；USER=用户工号直连（Kyuubi 无密码无代理，SR 密码仍用实例统一密码）
        String user;
        String password;
        if (userMode) {
            if (userId == null || userId.isEmpty()) {
                throw new AdhocException(AdhocErrorCode.ADHOC_USER_CONTEXT_MISSING,
                        engine + " USER 鉴权模式下缺少用户身份，无法直连: instance=" + instance);
            }
            user = userId;
            password = isStarRocks ? instancePassword : null;
        } else {
            user = instanceUser;
            password = instancePassword;
        }

        String url;
        if (isStarRocks) {
            // jdbc:mysql://host:port[/db][?params]，database/params 每实例可选：
            // database 配了则作默认库追加；未配不追加默认库（库由 SQL 的 USE 切换）
            String db = readPerInstance(engine, instance, "database");
            String params = readPerInstance(engine, instance, "params");
            StringBuilder urlBuilder = new StringBuilder(STARROCKS_JDBC_SCHEME)
                    .append(endpoint.getHost()).append(":").append(endpoint.getPort());
            if (db != null && !db.isEmpty()) {
                urlBuilder.append("/").append(db);
            }
            if (params != null && !params.isEmpty()) {
                urlBuilder.append("?").append(params);
            }
            url = urlBuilder.toString();
        } else {
            url = endpoint.toJdbcUrl();  // jdbc:hive2://host:port
        }

        // 代理用户：仅 FIXED 模式 Kyuubi 用（job userId 作 proxy）；USER 模式直连不代理
        String proxyUser = (!isStarRocks && !userMode) ? resolveUserId(userId) : null;
        log.info("【解析实例】{} instance={} selected={} authMode={} user={} url={}",
                engine, instance, endpoint, userMode ? "USER" : "FIXED", user, url);
        return new ResolvedInstance(instance, url, user, password, proxyUser);
    }

    /** 读每实例配置项（adhoc.engine.{engine}.{instance}.{field}），无回退，未配返回 null。 */
    private String readPerInstance(String engine, String instance, String field) {
        return cfg.getString("adhoc.engine." + engine + "." + instance + "." + field, null);
    }

    /** job userId 空值兜底 "Unknown"。 */
    private static String resolveUserId(String userId) {
        return (userId == null || userId.isEmpty()) ? "Unknown" : userId;
    }

    /** 解析后的引擎实例连接规格。 */
    public static final class ResolvedInstance {
        public final String instance;
        public final String url;
        public final String user;
        public final String password;
        /** 代理用户：Kyuubi=job userId；StarRocks=null */
        public final String proxyUser;

        public ResolvedInstance(String instance, String url, String user, String password, String proxyUser) {
            this.instance = instance;
            this.url = url;
            this.user = user;
            this.password = password;
            this.proxyUser = proxyUser;
        }
    }
}
