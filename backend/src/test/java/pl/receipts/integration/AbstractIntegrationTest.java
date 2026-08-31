package pl.receipts.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Shared base for @SpringBootTest integration tests: a real PostgreSQL container (Flyway runs
 * V1__init.sql against it on context start — no ddl-auto, no H2) — CLAUDE.md's quality gate:
 * "Backend integration tests use Testcontainers against real PostgreSQL — do not mock the
 * database." The container itself is a JVM-wide singleton — see {@link TestPostgresContainer}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", TestPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", TestPostgresContainer.INSTANCE::getPassword);
    }
}
