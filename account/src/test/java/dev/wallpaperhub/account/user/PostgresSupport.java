package dev.wallpaperhub.account.user;

import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * One PostgreSQL for the tests of this module, started on first use and removed by Testcontainers when the JVM ends. The schema
 * is the contract's own file, loaded by the image's entrypoint: the tests never define tables (AGENTS.md rule 5).
 */
final class PostgresSupport {

    // the version of the template's docker-compose.yml
    private static final String IMAGE = "postgres:18.7-trixie";
    // surefire runs in the module directory; contracts/ is in the repository root
    private static final Path SCHEMA =
            Path.of("..", "contracts", "db-schema", "postgres", "account", "V1__init.sql").toAbsolutePath().normalize();

    private static PostgreSQLContainer postgres;

    private PostgresSupport() {}

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    static synchronized void register(DynamicPropertyRegistry registry) {
        if (postgres == null) {
            postgres = new PostgreSQLContainer(IMAGE)
                    .withCopyFileToContainer(
                            MountableFile.forHostPath(SCHEMA), "/docker-entrypoint-initdb.d/V1__init.sql");
            postgres.start();
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
