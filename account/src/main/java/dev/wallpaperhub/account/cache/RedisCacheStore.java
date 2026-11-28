package dev.wallpaperhub.account.cache;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** {@link CacheStore} on the shared Redis; the timeouts are in application.yml (200 ms). */
@Component
class RedisCacheStore implements CacheStore {

    private final StringRedisTemplate redis;

    RedisCacheStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }
}
