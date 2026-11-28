package dev.wallpaperhub.account.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

    /** The first chunk of the resync stream: every live user changed at or after {@code since}, oldest first (index users_updated_idx). */
    List<UserEntity> findByDeletedAtIsNullAndUpdatedAtGreaterThanEqualOrderByUpdatedAtAscIdAsc(
            Instant since, Pageable page);

    /** The chunks after it: keyset paging on (updated_at, id), so that no chunk skips or repeats a row with the same timestamp. */
    @Query(
            """
            select u from UserEntity u
            where u.deletedAt is null and (u.updatedAt > :ts or (u.updatedAt = :ts and u.id > :id))
            order by u.updatedAt asc, u.id asc
            """)
    List<UserEntity> findAfter(@Param("ts") Instant ts, @Param("id") UUID id, Pageable page);
}
