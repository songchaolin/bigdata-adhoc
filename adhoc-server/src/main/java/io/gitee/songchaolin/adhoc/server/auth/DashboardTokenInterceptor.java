package io.gitee.songchaolin.adhoc.server.auth;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Dashboard / Metrics 访问令牌拦截器：给指标大盘（{@code /dashboard/**}，含静态资源）与
 * 运维接口（{@code /api/metrics/**}）加一道固定 token 闸，防免登录态下全平台数据裸奔。
 *
 * <p>开关：读 {@link AdhocServerConfig#DASHBOARD_ACCESS_TOKEN}（Apollo 活读，改值秒级生效）。
 * <ul>
 *   <li>配置为空 → 鉴权关闭，直接放行（本地开发 / 未启用环境无感）。</li>
 *   <li>配置非空 → 请求须带 {@code X-Dashboard-Token} 头且与配置值相等，否则 401。</li>
 * </ul>
 *
 * <p>token 比对用 {@link MessageDigest#isEqual}（常量时间比较，防时序侧信道）。
 * token 来源两路（任一命中即可）：
 * <ul>
 *   <li>请求头 {@code X-Dashboard-Token}：前端 fetch 统一带（{@code /api/metrics/**} 主路径）。</li>
 *   <li>URL 参数 {@code token}：浏览器加载 {@code index.html} 等无法带自定义头的入口请求时，
 *       首次访问 {@code /dashboard/?token=xxx} 由前端写 localStorage 后切到 header 方式。</li>
 * </ul>
 * 校验失败不抛 {@code AdhocException}（避免业务异常日志噪音），直接写 401 + 简洁 JSON
 * （{@code code:-1}，与前端 {@code res.code !== 1} 失败判定一致）。
 *
 * <p>鉴权闸在 {@link GatewayUserInterceptor} 之前（token 是更外层访问闸，先验 token 再谈用户上下文）。
 * 两者路径基本不重叠：{@code /dashboard/**} 不被用户拦截器覆盖；{@code /api/metrics/**}
 * 已被用户拦截器 exclude。
 * <p>注意：浏览器加载 {@code <script src>}/{@code <link href>} 等静态子资源时无法带自定义 header、
 * 且 URL 不带 token，故注册时排除纯静态资源后缀（{@code *.js}/{@code *.css}/{@code /dashboard/lib/**}），
 * 只保护页面入口 {@code index.html} 与接口 {@code /api/metrics/**}。静态库本身不含敏感数据。
 */
@Component
public class DashboardTokenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DashboardTokenInterceptor.class);

    /** 前端 fetch 统一带的访问令牌头。 */
    public static final String TOKEN_HEADER = "X-Dashboard-Token";

    /** URL 参数名：浏览器加载 index.html 入口时无法带自定义头，首次访问 /dashboard/?token=xxx 走此参数。 */
    public static final String TOKEN_PARAM = "token";

    private static final String REJECT_BODY = "{\"code\":-1,\"msg\":\"dashboard token 无效或缺失\"}";

    private final ConfigHolder configHolder;

    public DashboardTokenInterceptor(ConfigHolder configHolder) {
        this.configHolder = configHolder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String expected = configHolder.get(AdhocServerConfig.DASHBOARD_ACCESS_TOKEN);
        // 未配置 token = 不启用鉴权
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        String got = request.getHeader(TOKEN_HEADER);
        // 回退 URL 参数 token（浏览器加载 index.html 入口时无法带自定义头，首次访问带 ?token=xxx）
        if (got == null || got.isEmpty()) {
            got = request.getParameter(TOKEN_PARAM);
        }
        boolean ok = got != null && MessageDigest.isEqual(
                got.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            log.warn("[dashboard-token] rejected uri={} hasToken={}", request.getRequestURI(), got != null);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(REJECT_BODY);
            return false;
        }
        return true;
    }
}
