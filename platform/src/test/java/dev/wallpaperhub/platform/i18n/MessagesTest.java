package dev.wallpaperhub.platform.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

class MessagesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** contracts/i18n/format-tests.json: the cases every implementation of the placeholder syntax must pass. */
    static Stream<Map<String, Object>> formatCases() throws Exception {
        try (InputStream in = MessagesTest.class.getResourceAsStream("/i18n-contract/format-tests.json")) {
            List<?> cases = JSON.readValue(in, List.class);
            return cases.stream().map(c -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) c;
                return m;
            });
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("formatCases")
    void formatFollowsTheContract(Map<String, Object> testCase) {
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) testCase.get("args");
        assertThat(Messages.format((String) testCase.get("template"), args)).isEqualTo(testCase.get("expected"));
    }

    @Test
    void bundlesLoadAndFallBackToEnglish() {
        Messages messages = Messages.load(JSON);
        assertThat(messages.has("problem.not-found.title")).isTrue();
        assertThat(messages.text("en", "problem.not-found.title", Map.of())).isEqualTo("Not found");
        assertThat(messages.text("de", "problem.not-found.title", Map.of())).isNotEqualTo("Not found").isNotBlank();
        assertThat(messages.text("fr", "problem.not-found.title", Map.of())).isEqualTo("Not found");
        assertThat(messages.text("en", "no.such.key", Map.of())).isEqualTo("no.such.key");
    }

    @Test
    void everyLanguageHasEveryKey() {
        Messages messages = Messages.load(JSON);
        for (String lang : Messages.LANGUAGES) {
            assertThat(messages.text(lang, "field.username", Map.of())).as(lang).isNotEqualTo("field.username");
        }
    }
}
