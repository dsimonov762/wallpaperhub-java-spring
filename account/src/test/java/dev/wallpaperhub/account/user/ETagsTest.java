package dev.wallpaperhub.account.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wallpaperhub.platform.problem.AppException;
import org.junit.jupiter.api.Test;

class ETagsTest {

    @Test
    void noHeaderOrStarMeansAnyVersion() {
        assertThat(ETags.expectedVersion(null)).isNull();
        assertThat(ETags.expectedVersion("  ")).isNull();
        assertThat(ETags.expectedVersion("*")).isNull();
    }

    @Test
    void readsQuotedAndWeakTags() {
        assertThat(ETags.expectedVersion("\"3\"")).isEqualTo(3L);
        assertThat(ETags.expectedVersion("W/\"7\"")).isEqualTo(7L);
        assertThat(ETags.expectedVersion("12")).isEqualTo(12L);
    }

    @Test
    void aTagThatIsNotANumberNeverMatches() {
        assertThatThrownBy(() -> ETags.expectedVersion("\"abc\""))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.status()).isEqualTo(409);
                    assertThat(e.code()).isEqualTo("request.version-conflict");
                });
    }
}
