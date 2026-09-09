package io.gitee.songchaolin.adhoc.server.auth;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 资源归属校验：判断当前访问者是否有权访问 {@code ownerUserId} 的 Job/Task/结果/日志。
 *
 * <p>读类接口（{@code /api/job/detail}、{@code /api/job/status}、{@code /api/job/progress}、
 * {@code /api/task/detail}、{@code /api/task/result}、{@code /api/job/result}、
 * {@code /api/task/log}、{@code /api/job/log}）与取消接口（{@code /api/job/cancel}）
 * 按"本人或管理员"放行：
 * <ol>
 *   <li>{@code accessUserId == null} → 系统级访问（dashboard metric 带 token 通过、或 gRPC/SDK 内部调用），放行。</li>
 *   <li>{@code accessUserId} 在管理员列表（{@link AdhocServerConfig#ADMIN_USER_IDS}）→ 放行。</li>
 *   <li>{@code accessUserId.equals(ownerUserId)} → 本人，放行。</li>
 *   <li>否则抛 {@link AdhocErrorCode#ADHOC_JOB_FORBIDDEN}。</li>
 * </ol>
 *
 * <p>管理员列表读 Apollo 活值（{@link ConfigHolder#get}），Apollo 改工号秒级生效，无需重启。
 * 列表逗号分隔，空值/空白项忽略；空列表=无管理员（仅本人可读）。
 *
 * <p>背景：2026-08-13 曾把这些读接口改为 capability-token 模型（知道 jobId 即可读，支持分享），
 * 2026-08-28 反转为"本人或管理员"，取消接口（cancel）同规则拦截——metric 控制台带 token 访问时走系统级放行（accessUserId=null），
 * 业务接口走登录用户身份做归属校验。
 */
@Component
public class OwnershipChecker {

    private final ConfigHolder configHolder;

    public OwnershipChecker(ConfigHolder configHolder) {
        this.configHolder = configHolder;
    }

    /**
     * 校验当前访问者是否有权访问 ownerUserId 的资源。无权抛 {@link AdhocErrorCode#ADHOC_JOB_FORBIDDEN}。
     *
     * @param accessUserId 访问者工号（业务接口传 {@link UserContextHolder#getUserId()}；
     *                     metric/gRPC 系统级访问传 {@code null} → 跳过校验放行）
     * @param ownerUserId  资源归属人工号（job.getUserId() / task.getUserId()）
     */
    public void requireAccess(String accessUserId, String ownerUserId) {
        if (accessUserId == null || accessUserId.isEmpty()) {
            return;   // 系统级访问（dashboard token / gRPC 内部调用）
        }
        if (isAdmin(accessUserId)) {
            return;   // 管理员
        }
        if (accessUserId.equals(ownerUserId)) {
            return;   // 本人
        }
        throw new AdhocException(AdhocErrorCode.ADHOC_JOB_FORBIDDEN);
    }

    /** 当前 accessUserId 是否为配置的管理员（Apollo 活读，逗号分隔，空列表=无管理员）。 */
    private boolean isAdmin(String accessUserId) {
        String raw = configHolder.get(AdhocServerConfig.ADMIN_USER_IDS);
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        Set<String> ids = new HashSet<>();
        for (String s : Arrays.asList(raw.split(","))) {
            String t = s.trim();
            if (!t.isEmpty()) {
                ids.add(t);
            }
        }
        return ids.contains(accessUserId);
    }
}
