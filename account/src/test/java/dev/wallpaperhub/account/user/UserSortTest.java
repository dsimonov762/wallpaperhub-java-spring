package dev.wallpaperhub.account.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wallpaperhub.platform.problem.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class UserSortTest {

    @Test
    void defaultIsNewestFirstWithIdAsTieBreaker() {
        Sort sort = UserSort.parse(null);
        assertThat(sort.toList()).hasSize(2);
        assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void acceptsTheDocumentedValues() {
        assertThat(UserSort.parse("username,asc").getOrderFor("username").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(UserSort.parse("username,desc").getOrderFor("username").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(UserSort.parse("createdAt,asc").getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void anythingElseIsRefusedWithTheFieldName() {
        assertThatThrownBy(() -> UserSort.parse("email,asc")).isInstanceOfSatisfying(AppException.class, e -> {
            assertThat(e.status()).isEqualTo(400);
            assertThat(e.code()).isEqualTo("pagination.sort-not-allowed");
            assertThat(e.args()).containsEntry("field", "email");
        });
        assertThatThrownBy(() -> UserSort.parse("username")).isInstanceOf(AppException.class);
    }
}
