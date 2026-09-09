package io.gitee.songchaolin.adhoc.server.auth;

import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 从标准头解析用户身份注入 {@link UserContextHolder}：前置网关/SSO/反代负责认证并注入
 * {@code X-Adhoc-User-Id}（必填）与 {@code X-Adhoc-User-Name}（可选，中文名）。
 * <p>生产部署必须保证服务端口不绕过网关直接对外暴露，否则该头可被伪造身份。
 * <p>afterCompletion 清理 ThreadLocal，防线程池复用导致用户上下文泄漏到下一个请求。
 */
public class GatewayUserInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GatewayUserInterceptor.class);

    /** 用户 ID 头（必填） */
    public static final String USER_ID_HEADER = "X-Adhoc-User-Id";

    /** 用户显示名头（可选，中文名） */
    public static final String USER_NAME_HEADER = "X-Adhoc-User-Name";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null || userId.isEmpty()) {
            log.warn("[gateway-user] missing {} header, uri={}", USER_ID_HEADER, request.getRequestURI());
            throw new AdhocException(AdhocErrorCode.ADHOC_USER_CONTEXT_MISSING);
        }
        GatewayUser user = new GatewayUser();
        user.setUserId(userId);
        user.setUserName(request.getHeader(USER_NAME_HEADER));
        UserContextHolder.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContextHolder.clear();
    }
}
