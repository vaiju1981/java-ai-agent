package dev.vaijanath.aiagent.fincopilot.analyst;

import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only analytics for the authenticated user — the data behind the dashboard charts. The same
 * aggregations the Analyst tools use, exposed directly for visualisation. Scoped to the session principal.
 */
@RestController
@RequestMapping("/api/analytics")
class AnalyticsController {

    private final Analytics analytics;

    AnalyticsController(Analytics analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    Analytics.Summary summary(@RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        return analytics.summary(principal);
    }

    @GetMapping("/spending-by-category")
    List<Analytics.CategorySpend> spendingByCategory(
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return analytics.spendingByCategory(principal, date(from, "from"), date(to, "to"));
    }

    @GetMapping("/monthly-cashflow")
    List<Analytics.MonthFlow> monthlyCashflow(
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        return analytics.monthlyCashflow(principal);
    }

    private static LocalDate date(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must be an ISO date (yyyy-MM-dd)");
        }
    }
}
