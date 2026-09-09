package io.gitee.songchaolin.adhoc.server.config;

import io.gitee.songchaolin.adhoc.server.auth.AccessLogInterceptor;
import io.gitee.songchaolin.adhoc.server.auth.DashboardTokenInterceptor;
import io.gitee.songchaolin.adhoc.server.auth.GatewayUserInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册拦截器链 + dashboard 入口转发。
 * <p>拦截器顺序（先到先执行）：
 * <ol>
 *   <li>{@link DashboardTokenInterceptor}：dashboard/metrics 访问令牌闸（拦 {@code /dashboard/**} + {@code /api/metrics/**}）。
 *       最外层访问闸，先验 token 再谈用户上下文。配置 token 为空时放行（本地开发/未启用无感）。</li>
 *   <li>{@link GatewayUserInterceptor}：解析用户身份注入 {@code UserContextHolder}（拦 {@code /api/**}，排除 {@code /api/metrics/**}）。</li>
 *   <li>{@link AccessLogInterceptor}：访问日志（拦 {@code /api/**}）。</li>
 * </ol>
 * <p>swagger 的 {@code doc.html} / {@code /webjars/**} 不在 /api 路径下，不受影响。
 * 与 {@link Knife4jConfiguration} 职责分离（后者仅注册 swagger 静态资源）。
 * <p>指标大盘（{@code /api/metrics/**}，对应 {@code /dashboard/} 页面）是运维视图，按地址+端口直达，
 * 不走网关、不要求用户上下文，故从用户拦截器排除（仍受 token 拦截器与 {@link AccessLogInterceptor} 记录访问日志）。
 */
@Configuration
public class AdhocWebMvcConfig implements WebMvcConfigurer {

    private final DashboardTokenInterceptor dashboardTokenInterceptor;

    public AdhocWebMvcConfig(DashboardTokenInterceptor dashboardTokenInterceptor) {
        this.dashboardTokenInterceptor = dashboardTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. dashboard/metrics 访问令牌闸（最外层，先验 token）
        //    排除纯静态子资源：浏览器加载 <script src>/<link href>/lib 时无法带自定义头且 URL 无 token，
        //    这些库（echarts/flatpickr/app.js/style.css）本身不含敏感数据，豁免；只保护 index.html 入口 + API。
        registry.addInterceptor(dashboardTokenInterceptor)
                .addPathPatterns("/dashboard/**", "/api/metrics/**")
                .excludePathPatterns("/dashboard/lib/**", "/dashboard/*.js", "/dashboard/*.css");
        // 2. 用户身份解析（/api/**，排除 metrics —— metrics 不要求用户上下文）
        registry.addInterceptor(new GatewayUserInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/metrics/**");
        // 3. 访问日志
        registry.addInterceptor(new AccessLogInterceptor())
                .addPathPatterns("/api/**");
    }

    /** 大盘入口：Spring Boot 的 welcome-page 仅对根 / 生效，子目录 /dashboard/ 不会自动找 index.html，
     *  故显式转发到静态资源 /dashboard/index.html（ResourceHttpRequestHandler 已能 serve，已验证 200）。
     *  同时注册带/不带尾斜杠两条，避免 useTrailingSlashMatch 差异。 */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/dashboard").setViewName("forward:/dashboard/index.html");
        registry.addViewController("/dashboard/").setViewName("forward:/dashboard/index.html");
    }
}
