package io.gitee.songchaolin.adhoc.server.auth;

import lombok.Data;

/**
 * 请求携带的登录用户信息（由前置网关/SSO 注入标准头，GatewayUserInterceptor 解析）。
 * <ul>
 *   <li>{@code userId}：用户 ID（入库 user_id 字段用此值）</li>
 *   <li>{@code userName}：用户显示名/中文名（入库 user_name 字段用此值；可选）</li>
 * </ul>
 */
@Data
public class GatewayUser {

    /** 用户 ID */
    private String userId;

    /** 用户显示名（中文名，可选） */
    private String userName;
}
