package io.gitee.songchaolin.adhoc.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SQL Hash 工具类。
 * <p>
 * 用于结果复用判断。SHA-256，避免 MD5 碰撞风险。
 * 规范化：trim + 连续空白压成单空格 + 小写。
 *
 * <p><b>结果指纹</b>（{@link #resultFingerprintHash}）：结果是否可复用取决于
 * 引擎类型 + 引擎实例 + 当前库(prefix use/SET) + 规整后SQL，任一不同则结果可能不同，
 * 必须区分，否则跨引擎/跨实例/跨库误复用导致结果错乱。
 * 写入({@code TaskCreator})与查询({@code TaskExecutionPipeline}/{@code ResultReuseChecker})
 * 必须用同一方法，保证口径一致。
 */
public final class SqlHashUtil {

    /** 维度分隔符：SOH(0x01) 控制符，非空白，不会被 normalize 的 \s+ 压缩，保证维度边界 */
    private static final String SEP = String.valueOf((char) 1);

    private SqlHashUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 计算单条 SQL 的 SHA-256 哈希（仅 SQL 文本，不含引擎/库上下文）。
     * <p>用于不需要区分上下文的场景；结果复用请用 {@link #resultFingerprintHash}。
     */
    public static String hash(String sql) {
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("SQL cannot be null or empty");
        }
        return digest(normalize(sql));
    }

    /**
     * 计算结果指纹哈希（用于结果复用）。
     * <p>
     * 维度：engineType + engineInstance + prefix(use/SET 切库) + executedSql(规整后)。
     * 任一不同则结果可能不同，必须区分。null/空维度按空串处理。
     *
     * @param engineType     引擎类型（KYUUBI/STARROCKS）
     * @param engineInstance 引擎实例名（如 kyuubi-01/starrocks-02，EngineSelector 解析后），可空
     * @param prefix         前缀 SQL（use db / SET ...），可空
     * @param executedSql    规整后的可执行 SQL（已 enforce LIMIT）
     * @return SHA-256 哈希（小写十六进制，64字符）
     */
    public static String resultFingerprintHash(String engineType, String engineInstance,
                                                String prefix, String executedSql) {
        String joined = safe(engineType) + SEP
                + safe(engineInstance) + SEP
                + safe(prefix) + SEP + safe(executedSql);
        return digest(normalize(joined));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String digest(String normalized) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return bytesToHex(md.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 标准算法，理论上不会抛出此异常
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 规范化：trim + 连续空白压成单空格 + 小写。
     * 不同格式的相同 SQL 产生相同哈希：{@code "SELECT * FROM t"} == {@code "select  *  from  t"}。
     * SEP 为 SOH 控制符（非空白），不会被压缩，维度边界保留。
     */
    private static String normalize(String sql) {
        return sql.trim()
                .replaceAll("\\s+", " ")  // 压缩空白
                .toLowerCase();            // 统一小写
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
