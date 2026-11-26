package dev.wallpaperhub.account;

import dev.wallpaperhub.platform.PlatformApplication;
import dev.wallpaperhub.platform.Startup;

@PlatformApplication
public class AccountApplication {

    /** Required beside {@link Startup#COMMON_REQUIRED}. WP-S1 adds DB_*, REDIS_*, KAFKA_BOOTSTRAP_SERVERS, ... as the code starts to use them. */
    private static final String[] REQUIRED = {};

    public static void main(String[] args) {
        Startup.run(AccountApplication.class, args, REQUIRED);
    }
}
