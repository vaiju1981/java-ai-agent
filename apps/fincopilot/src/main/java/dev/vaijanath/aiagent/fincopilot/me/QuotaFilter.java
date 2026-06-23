package dev.vaijanath.aiagent.fincopilot.me;

import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Enforces a per-user daily request quota on the chat endpoints. Runs after the session filter (which
 * sets the principal); a request over the quota gets {@code 429}. A quota {@code <= 0} disables it.
 */
public final class QuotaFilter implements Filter {

    private static final int TOO_MANY_REQUESTS = 429;

    private final UsageMeter usage;
    private final int dailyQuota;

    public QuotaFilter(UsageMeter usage, int dailyQuota) {
        this.usage = usage;
        this.dailyQuota = dailyQuota;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Object principal = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (dailyQuota > 0 && principal instanceof String userId) {
            if (usage.today(userId) >= dailyQuota) {
                ((HttpServletResponse) response).sendError(TOO_MANY_REQUESTS, "daily request quota exceeded");
                return;
            }
            usage.record(userId);
        }
        chain.doFilter(request, response);
    }
}
