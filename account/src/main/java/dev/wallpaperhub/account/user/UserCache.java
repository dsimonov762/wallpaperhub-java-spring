package dev.wallpaperhub.account.user;

import dev.wallpaperhub.account.cache.CacheStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache-aside for profiles: {@code cache:user:{id}} holds the JSON of a {@link UserView} for 5 minutes (plus or minus 20 %, so
 * entries written together do not expire together). contracts/db-schema/redis/keys.yaml and README 6.5 are the contract.
 *
 * <ul>
 *   <li><b>Single flight:</b> when many requests miss the same key, one loads from the database and the others wait for its result.
 *   <li><b>Redis is optional:</b> any failure is logged once and the request is served from the database. After a failure Redis is
 *       left alone for 5 seconds, so that a dead Redis costs one slow command (200 ms) per 5 seconds, not one per request.
 *   <li><b>Invalidation:</b> {@link #evict} after the commit of every change. An eviction that cannot reach Redis is remembered and
 *       retried before the next read once Redis answers again (the entry may still be there if Redis was only unreachable).
 * </ul>
 *
 * Known limit: a reader that loaded the old row just before a commit can store it just after the eviction; it lives until its TTL.
 */
@Component
class UserCache {

    static final Duration TTL = Duration.ofMinutes(5);
    static final Duration RETRY_AFTER_FAILURE = Duration.ofSeconds(5);
    static final int MAX_PENDING_EVICTIONS = 10_000;

    private static final Logger LOG = LoggerFactory.getLogger(UserCache.class);

    private final CacheStore store;
    private final JsonMapper mapper;
    private final LongSupplier nanoTime;
    private final DoubleSupplier random;
    private final ConcurrentHashMap<UUID, CompletableFuture<UserView>> inFlight = new ConcurrentHashMap<>();
    private final Set<UUID> pendingEvictions = ConcurrentHashMap.newKeySet();
    private volatile long skipUntil;
    private volatile boolean skipping;

    @Autowired
    UserCache(CacheStore store, JsonMapper mapper) {
        this(store, mapper, System::nanoTime, () -> ThreadLocalRandom.current().nextDouble());
    }

    UserCache(CacheStore store, JsonMapper mapper, LongSupplier nanoTime, DoubleSupplier random) {
        this.store = store;
        this.mapper = mapper;
        this.nanoTime = nanoTime;
        this.random = random;
    }

    static String key(UUID id) {
        return "cache:user:" + id;
    }

    /** The cached view of the user, or what {@code loader} returns (which is then cached). Exceptions of the loader pass through. */
    UserView get(UUID id, Supplier<UserView> loader) {
        if (available()) {
            retryPendingEvictions();
        }
        if (available()) {
            try {
                Optional<String> json = store.get(key(id));
                if (json.isPresent()) {
                    UserView cached = parse(id, json.get());
                    if (cached != null) {
                        return cached;
                    }
                }
            } catch (RuntimeException e) {
                failed("read", e);
            }
        }
        return singleFlight(id, loader);
    }

    /** Removes the entry. Never throws: the change is committed, a failure here is retried later. */
    void evict(UUID id) {
        if (!available()) {
            remember(id);
            return;
        }
        try {
            store.delete(key(id));
            pendingEvictions.remove(id);
        } catch (RuntimeException e) {
            failed("evict", e);
            remember(id);
        }
    }

    int pendingEvictions() {
        return pendingEvictions.size();
    }

    private UserView singleFlight(UUID id, Supplier<UserView> loader) {
        CompletableFuture<UserView> mine = new CompletableFuture<>();
        CompletableFuture<UserView> other = inFlight.putIfAbsent(id, mine);
        if (other != null) {
            try {
                return other.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException cause) {
                    throw cause;
                }
                throw e;
            }
        }
        try {
            UserView view = loader.get();
            fill(id, view);
            mine.complete(view);
            return view;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(id, mine);
        }
    }

    private void fill(UUID id, UserView view) {
        if (!available()) {
            return;
        }
        try {
            store.set(key(id), mapper.writeValueAsString(view), jitteredTtl());
        } catch (RuntimeException e) {
            failed("write", e);
        }
    }

    Duration jitteredTtl() {
        double factor = 0.8 + 0.4 * random.getAsDouble();
        return Duration.ofMillis(Math.round(TTL.toMillis() * factor));
    }

    /** A value that cannot be read (another version of the service wrote it, or it is damaged) counts as a miss and is removed. */
    private UserView parse(UUID id, String json) {
        try {
            return mapper.readValue(json, UserView.class);
        } catch (RuntimeException e) {
            LOG.warn(
                    "cache entry of user {} is unreadable and is dropped: {}",
                    id,
                    e.getClass().getSimpleName());
            evict(id);
            return null;
        }
    }

    private void retryPendingEvictions() {
        if (pendingEvictions.isEmpty()) {
            return;
        }
        for (UUID id : new ArrayList<>(pendingEvictions)) {
            try {
                store.delete(key(id));
                pendingEvictions.remove(id);
            } catch (RuntimeException e) {
                failed("evict", e);
                return;
            }
        }
    }

    private void remember(UUID id) {
        if (pendingEvictions.size() < MAX_PENDING_EVICTIONS) {
            pendingEvictions.add(id);
        } else {
            LOG.error("too many cache evictions are waiting for Redis; entries expire after their TTL");
        }
    }

    private boolean available() {
        return !skipping || nanoTime.getAsLong() - skipUntil >= 0;
    }

    private void failed(String what, RuntimeException e) {
        skipUntil = nanoTime.getAsLong() + RETRY_AFTER_FAILURE.toNanos();
        skipping = true;
        // one line per outage, not per request: the cache is not used again for a few seconds
        LOG.warn(
                "Redis {} failed, serving from the database for {} s: {}",
                what,
                RETRY_AFTER_FAILURE.toSeconds(),
                e.getClass().getSimpleName());
    }
}
