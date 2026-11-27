package dev.wallpaperhub.account.outbox;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inserts a row into {@code outbox_event} (README 4.3). It must run inside the transaction of the domain change, so that both
 * are committed or neither is ({@link Propagation#MANDATORY} refuses to run without one). Plain SQL on purpose: the JSON
 * columns need no JSON mapping of the ORM. Publishing the rows to Kafka is the relay (WP-S1, outbox increment).
 */
@Component
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    OutboxWriter(JdbcClient jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void add(OutboxMessage message) {
        jdbc.sql(
                        """
                        insert into outbox_event (event_id, aggregate_type, aggregate_id, event_type, topic, payload, headers)
                        values (:eventId, 'user', :aggregateId, :eventType, :topic, cast(:payload as jsonb), cast(:headers as jsonb))
                        """)
                .param("eventId", message.eventId())
                .param("aggregateId", message.aggregateId())
                .param("eventType", message.eventType())
                .param("topic", message.topic())
                .param("payload", mapper.writeValueAsString(message.payload()))
                .param("headers", mapper.writeValueAsString(headers(message)))
                .update();
    }

    /** README 5.6: the headers of an event are {@code traceparent}, {@code correlation-id} and {@code event-id}. */
    static Map<String, String> headers(OutboxMessage message) {
        Map<String, String> headers = new LinkedHashMap<>();
        String traceId = MDC.get("trace_id");
        String spanId = MDC.get("span_id");
        if (traceId != null && spanId != null) {
            headers.put("traceparent", "00-" + traceId + "-" + spanId + "-01");
        }
        String requestId = MDC.get("request_id");
        if (requestId != null) {
            headers.put("correlation-id", requestId);
        }
        headers.put("event-id", message.eventId().toString());
        return headers;
    }
}
