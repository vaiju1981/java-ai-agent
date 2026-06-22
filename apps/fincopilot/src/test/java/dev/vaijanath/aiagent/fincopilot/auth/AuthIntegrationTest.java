package dev.vaijanath.aiagent.fincopilot.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end consumer-auth test against a real Postgres (Testcontainers, skipped without Docker): the
 * JDBC stores, {@link AuthService}, the {@link SessionAuthenticationFilter}, and the {@link AuthController}
 * HTTP contract, all over the real Flyway schema.
 */
@Testcontainers(disabledWithoutDocker = true)
class AuthIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    private static AuthService auth;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        ConnectionSource connections = () ->
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        auth = new AuthService(
                new UserStore(connections), new SessionStore(connections), new BCryptPasswordEncoder(), Duration.ofDays(1));
    }

    @Test
    void signupLoginResolveLogout() {
        Optional<String> token = auth.signup("Alice@Example.com", "correct horse battery");
        assertTrue(token.isPresent(), "signup returns a session token");
        assertTrue(auth.resolve(token.get()).isPresent(), "the token resolves to a user");

        assertTrue(auth.signup("alice@example.com", "another-pass").isEmpty(), "duplicate email (any case) rejected");
        assertTrue(auth.login("alice@example.com", "correct horse battery").isPresent(), "correct password logs in");
        assertTrue(auth.login("alice@example.com", "wrong").isEmpty(), "wrong password rejected");
        assertTrue(auth.login("nobody@example.com", "whatever").isEmpty(), "unknown email rejected");

        auth.logout(token.get());
        assertTrue(auth.resolve(token.get()).isEmpty(), "logout invalidates the session");
    }

    @Test
    void filterRejectsMissingSessionAndAcceptsValid() throws Exception {
        String token = auth.signup("bob@example.com", "password123").orElseThrow();
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(auth);

        MockHttpServletResponse anonymous = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), anonymous, new MockFilterChain());
        assertEquals(401, anonymous.getStatus(), "no session -> 401");

        MockHttpServletRequest authed = new MockHttpServletRequest();
        authed.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilter(authed, ok, new MockFilterChain());
        assertEquals(200, ok.getStatus(), "valid session -> chain proceeds");
        assertNotNull(authed.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE), "principal is set");
    }

    @Test
    void authEndpointsHttpContract() throws Exception {
        MockMvc mvc = standaloneSetup(new AuthController(auth)).build();
        String carol = "{\"email\":\"carol@example.com\",\"password\":\"password123\"}";

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(carol))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(carol))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carol@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }
}
