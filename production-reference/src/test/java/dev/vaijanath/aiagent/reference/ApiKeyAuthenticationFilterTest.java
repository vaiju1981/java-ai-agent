package dev.vaijanath.aiagent.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiKeyAuthenticationFilterTest {

    private final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(List.of("s3cret"));

    @Test
    void rejectsMissingKeyWith401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/agent/turn"), response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest(), "the chain must not run for an unauthenticated request");
    }

    @Test
    void rejectsWrongKeyWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/turn");
        request.addHeader(ApiKeyAuthenticationFilter.HEADER, "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void allowsAValidKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/turn");
        request.addHeader(ApiKeyAuthenticationFilter.HEADER, "s3cret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "a valid key must pass through to the chain");
    }

    @Test
    void exemptsHealthProbes() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(
                new MockHttpServletRequest("GET", "/actuator/health/readiness"),
                new MockHttpServletResponse(),
                chain);

        assertNotNull(chain.getRequest(), "health probes must be reachable without a key");
    }

    @Test
    void passesThroughWhenNoKeysConfigured() throws Exception {
        ApiKeyAuthenticationFilter open = new ApiKeyAuthenticationFilter(List.of());
        MockFilterChain chain = new MockFilterChain();

        open.doFilter(new MockHttpServletRequest("POST", "/api/agent/turn"), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "no keys configured means auth is disabled");
    }
}
