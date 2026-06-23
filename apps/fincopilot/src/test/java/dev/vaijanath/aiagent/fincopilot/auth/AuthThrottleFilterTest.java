package dev.vaijanath.aiagent.fincopilot.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthThrottleFilterTest {

    @Test
    void allowsUpToTheLimitThenRejects() throws Exception {
        AuthThrottleFilter filter = new AuthThrottleFilter(2);
        for (int i = 0; i < 2; i++) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request("1.2.3.4"), new MockHttpServletResponse(), chain);
            assertNotNull(chain.getRequest(), "request within the limit passes through");
        }
        MockFilterChain blocked = new MockFilterChain();
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(request("1.2.3.4"), rejected, blocked);
        assertEquals(429, rejected.getStatus());
        assertNull(blocked.getRequest(), "over-limit request does not reach the chain");
    }

    @Test
    void throttlesPerClientAddress() throws Exception {
        AuthThrottleFilter filter = new AuthThrottleFilter(1);
        filter.doFilter(request("10.0.0.1"), new MockHttpServletResponse(), new MockFilterChain());
        MockFilterChain otherIp = new MockFilterChain();
        filter.doFilter(request("10.0.0.2"), new MockHttpServletResponse(), otherIp);
        assertNotNull(otherIp.getRequest(), "a different address is not throttled");
    }

    @Test
    void zeroDisablesThrottling() throws Exception {
        AuthThrottleFilter filter = new AuthThrottleFilter(0);
        for (int i = 0; i < 50; i++) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request("9.9.9.9"), new MockHttpServletResponse(), chain);
            assertNotNull(chain.getRequest());
        }
    }

    private static MockHttpServletRequest request(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }
}
