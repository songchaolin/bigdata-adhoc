package io.gitee.songchaolin.adhoc.server.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 访问日志拦截器：记录"谁调了什么接口"，便于观察接口被谁访问。
 * <p>注册在 {@link GatewayUserInterceptor} 之后（此时 {@link UserContextHolder} 已注入用户身份），
 * 取 userId / userName，结合 HTTP method、URI、controller 方法名打印一条访问日志。
 * <p>使用独立 logger（{@code io.gitee.songchaolin.adhoc.server.auth.AccessLogInterceptor}），
 * 便于运维按 logger 名单独调整级别（如生产环境降为 DEBUG 减少日志量）。
 */
public class AccessLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AccessLogInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        GatewayUser user = UserContextHolder.get();
        String userId = user != null && user.getUserId() != null ? user.getUserId() : "-";
        String userName = user != null && user.getUserName() != null ? user.getUserName() : "-";
        log.info("[access] userId={} userName={} -> {} {} | {}",
                userId, userName,
                request.getMethod(), request.getRequestURI(), resolveHandler(handler));
        return true;
    }

    /** 解析 controller 方法定位，格式 类名#方法名（如 JobController#submit） */
    private String resolveHandler(Object handler) {
        if (handler instanceof HandlerMethod) {
            HandlerMethod hm = (HandlerMethod) handler;
            return hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName();
        }
        return handler == null ? "-" : handler.getClass().getSimpleName();
    }
}
