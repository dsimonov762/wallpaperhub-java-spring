package dev.wallpaperhub.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class StartupTest {

    @Test
    void absentAndBlankVariablesAreMissing() {
        Map<String, String> env = Map.of("A", "x", "B", "", "C", "   ");
        assertThat(Startup.missing(env::get, List.of("A", "B", "C", "D"))).containsExactly("B", "C", "D");
    }

    @Test
    void nothingIsMissingWhenEverythingIsSet() {
        assertThat(Startup.missing(name -> "v", Startup.COMMON_REQUIRED)).isEmpty();
    }

    @Test
    void theErrorLineIsJsonAndNamesTheVariable() {
        String line = Startup.errorLine("account-service", "missing or empty required environment variable(s): OIDC_ISSUER_URI");
        assertThat(line).startsWith("{").endsWith("}").contains("\"level\":\"error\"").contains("OIDC_ISSUER_URI");
    }
}
