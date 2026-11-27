package dev.wallpaperhub.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JsonLayoutTest {

    @Test
    void oneLineOfJsonWithTheRequiredFields() {
        LoggerContext context = new LoggerContext();
        Logger logger = context.getLogger("test");
        LoggingEvent event = new LoggingEvent("x", logger, Level.WARN, "hello \"world\"\nsecond line", null, null);
        event.setMDCPropertyMap(Map.of("trace_id", "4bf92f3577b34da6a3ce929d0e0e4736", "request_id", "r1"));
        JsonLayout layout = new JsonLayout();
        layout.setContext(context);
        layout.start();

        String line = layout.doLayout(event);

        assertThat(line).endsWith("}\n");
        assertThat(line.strip()).doesNotContain("\n");
        Map<?, ?> json = JsonMapper.builder().build().readValue(line, Map.class);
        assertThat(json.get("level")).isEqualTo("warn");
        assertThat(json.get("message")).isEqualTo("hello \"world\"\nsecond line");
        assertThat(json.get("trace_id")).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(json.get("timestamp").toString()).matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z");
        assertThat(json.containsKey("service")).as("service field").isTrue();
    }
}
