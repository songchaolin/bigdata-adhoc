package io.gitee.songchaolin.adhoc.server.auth;

/**
 * 请求级用户上下文（ThreadLocal）。由 {@link GatewayUserInterceptor} 在 preHandle 解析并 set，
 * afterCompletion clear（防线程池复用泄漏）。controller 通过本类取当前登录用户，不再用 @RequestHeader。
 */
public final class UserContextHolder {

    private static final ThreadLocal<GatewayUser> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(GatewayUser user) {
        HOLDER.set(user);
    }

    public static GatewayUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 当前用户 ID（入库 user_id 字段用此值）；上下文未设置返回 {@code null}。
     */
    public static String getUserId() {
        GatewayUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    /** 当前用户显示名（中文名，对应 service 层 userName 入参）；上下文未设置返回 {@code null} */
    public static String getUserName() {
        GatewayUser u = HOLDER.get();
        return u == null ? null : u.getUserName();
    }
}
