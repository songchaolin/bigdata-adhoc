package io.gitee.songchaolin.adhoc.server.auth;

import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/** 标准头解析测试：X-Adhoc-User-Id 必填，X-Adhoc-User-Name 可选。 */
class GatewayUserInterceptorTest {

    private final GatewayUserInterceptor interceptor = new GatewayUserInterceptor();

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void resolvesStandardHeaders() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Adhoc-User-Id", "u001");
        req.addHeader("X-Adhoc-User-Name", "张三");
        assertTrue(interceptor.preHandle(req, null, null));
        assertEquals("u001", UserContextHolder.getUserId());
        assertEquals("张三", UserContextHolder.getUserName());
    }

    @Test
    void userNameOptional() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Adhoc-User-Id", "u001");
        assertTrue(interceptor.preHandle(req, null, null));
        assertEquals("u001", UserContextHolder.getUserId());
        assertNull(UserContextHolder.getUserName());
    }

    @Test
    void missingUserIdRejected() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertThrows(AdhocException.class, () -> interceptor.preHandle(req, null, null));
        assertNull(UserContextHolder.get());
    }
}
