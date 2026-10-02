package pl.receipts.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * V3/V4 (docs/adr/ADR-014-image-import-source.md) against real PostgreSQL, run through Flyway
 * itself the way the application runs them: V3 adds the {@code IMAGE_IMPORT} enum value, V4
 * re-creates the {@code receipts_image_path_matches_source} CHECK that uses it. The two must be
 * separate transactions (PostgreSQL refuses to use a new enum value in the transaction that adds
 * it, SQLSTATE 55P04), which holds only while {@code spring.flyway.group} stays at its default.
 *
 * <p>Deliberately NOT built on {@link AbstractIntegrationTest}: that context migrates a fresh
 * database straight to the latest version at start-up, but the interesting case here is the
 * upgrade of a database that is already at V2 and holds CAMERA and MANUAL rows (the deployed
 * state). So this class owns a container pinned to the production image
 * ({@code postgres:17.11-alpine}, infra/compose.yml) and a fresh database per test.
 */
class ImageImportMigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine")
            .withDatabaseName("postgres")
            .withUsername("receipts")
            .withPassword("receipts");

    private static final AtomicInteger DB_SEQUENCE = new AtomicInteger();

    private static JdbcTemplate admin;

    @BeforeAll
    static void startPostgres() {
        POSTGRES.start();
        admin = new JdbcTemplate(dataSource("postgres"));
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Test
    void migrationsApplyOnADatabaseThatAlreadyHasCameraAndManualRows() {
        String database = freshDatabase();
        flyway(database, "2").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource(database));

        // The deployed state before this feature: V1 + V2, one CAMERA and one MANUAL receipt.
        jdbc.update("INSERT INTO receipts (source, image_path) VALUES ('CAMERA', '2026/05/old.jpg')");
        jdbc.update("""
                INSERT INTO receipts (status, source, store_name, total_amount, processed_at)
                VALUES ('PROCESSED', 'MANUAL', 'PGE', 150.00, NOW())
                """);
        jdbc.update("""
                INSERT INTO receipt_line_items (receipt_id, product_name, category, amount)
                SELECT id, 'Rachunek za prad', 'RACHUNKI', 150.00 FROM receipts WHERE source = 'MANUAL'
                """);
        assertThat(sourceLabels(jdbc)).containsExactly("CAMERA", "MANUAL");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO receipts (source, image_path) VALUES ('IMAGE_IMPORT', 'x.png')"))
                .hasMessageContaining("invalid input value for enum");

        var result = flyway(database, null).migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(2);
        // V3 and V4 each committed on their own: both are recorded as applied, in order.
        assertThat(flyway(database, null).info().applied()).extracting(MigrationInfo::getVersion)
                .extracting(Object::toString).containsExactly("1", "2", "3", "4");
        assertThat(flyway(database, null).info().applied()).extracting(MigrationInfo::getState)
                .containsOnly(MigrationState.SUCCESS);

        // The new value is appended; existing rows are untouched.
        assertThat(sourceLabels(jdbc)).containsExactly("CAMERA", "MANUAL", "IMAGE_IMPORT");
        assertThat(jdbc.queryForList("SELECT source::text FROM receipts ORDER BY id", String.class))
                .containsExactly("CAMERA", "MANUAL");
        assertThat(jdbc.queryForObject("SELECT image_path FROM receipts WHERE source = 'CAMERA'", String.class))
                .isEqualTo("2026/05/old.jpg");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM receipts WHERE source = 'MANUAL' AND image_path IS NULL",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM receipt_line_items", Integer.class)).isEqualTo(1);

        // ...and the migrated schema accepts an image import next to them.
        jdbc.update("INSERT INTO receipts (source, image_path) VALUES ('IMAGE_IMPORT', '2026/10/new.png')");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM receipts WHERE source = 'IMAGE_IMPORT'",
                Integer.class)).isEqualTo(1);
    }

    /** The extended receipts_image_path_matches_source CHECK, case by case (V4). */
    @ParameterizedTest(name = "{0} with image_path {1} is accepted")
    @CsvSource({
            "IMAGE_IMPORT, 2026/10/a.png",
            "CAMERA,       2026/10/a.jpg",
            "MANUAL,       "
    })
    void imagePathCheckAcceptsImageBackedSourcesWithAPathAndManualWithout(String source, String imagePath) {
        JdbcTemplate jdbc = migratedDatabase();

        int inserted = jdbc.update(
                "INSERT INTO receipts (source, image_path) VALUES (CAST(? AS receipt_source_enum), ?)",
                source, imagePath);

        assertThat(inserted).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0} with image_path {1} violates the CHECK")
    @CsvSource({
            "IMAGE_IMPORT, ",
            "CAMERA,       ",
            "MANUAL,       2026/10/a.jpg"
    })
    void imagePathCheckRejectsAnImageBackedSourceWithoutAPathAndManualWithOne(String source, String imagePath) {
        JdbcTemplate jdbc = migratedDatabase();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO receipts (source, image_path) VALUES (CAST(? AS receipt_source_enum), ?)",
                source, imagePath))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("receipts_image_path_matches_source");
    }

    /** An UPDATE can't sneak past the constraint either (source is immutable, the path is not). */
    @Test
    void clearingTheImagePathOfAnImageImportRowViolatesTheCheck() {
        JdbcTemplate jdbc = migratedDatabase();
        jdbc.update("INSERT INTO receipts (source, image_path) VALUES ('IMAGE_IMPORT', '2026/10/a.png')");

        assertThatThrownBy(() -> jdbc.update("UPDATE receipts SET image_path = NULL WHERE source = 'IMAGE_IMPORT'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("receipts_image_path_matches_source");
    }

    private static JdbcTemplate migratedDatabase() {
        String database = freshDatabase();
        flyway(database, null).migrate();
        return new JdbcTemplate(dataSource(database));
    }

    private static List<String> sourceLabels(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT unnest(enum_range(NULL::receipt_source_enum))::text", String.class);
    }

    /** {@code target == null} migrates to the latest version; "2" stops at the pre-feature schema. */
    private static Flyway flyway(String database, String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource(database))
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static String freshDatabase() {
        String name = "migration_" + DB_SEQUENCE.incrementAndGet();
        admin.execute("CREATE DATABASE " + name);
        return name;
    }

    private static DriverManagerDataSource dataSource(String database) {
        return new DriverManagerDataSource(
                "jdbc:postgresql://%s:%d/%s".formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), database),
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
