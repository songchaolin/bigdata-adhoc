package io.gitee.songchaolin.adhoc.metadata.fetcher;

import com.alibaba.druid.pool.DruidDataSource;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.config.ConfigItem;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 元数据 fetcher 基类：每实例 lazy 初始化独立 DruidDataSource（不配 {@code spring.datasource.druid.*}
 * 不触发自动配置），子类只提供连接配置项 + SQL。
 *
 * <p><b>多实例</b>：每实例独立连接池，连接源（url/user/password）全部来自 yml/Apollo 每实例键
 * {@code adhoc.metadata.{kind}.{instance}.{url,user,password}}，无引擎级回退、无代码默认凭据。
 * 实例由方法首参传入，空则取默认实例（{@code adhoc.metadata.{kind}.default_instance}）。
 * 未配 url 抛 {@link AdhocErrorCode#ADHOC_METADATA_NOT_CONFIGURED}（不影响 server 启动，首次查询才 lazy init）。
 *
 * <p>pool-size 为操作性配置（非连接源）：每实例可配 {@code adhoc.metadata.{kind}.{instance}.pool-size} 覆盖，
 * 未配回退引擎级 {@code adhoc.metadata.{kind}.pool-size}（代码默认 4）。
 *
 * <p>线程安全：每实例 double-check 建池；{@link JdbcTemplate} 本身线程安全，多线程并发查询安全。
 * 连接/查询失败由 {@link io.gitee.songchaolin.adhoc.metadata.MetadataService} 包成 {@link AdhocErrorCode#ADHOC_METADATA_QUERY_FAILED}。
 */
public abstract class AbstractJdbcMetadataFetcher implements MetadataFetcher {

    protected final ConfigHolder cfg;
    /** 每实例独立 Druid 池 + JdbcTemplate，key = 实例名，lazy 建。 */
    private final Map<String, DruidDataSource> dsByInstance = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcByInstance = new ConcurrentHashMap<>();

    protected AbstractJdbcMetadataFetcher(ConfigHolder cfg) {
        this.cfg = cfg;
    }

    /** 子类提供引擎级 pool-size 配置项（每实例未配 pool-size 时兜底，代码默认 4）。 */
    protected abstract ConfigItem<Integer> poolSizeItem();

    /** 子类提供默认实例名配置项（adhoc.metadata.{kind}.default_instance）。 */
    protected abstract ConfigItem<String> defaultInstanceItem();

    /** 子类提供配置 key 的 kind 段（hive-metastore / starrocks）。 */
    protected abstract String kind();

    /** lazy 建指定实例的 Druid 池 + JdbcTemplate（首次查询触发；未配 url 抛 NOT_CONFIGURED）。 */
    protected JdbcTemplate jdbc(String instance) {
        String inst = resolveInstance(instance);
        JdbcTemplate j = jdbcByInstance.get(inst);
        if (j == null) {
            synchronized (this) {
                j = jdbcByInstance.get(inst);
                if (j == null) {
                    String url = readPerInstance(inst, "url");
                    if (url == null || url.trim().isEmpty()) {
                        throw new AdhocException(AdhocErrorCode.ADHOC_METADATA_NOT_CONFIGURED,
                                "元数据连接未配置: " + kind() + "/" + inst);
                    }
                    DruidDataSource ds = new DruidDataSource();
                    ds.setUrl(url);
                    ds.setUsername(readPerInstance(inst, "user"));
                    ds.setPassword(readPerInstance(inst, "password"));
                    ds.setDriverClassName("com.mysql.cj.jdbc.Driver");  // hive metastore + SR 均走 MySQL 协议
                    ds.setMaxActive(readInstancePoolSize(inst));
                    ds.setMinIdle(1);
                    ds.setInitialSize(0);  // 不启动即连，避免未配/配错时 server 启动失败；首次查询才 lazy init
                    ds.setMaxWait(5000L);
                    ds.setTimeBetweenConnectErrorMillis(10000L);
                    ds.setValidationQuery("SELECT 1");
                    ds.setTestWhileIdle(true);
                    dsByInstance.put(inst, ds);
                    j = new JdbcTemplate(ds);
                    jdbcByInstance.put(inst, j);
                }
            }
        }
        return j;
    }

    /** 解析实例名：空 -> 默认实例。供缓存键用（避免 default_instance 变更后缓存串集群）+ jdbc 建池 key。 */
    @Override
    public String resolveInstanceName(String instance) {
        return resolveInstance(instance);
    }

    /** 解析实例名：空 -> 默认实例。 */
    private String resolveInstance(String requested) {
        if (requested == null || requested.isEmpty()) {
            return cfg.get(defaultInstanceItem());
        }
        return requested;
    }

    /** 读每实例连接源项（adhoc.metadata.{kind}.{instance}.{field}），无回退，未配返回 null。 */
    private String readPerInstance(String instance, String field) {
        return cfg.getString("adhoc.metadata." + kind() + "." + instance + "." + field, null);
    }

    /** 每实例 pool-size；未配回退引擎级（操作性配置，默认 4）。 */
    private int readInstancePoolSize(String instance) {
        String val = cfg.getString("adhoc.metadata." + kind() + "." + instance + ".pool-size", null);
        if (val != null && !val.trim().isEmpty()) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignore) {
                // 解析失败回退引擎级
            }
        }
        return cfg.get(poolSizeItem());
    }

    /** 关闭所有实例连接池（测试清理 / server 优雅停机）。幂等。Druid Destroyer 线程非 daemon，未关会让测试 JVM 不退出。 */
    public synchronized void close() {
        dsByInstance.values().forEach(ds -> {
            try {
                ds.close();
            } catch (Exception e) {
                // 忽略单个池关闭失败
            }
        });
        dsByInstance.clear();
        jdbcByInstance.clear();
    }
}
