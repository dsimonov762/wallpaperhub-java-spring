package dev.wallpaperhub.account.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wallpaperhub.account.cache.CacheStore;
import dev.wallpaperhub.platform.problem.AppException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class UserCacheTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UserView VIEW = new UserView(
            3,
            new UserResponse(
                    ALICE,
                    "alice",
                    "alice@example.com",
                    "Alice",
                    null,
                    "en",
                    null,
                    null,
                    null,
                    null,
                    "ACTIVE",
                    Instant.parse("2026-01-01T00:00:00.123456Z"),
                    Instant.parse("2026-01-02T00:00:00Z")));

    /** A Redis in a map, that can be switched off. Records the TTL of the last write. */
    private static final class FakeStore implements CacheStore {
        final Map<String, String> data = new HashMap<>();
        volatile boolean down;
        volatile Duration lastTtl;
        final AtomicInteger calls = new AtomicInteger();

        private void check() {
            calls.incrementAndGet();
            if (down) {
                throw new IllegalStateException("Redis is down");
            }
        }

        @Override
        public synchronized Optional<String> get(String key) {
            check();
            return Optional.ofNullable(data.get(key));
        }

        @Override
        public synchronized void set(String key, String value, Duration ttl) {
            check();
            data.put(key, value);
            lastTtl = ttl;
        }

        @Override
        public synchronized void delete(String key) {
            check();
            data.remove(key);
        }
    }

    private FakeStore store;
    private AtomicLong clock;
    private UserCache cache;
    private AtomicInteger loads;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        clock = new AtomicLong(1_000_000_000_000L);
        cache = new UserCache(store, JsonMapper.builder().build(), clock::get, () -> 0.5);
        loads = new AtomicInteger();
    }

    private UserView load() {
        loads.incrementAndGet();
        return VIEW;
    }

    @Test
    void aMissLoadsAndFillsAndTheNextReadIsAHitThatKeepsEveryField() {
        assertThat(cache.get(ALICE, this::load)).isEqualTo(VIEW);
        assertThat(store.data).containsKey("cache:user:" + ALICE);

        assertThat(cache.get(ALICE, this::load)).isEqualTo(VIEW);
        assertThat(loads).hasValue(1);
    }

    @Test
    void theTtlIsFiveMinutesPlusOrMinusTwentyPercent() {
        UserCache low = new UserCache(store, JsonMapper.builder().build(), clock::get, () -> 0.0);
        UserCache high = new UserCache(store, JsonMapper.builder().build(), clock::get, () -> 1.0);

        assertThat(low.jitteredTtl()).isEqualTo(Duration.ofMinutes(4));
        assertThat(high.jitteredTtl()).isEqualTo(Duration.ofMinutes(6));
        assertThat(cache.jitteredTtl()).isEqualTo(Duration.ofMinutes(5));
        cache.get(ALICE, this::load);
        assertThat(store.lastTtl).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void anUnknownUserIsNotCachedAndTheExceptionPassesThrough() {
        assertThatThrownBy(() -> cache.get(ALICE, () -> {
                    throw AppException.notFound("user.not-found");
                }))
                .isInstanceOf(AppException.class);

        assertThat(store.data).isEmpty();
    }

    @Test
    void evictRemovesTheEntry() {
        cache.get(ALICE, this::load);

        cache.evict(ALICE);

        assertThat(store.data).isEmpty();
    }

    @Test
    void anUnreadableEntryIsDroppedAndReloaded() {
        store.data.put("cache:user:" + ALICE, "{not json");

        assertThat(cache.get(ALICE, this::load)).isEqualTo(VIEW);

        assertThat(loads).hasValue(1);
        assertThat(store.data.get("cache:user:" + ALICE)).contains("alice");
    }

    @Test
    void whenRedisIsDownTheDatabaseAnswersAndRedisIsLeftAloneForFiveSeconds() {
        store.down = true;

        assertThat(cache.get(ALICE, this::load)).isEqualTo(VIEW);
        int callsAfterFirstFailure = store.calls.get();
        assertThat(cache.get(ALICE, this::load)).isEqualTo(VIEW);
        assertThat(cache.get(ALICE, this::load)).isEqualTo(VIEW);

        assertThat(loads).hasValue(3);
        assertThat(store.calls).hasValue(callsAfterFirstFailure); // not asked again inside the window

        clock.addAndGet(Duration.ofSeconds(6).toNanos());
        store.down = false;
        cache.get(ALICE, this::load);
        assertThat(store.data).containsKey("cache:user:" + ALICE);
    }

    @Test
    void anEvictionThatCannotReachRedisIsRetriedBeforeTheNextRead() {
        cache.get(ALICE, this::load);
        store.down = true;

        cache.evict(ALICE);
        assertThat(cache.pendingEvictions()).isEqualTo(1);
        assertThat(store.data).containsKey("cache:user:" + ALICE); // unreachable, so still there

        clock.addAndGet(Duration.ofSeconds(6).toNanos());
        store.down = false;
        UserView fresh = new UserView(4, VIEW.user());
        assertThat(cache.get(ALICE, () -> fresh)).isEqualTo(fresh); // the stale entry was removed first

        assertThat(cache.pendingEvictions()).isZero();
    }

    @Test
    void manyReadersOfTheSameMissLoadOnce() throws Exception {
        int readers = 8;
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(readers);
        try {
            Future<UserView> first = pool.submit(() -> cache.get(ALICE, () -> {
                loads.incrementAndGet();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return VIEW;
            }));
            while (loads.get() == 0) {
                Thread.sleep(5); // the first reader is inside its load
            }
            List<Future<UserView>> others = new ArrayList<>();
            for (int i = 1; i < readers; i++) {
                others.add(pool.submit(() -> cache.get(ALICE, this::load)));
            }
            Thread.sleep(100); // let them arrive and wait
            release.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(VIEW);
            for (Future<UserView> other : others) {
                assertThat(other.get(5, TimeUnit.SECONDS)).isEqualTo(VIEW);
            }
            assertThat(loads).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
