package dev.vaijanath.aiagent.fincopilot.auth;

import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Wires consumer auth: the BCrypt password encoder, the JDBC user/session stores, the auth service, and
 * the session filter protecting {@code /api/chat/*}. The auth endpoints themselves stay public.
 */
@Configuration
class AuthConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserStore userStore(DataSource dataSource) {
        return new UserStore(dataSource::getConnection);
    }

    @Bean
    SessionStore sessionStore(DataSource dataSource) {
        return new SessionStore(dataSource::getConnection);
    }

    @Bean
    AuthService authService(UserStore userStore, SessionStore sessionStore, PasswordEncoder passwordEncoder) {
        return new AuthService(userStore, sessionStore, passwordEncoder, Duration.ofDays(7));
    }

    @Bean
    FilterRegistrationBean<SessionAuthenticationFilter> sessionAuthFilter(AuthService authService) {
        FilterRegistrationBean<SessionAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new SessionAuthenticationFilter(authService));
        // Guard the authenticated resources (exact + wildcard); /api/auth/* stays public.
        registration.addUrlPatterns(
                "/api/chat/*",
                "/api/accounts",
                "/api/accounts/*",
                "/api/transactions",
                "/api/transactions/*",
                "/api/analytics/*",
                "/api/goals",
                "/api/goals/*",
                "/api/me",
                "/api/me/*");
        registration.setOrder(10);
        return registration;
    }

    @Bean
    FilterRegistrationBean<AuthThrottleFilter> authThrottleFilter(
            @Value("${fincopilot.auth-attempts-per-minute:20}") int attemptsPerMinute) {
        FilterRegistrationBean<AuthThrottleFilter> registration =
                new FilterRegistrationBean<>(new AuthThrottleFilter(attemptsPerMinute));
        registration.addUrlPatterns("/api/auth/*");
        registration.setOrder(5); // before the session filter; throttles login/signup brute-forcing
        return registration;
    }
}
