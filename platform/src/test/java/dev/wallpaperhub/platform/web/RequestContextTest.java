package dev.wallpaperhub.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestContextTest {

    @Test
    void aPlainRequestIdIsKept() {
        assertThat(RequestContext.requestId("abc-123_X.y:z")).isEqualTo("abc-123_X.y:z");
    }

    @Test
    void anythingElseIsReplacedByANewId() {
        for (String bad : new String[] {null, "", "has space", "line\r\nbreak: x", "a".repeat(129), "semi;colon"}) {
            String id = RequestContext.requestId(bad);
            assertThat(id).isNotEqualTo(bad).matches("[A-Za-z0-9._:-]{1,128}");
        }
    }

    @Test
    void theTraceIdOfAValidTraceparentIsKept() {
        String trace = "4bf92f3577b34da6a3ce929d0e0e4736";
        assertThat(RequestContext.traceId("00-" + trace + "-00f067aa0ba902b7-01")).isEqualTo(trace);
    }

    @Test
    void aMissingOrBrokenTraceparentGivesANewTraceId() {
        for (String bad : new String[] {null, "", "garbage", "00-" + "0".repeat(32) + "-00f067aa0ba902b7-01",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-" + "0".repeat(16) + "-01"}) {
            assertThat(RequestContext.traceId(bad)).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
        }
    }

    @Test
    void languageIsTheFirstTag() {
        assertThat(Language.of("de-DE,de;q=0.9,en;q=0.8")).isEqualTo("de");
        assertThat(Language.of("ES")).isEqualTo("es");
        assertThat(Language.of("fr-FR")).isEqualTo("en");
        assertThat(Language.of(null)).isEqualTo("en");
        assertThat(Language.of("  de ;q=1")).isEqualTo("de");
    }
}
