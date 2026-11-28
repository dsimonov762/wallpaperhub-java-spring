package dev.wallpaperhub.account.cache;

import dev.wallpaperhub.platform.management.ReadinessCheck;
import org.springframework.stereotype.Component;

/**
 * Redis has been reachable at least once (SERVICE-SPEC section 2), like the signing keys. After that a Redis that goes away does
 * not take the service out of rotation: the cache is optional and reads keep working without it (chaos scenario redis-down).
 */
@Component
class RedisReadiness implements ReadinessCheck {

    private final CacheStore store;
    private volatile boolean reached;

    RedisReadiness(CacheStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public boolean ready() {
        if (!reached) {
            try {
                store.get("probe:ready"); // an answer, with or without a value, means that Redis is there
                reached = true;
            } catch (RuntimeException e) {
                // not reachable yet
            }
        }
        return reached;
    }
}
