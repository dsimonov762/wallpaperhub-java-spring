package dev.wallpaperhub.account.user;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** One Redis for the tests of this module, started on first use and removed by Testcontainers when the JVM ends. */
final class RedisSupport {

    // the version of the template's docker-compose.yml
    private static final String IMAGE = "redis:8.10.3-trixie";

    private static GenericContainer<?> redis;

    private RedisSupport() {}

    static synchronized void register(DynamicPropertyRegistry registry) {
        if (redis == null) {
            redis = new GenericContainer<>(DockerImageName.parse(IMAGE)).withExposedPorts(6379);
            redis.start();
        }
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
