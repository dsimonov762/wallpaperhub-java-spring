package dev.wallpaperhub.account.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.wallpaperhub.account.audit.AuditWriter;
import dev.wallpaperhub.account.outbox.OutboxMessage;
import dev.wallpaperhub.account.outbox.OutboxWriter;
import dev.wallpaperhub.platform.problem.AppException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05.678901Z");
    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private UserRepository users;
    private OutboxWriter outbox;
    private AuditWriter audit;
    private UserCache cache;
    private UserService service;
    private UserEntity alice;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        outbox = mock(OutboxWriter.class);
        audit = mock(AuditWriter.class);
        cache = mock(UserCache.class);
        service = new UserService(users, outbox, audit, Clock.fixed(NOW, ZoneOffset.UTC), cache);
        alice = UserEntity.create(ALICE, "alice", "Alice@Example.com", "Alice", "en", Instant.EPOCH);
        when(users.findById(ALICE)).thenReturn(Optional.of(alice));
    }

    private static UpdateProfileRequest request(String displayName, String locale) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName(displayName);
        request.setLocale(locale);
        return request;
    }

    @Test
    void unknownAndDeletedUsersDoNotExist() {
        assertThatThrownBy(() -> service.get(BOB))
                .isInstanceOfSatisfying(
                        AppException.class, e -> assertThat(e.status()).isEqualTo(404));
        alice.markDeleted(Instant.EPOCH);
        assertThatThrownBy(() -> service.get(ALICE)).isInstanceOf(AppException.class);
    }

    @Test
    void viewLoadsThroughTheCacheAndKeepsTheVersionForTheEtag() {
        when(cache.get(eq(ALICE), any())).thenAnswer(call -> call.<Supplier<UserView>>getArgument(1).get());

        UserView view = service.view(ALICE);

        assertThat(view.version()).isZero();
        assertThat(view.user().username()).isEqualTo("alice");
        assertThat(view.user().email()).isEqualTo("alice@example.com");
    }

    @Test
    void viewOfAnUnknownUserIsNotFound() {
        when(cache.get(eq(BOB), any())).thenAnswer(call -> call.<Supplier<UserView>>getArgument(1).get());

        assertThatThrownBy(() -> service.view(BOB))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.status()).isEqualTo(404));
    }

    @Test
    void everyChangeEvictsTheCachedProfile() {
        service.updateProfile(ALICE, request("Alice W.", null), null);
        verify(cache).evict(ALICE);

        service.delete(ALICE, ALICE, false);
        verify(cache, org.mockito.Mockito.times(2)).evict(ALICE);
    }

    @Test
    void aFailedOrEmptyChangeEvictsNothing() {
        service.updateProfile(ALICE, request("Alice", null), null);
        assertThatThrownBy(() -> service.updateProfile(ALICE, request("Other", null), 9L)).isInstanceOf(AppException.class);

        verifyNoInteractions(cache);
    }

    @Test
    void displayNameChangeWritesAuditAndProfileUpdatedEvent() {
        UserEntity result = service.updateProfile(ALICE, request("Alice W.", null), null);

        assertThat(result.getDisplayName()).isEqualTo("Alice W.");
        assertThat(result.getUpdatedAt()).isEqualTo(Instant.parse("2026-01-02T03:04:05.678Z"));
        verify(users).saveAndFlush(alice);
        verify(audit).record(eq(ALICE), eq("PROFILE_UPDATED"), eq(ALICE.toString()), any(), any());
        ArgumentCaptor<OutboxMessage> event = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).add(event.capture());
        assertThat(event.getValue().topic()).isEqualTo("account.user.profile-updated.v1");
        assertThat(event.getValue().payload()).containsEntry("displayName", "Alice W.");
    }

    @Test
    void bioOrLocaleChangeAloneWritesNoEvent() {
        service.updateProfile(ALICE, request(null, "de"), null);

        assertThat(alice.getLocale()).isEqualTo("de");
        verify(audit).record(eq(ALICE), eq("PROFILE_UPDATED"), any(), any(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void clearingTheBioNeedsAnExplicitNull() {
        alice.changeBio("hello");
        UpdateProfileRequest clear = new UpdateProfileRequest();
        clear.setBio(null);

        service.updateProfile(ALICE, clear, null);

        assertThat(alice.getBio()).isNull();
    }

    @Test
    void aChangeThatChangesNothingTouchesNothing() {
        service.updateProfile(ALICE, request("Alice", "en"), null);

        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(audit, outbox);
    }

    @Test
    void aStaleIfMatchIsAConflictAndWritesNothing() {
        assertThatThrownBy(() -> service.updateProfile(ALICE, request("Other", null), alice.getVersion() + 1))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.status()).isEqualTo(409);
                    assertThat(e.code()).isEqualTo("request.version-conflict");
                });
        assertThat(alice.getDisplayName()).isEqualTo("Alice");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void aLostRaceIsAlsoAConflict() {
        when(users.saveAndFlush(alice)).thenThrow(new ObjectOptimisticLockingFailureException(UserEntity.class, ALICE));

        assertThatThrownBy(() -> service.updateProfile(ALICE, request("Other", null), null))
                .isInstanceOfSatisfying(
                        AppException.class, e -> assertThat(e.status()).isEqualTo(409));
        verifyNoInteractions(audit, outbox);
    }

    @Test
    void anotherUserMayNotDeleteYouAndIsRefusedBeforeTheLookup() {
        assertThatThrownBy(() -> service.delete(ALICE, BOB, false))
                .isInstanceOfSatisfying(
                        AppException.class, e -> assertThat(e.status()).isEqualTo(403));
        verifyNoInteractions(users, outbox, audit);
    }

    @Test
    void ownerAndAdminMayDeleteAndItWritesUserDeleted() {
        service.delete(ALICE, ALICE, false);

        assertThat(alice.isDeleted()).isTrue();
        ArgumentCaptor<OutboxMessage> event = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).add(event.capture());
        assertThat(event.getValue().topic()).isEqualTo("account.user.deleted.v1");
        assertThat(event.getValue().payload()).doesNotContainKeys("username", "displayName", "email");

        UserEntity bob = UserEntity.create(BOB, "bob", "bob@example.com", "Bob", "en", Instant.EPOCH);
        when(users.findById(BOB)).thenReturn(Optional.of(bob));
        service.delete(BOB, ALICE, true);
        assertThat(bob.isDeleted()).isTrue();
    }
}
