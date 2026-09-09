package io.gitee.songchaolin.adhoc.executor.runner;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.enums.SqlType;
import io.gitee.songchaolin.adhoc.executor.config.AdhocExecutorConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DQL/CTAS LIMIT 规整（adhoc.executor.result-limit，默认 100w）。
 * 无 LIMIT -> 追加；有 LIMIT 且 > 阈值 -> 改为阈值；有 LIMIT 且 <= 阈值 -> 不改。
 * 兼容三种语法：LIMIT N / LIMIT N OFFSET M / LIMIT M, N（MySQL comma 语法）。
 * 非 DQL/CTAS（DDL/DML/SET/USE 等）不加 LIMIT。
 *
 * <p>作为 Spring bean 注入：构造时读取 EXECUTOR_RESULT_LIMIT；同时保留 int 构造供单测传入任意阈值。
 * <p>结果复用以 enforce 后的 SQL 计算 hash（见 {@link TaskCreator}），与 {@link TaskExecutionPipeline}
 * 复用查询端一致，避免被追加 LIMIT 的 SQL 写入/查询 hash 错位导致永不命中。
 */
@Component
public class SqlLimitEnforcer {

    // LIMIT N（行尾）
    private static final Pattern LIMIT_PLAIN = Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)\\s*$");
    // LIMIT N OFFSET M
    private static final Pattern LIMIT_OFFSET = Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)\\s+OFFSET\\s+(\\d+)\\s*$");
    // LIMIT M, N（MySQL comma: offset, count）
    private static final Pattern LIMIT_COMMA = Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)\\s*,\\s*(\\d+)\\s*$");

    private final int resultLimit;

    public SqlLimitEnforcer(int resultLimit) {
        this.resultLimit = resultLimit;
    }

    @Autowired
    public SqlLimitEnforcer(ConfigHolder cfg) {
        this(cfg.get(AdhocExecutorConfig.EXECUTOR_RESULT_LIMIT));
    }

    public String enforce(String sql, String sqlType) {
        if (!SqlType.DQL.is(sqlType) && !SqlType.CTAS.is(sqlType)) {
            return sql;
        }
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        // 1. LIMIT M, N（MySQL comma 语法：offset, count）-- 先匹配，避免被 LIMIT N 误匹配
        Matcher mc = LIMIT_COMMA.matcher(trimmed);
        if (mc.find()) {
            long offset = Long.parseLong(mc.group(1));
            long count = Long.parseLong(mc.group(2));
            if (count > resultLimit) {
                return trimmed.substring(0, mc.start()) + " LIMIT " + offset + ", " + resultLimit;
            }
            return sql;
        }

        // 2. LIMIT N OFFSET M
        Matcher mo = LIMIT_OFFSET.matcher(trimmed);
        if (mo.find()) {
            long count = Long.parseLong(mo.group(1));
            long offset = Long.parseLong(mo.group(2));
            if (count > resultLimit) {
                return trimmed.substring(0, mo.start()) + " LIMIT " + resultLimit + " OFFSET " + offset;
            }
            return sql;
        }

        // 3. LIMIT N（纯 LIMIT）
        Matcher m = LIMIT_PLAIN.matcher(trimmed);
        if (m.find()) {
            long existing = Long.parseLong(m.group(1));
            if (existing > resultLimit) {
                return trimmed.substring(0, m.start()) + " LIMIT " + resultLimit;
            }
            return sql;
        }

        // 4. 无 LIMIT -> 追加
        return trimmed + " LIMIT " + resultLimit;
    }
}
