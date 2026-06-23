package dev.vaijanath.aiagent.fincopilot.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.fincopilot.auth.UserStore;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import dev.vaijanath.aiagent.tool.ToolCallContext;
import dev.vaijanath.aiagent.tool.ToolInvocation;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Savings-goal store + effectful tool against a real Postgres (Testcontainers, skipped without Docker),
 * over the real Flyway schema (V5). Each test uses a distinct user so counts are isolated.
 */
@Testcontainers(disabledWithoutDocker = true)
class GoalsIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    private static UserStore users;
    private static SavingsGoalStore goals;

    @BeforeAll
    static void setup() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        ConnectionSource connections = () ->
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        users = new UserStore(connections);
        goals = new SavingsGoalStore(connections);
    }

    @Test
    void createsAndListsGoalsWithAndWithoutADate() {
        String userId = users.create("goals-a@example.com", "hash").orElseThrow().id();
        goals.create(userId, "Emergency fund", new BigDecimal("5000.00"), LocalDate.parse("2026-12-31"));
        goals.create(userId, "New laptop", new BigDecimal("1500.00"), null);

        List<SavingsGoal> list = goals.listByUser(userId);
        assertEquals(2, list.size());
        SavingsGoal withDate = list.get(0);
        assertEquals("Emergency fund", withDate.name());
        assertEquals(0, new BigDecimal("5000.00").compareTo(withDate.targetAmount()));
        assertEquals(LocalDate.parse("2026-12-31"), withDate.targetDate());
        assertNull(list.get(1).targetDate(), "a goal with no deadline stores a null date");
    }

    @Test
    void toolWritesAGoalForTheInvokingUser() {
        String userId = users.create("goals-b@example.com", "hash").orElseThrow().id();
        SetSavingsGoalTool tool = new SetSavingsGoalTool(goals);
        String args = "{\"name\":\"Vacation\",\"targetAmount\":3000}";

        ToolResult result = tool.invoke(new ToolInvocation(
                new ToolCall("c1", "set_savings_goal", args),
                new ToolCallContext(tool.spec(), args, userId, userId, "t", "s", null, null)));

        assertFalse(result.error(), result.content());
        assertTrue(goals.listByUser(userId).stream().anyMatch(g -> g.name().equals("Vacation")));
    }
}
