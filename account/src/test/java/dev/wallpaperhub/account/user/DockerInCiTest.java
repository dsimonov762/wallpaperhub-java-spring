package dev.wallpaperhub.account.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** UserPersistenceTest skips itself without Docker; on CI that would hide the tests, so there Docker is required. */
class DockerInCiTest {

    @Test
    void dockerIsAvailableOnCi() {
        if (System.getenv("CI") != null) {
            assertThat(PostgresSupport.dockerAvailable())
                    .as("the integration tests need Docker on CI")
                    .isTrue();
        }
    }
}
