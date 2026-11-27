#!/bin/sh
# SERVICE-SPEC 12: stdout and stderr carry nothing but JSON log lines.
# A JVM that finds JAVA_TOOL_OPTIONS in its environment announces it on stderr ("Picked up JAVA_TOOL_OPTIONS: ..."), which is not JSON.
# So the options are read here, removed from the environment and passed as arguments. exec: the JVM is PID 1 and receives SIGTERM (S1).
opts="${JAVA_TOOL_OPTIONS:-}"
unset JAVA_TOOL_OPTIONS
# The OpenTelemetry Java agent reads the OTEL_* variables of the contract (endpoint, protocol, service name, intervals) and exports traces, logs and
# metrics (SERVICE-SPEC section 8). OTEL_SDK_DISABLED=true starts the service without it. The agent's own log lines go through the application's
# logger (JSON on stdout), and the JDK's warning for sun.misc.Unsafe (used by libraries inside the agent) would be a non-JSON line on stderr.
agent=/otel/opentelemetry-javaagent.jar
agent_opts=""
if [ "${OTEL_SDK_DISABLED:-false}" != true ] && [ -f "$agent" ]; then
  agent_opts="-javaagent:$agent -Dotel.javaagent.logging=application --sun-misc-unsafe-memory-access=allow"
fi
# shellcheck disable=SC2086   # $opts and $agent_opts are lists of options on purpose
exec java $opts $agent_opts -XX:+ExitOnOutOfMemoryError -jar /app/app.jar "$@"
