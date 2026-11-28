package dev.wallpaperhub.account.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * The few Redis operations the service needs, as strings. Every method may throw a {@link RuntimeException} when Redis does not
 * answer in time: callers treat the cache as an optimisation and carry on without it.
 */
public interface CacheStore {

    Optional<String> get(String key);

    void set(String key, String value, Duration ttl);

    void delete(String key);
}
