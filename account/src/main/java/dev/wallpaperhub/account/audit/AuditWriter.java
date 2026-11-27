package dev.wallpaperhub.account.audit;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Appends to {@code audit_log} (who changed what, README 9), in the transaction of the change. {@code before} and {@code after}
 * hold only the changed profile fields, never credentials.
 */
@Component
public class AuditWriter {

    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");

    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    AuditWriter(JdbcClient jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, String entityId, Map<String, Object> before, Map<String, Object> after) {
        String traceId = MDC.get("trace_id");
        jdbc.sql(
                        """
                        insert into audit_log (actor_id, action, entity_type, entity_id, before, after, trace_id)
                        values (:actorId, :action, 'user', :entityId, cast(:before as jsonb), cast(:after as jsonb), :traceId)
                        """)
                .param("actorId", actorId)
                .param("action", action)
                .param("entityId", entityId)
                .param("before", before == null ? null : mapper.writeValueAsString(before))
                .param("after", after == null ? null : mapper.writeValueAsString(after))
                .param("traceId", traceId != null && TRACE_ID.matcher(traceId).matches() ? traceId : null)
                .update();
    }
}
