package dev.wallpaperhub.account.user;

import dev.wallpaperhub.account.audit.AuditWriter;
import dev.wallpaperhub.account.outbox.OutboxWriter;
import dev.wallpaperhub.account.outbox.UserEvents;
import dev.wallpaperhub.platform.problem.AppException;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading, listing, changing and deleting profiles. Every change writes its audit row and, where the contract says so, its outbox
 * event in the same transaction. A deleted user ({@code deleted_at} set) does not exist for any read path.
 */
@Service
class UserService {

    private final UserRepository users;
    private final OutboxWriter outbox;
    private final AuditWriter audit;
    private final Clock clock;

    UserService(UserRepository users, OutboxWriter outbox, AuditWriter audit, Clock clock) {
        this.users = users;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    UserEntity get(UUID id) {
        return find(id);
    }

    @Transactional(readOnly = true)
    Page<UserEntity> list(String q, UserStatus status, int page, int size, Sort sort) {
        List<Specification<UserEntity>> filters = new ArrayList<>();
        filters.add((root, query, cb) -> cb.isNull(root.get("deletedAt")));
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status.name()));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.strip().toLowerCase(Locale.ROOT)) + "%";
            filters.add((root, query, cb) -> {
                Predicate[] any = {
                    cb.like(cb.lower(root.get("username")), pattern, '\\'),
                    cb.like(cb.lower(root.get("displayName")), pattern, '\\'),
                    cb.like(cb.lower(root.get("email")), pattern, '\\')
                };
                return cb.or(any);
            });
        }
        return users.findAll(Specification.allOf(filters), PageRequest.of(page, size, sort));
    }

    @Transactional
    UserEntity updateProfile(UUID id, UpdateProfileRequest request, Long expectedVersion) {
        UserEntity user = find(id);
        if (expectedVersion != null && expectedVersion.longValue() != user.getVersion()) {
            throw ETags.versionConflict();
        }
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        boolean displayNameChanged = false;
        if (request.getDisplayName() != null && !request.getDisplayName().equals(user.getDisplayName())) {
            before.put("displayName", user.getDisplayName());
            after.put("displayName", request.getDisplayName());
            user.changeDisplayName(request.getDisplayName());
            displayNameChanged = true;
        }
        if (request.bioGiven() && !Objects.equals(request.getBio(), user.getBio())) {
            before.put("bio", user.getBio());
            after.put("bio", request.getBio());
            user.changeBio(request.getBio());
        }
        if (request.getLocale() != null && !request.getLocale().equals(user.getLocale())) {
            before.put("locale", user.getLocale());
            after.put("locale", request.getLocale());
            user.changeLocale(request.getLocale());
        }
        if (after.isEmpty()) {
            return user; // nothing changes: same version, no event, no audit row
        }
        Instant now = now();
        user.touch(now);
        flush(user);
        audit.record(id, "PROFILE_UPDATED", id.toString(), before, after);
        if (displayNameChanged) {
            outbox.add(UserEvents.profileUpdated(
                    UUID.randomUUID(), id, user.getUsername(), user.getDisplayName(), null, now));
        }
        return user;
    }

    /** The caller may delete themselves; an administrator may delete anyone. Others are refused before anything is looked up. */
    @Transactional
    void delete(UUID id, UUID actor, boolean actorIsAdmin) {
        if (!actorIsAdmin && !id.equals(actor)) {
            throw AppException.forbidden(null);
        }
        UserEntity user = find(id);
        Instant now = now();
        user.markDeleted(now);
        user.touch(now);
        flush(user);
        audit.record(actor, "USER_DELETED", id.toString(), null, null);
        outbox.add(UserEvents.deleted(UUID.randomUUID(), id, now));
    }

    private UserEntity find(UUID id) {
        return users.findById(id)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> AppException.notFound("user.not-found"));
    }

    private void flush(UserEntity user) {
        try {
            users.saveAndFlush(user);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw ETags.versionConflict();
        }
    }

    /** Milliseconds, like the events (Avro timestamp-millis) and like what a client sees after a round trip. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
