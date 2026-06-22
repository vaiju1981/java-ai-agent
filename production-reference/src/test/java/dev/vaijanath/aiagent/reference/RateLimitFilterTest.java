package dev.vaijanath.aiagent.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    @Test
    void rejectsOversizedBodyWith413() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(0, 10); // throttling off, 10-byte cap
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/turn");
        request.setContent(new byte[20]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertNull(chain.getRequest(), "an oversized body must be rejected before the chain");
    }

    @Test
    void throttlesOnceTheBucketEmpties() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 1_000_000); // 2 permits/minute

        assertEquals(200, attempt(filter).getStatus());
        assertEquals(200, attempt(filter).getStatus());
        assertEquals(429, attempt(filter).getStatus(), "the third call within the window must be throttled");
    }

    @Test
    void allowsRequestsUnderTheLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(10, 1_000_000);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/agent/turn"), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    private static MockHttpServletResponse attempt(RateLimitFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/turn");
        request.addHeader("X-Principal-Id", "user-1"); // same bucket key across attempts
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
