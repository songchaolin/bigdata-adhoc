package io.gitee.songchaolin.adhoc.common.enums;

/**
 * 引擎连接鉴权模式（引擎级开关 adhoc.engine.{ENGINE}.auth-mode）：
 * <ul>
 *   <li>{@link #FIXED}：统一账号直连（每实例配置 user/password；Kyuubi 另以 job userId 作 proxyUser 代理）</li>
 *   <li>{@link #USER}：用户工号直连（用户名=访问用户工号；Kyuubi 无密码无代理，StarRocks 密码仍取实例统一密码）</li>
 * </ul>
 */
public enum EngineAuthMode {
    FIXED,
    USER;

    /** 大小写不敏感匹配配置值（Apollo 上配 user/fixed 均可识别）。 */
    public boolean is(String value) {
        return name().equalsIgnoreCase(value);
    }
}