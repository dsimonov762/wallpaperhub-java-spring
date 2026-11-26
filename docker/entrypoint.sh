#!/bin/sh
# SERVICE-SPEC 12: stdout and stderr carry nothing but JSON log lines.
# A JVM that finds JAVA_TOOL_OPTIONS in its environment announces it on stderr ("Picked up JAVA_TOOL_OPTIONS: ..."), which is not JSON.
# So the options are read here, removed from the environment and passed as arguments. exec: the JVM is PID 1 and receives SIGTERM (S1).
opts="${JAVA_TOOL_OPTIONS:-}"
unset JAVA_TOOL_OPTIONS
# shellcheck disable=SC2086   # $opts is a list of options on purpose
exec java $opts -XX:+ExitOnOutOfMemoryError -jar /app/app.jar "$@"
