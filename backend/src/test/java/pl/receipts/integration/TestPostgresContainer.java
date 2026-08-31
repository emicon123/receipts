package pl.receipts.integration;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton-container pattern (Testcontainers' own recommended approach for sharing one
 * container across multiple test classes in a single JVM/Surefire fork): started once, eagerly,
 * in a static initializer — deliberately NOT annotated {@code @Container}/{@code @Testcontainers}
 * here, because that combination stops the container in each declaring class's {@code afterAll}
 * even when the field is inherited, which killed the shared container after the first test class
 * finished (every subsequent class then failed with "Connection refused"). Left running for the
 * whole JVM; Testcontainers' Ryuk reaper cleans it up when the test run's JVM exits.
 */
final class TestPostgresContainer {

    static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("receipts_test")
            .withUsername("receipts")
            .withPassword("receipts");

    static {
        INSTANCE.start();
    }

    private TestPostgresContainer() {
    }
}
