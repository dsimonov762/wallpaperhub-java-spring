package dev.wallpaperhub.account;

import dev.wallpaperhub.platform.PlatformApplication;
import dev.wallpaperhub.platform.Startup;

@PlatformApplication
public class AccountApplication {

    /** Required beside {@link Startup#COMMON_REQUIRED}. WP-S1 adds REDIS_*, KAFKA_BOOTSTRAP_SERVERS, ... as the code starts to use them. */
    private static final String[] REQUIRED = {"DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD"};

    public static void main(String[] args) {
        Startup.run(AccountApplication.class, args, REQUIRED);
    }
}
