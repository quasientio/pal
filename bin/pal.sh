#!/bin/sh
#
# Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
#
# Use of this software is governed by the Business Source License 1.1
# included in the file LICENSE and at https://mariadb.com/bsl11
#
# Change Date: 2029-10-01
# Change License: Apache 2.0
#


# Determine the command (first non-option argument)
for arg in "$@"; do
  case $arg in
    -*)
      # Skip options
      ;;
    *)
      COMMAND="$arg"
      break
      ;;
  esac
done

# Pick up JMX host/port from *prefixed* environment variables
if [ -n "$PAL_JMX_HOST" ]; then
  JMX_HOST="$PAL_JMX_HOST"
fi
if [ -n "$PAL_JMX_PORT" ]; then
  JMX_PORT="$PAL_JMX_PORT"
fi

# Default JVM options
JAVA_OPTS=${JAVA_OPTS:-""}

# Apply specific options for 'run' command
if [ "$COMMAND" = "run" ]; then
  JAVA_OPTS="$JAVA_OPTS -server -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled -Xmx1g -XX:+IgnoreUnrecognizedVMOptions -Xverify:none"

  CHRONICLE_EXPORTS="--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED \
            --add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
            --add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED \
            --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
            --add-opens=jdk.compiler/com.sun.tools.javac=ALL-UNNAMED \
            --add-opens=java.base/java.lang=ALL-UNNAMED \
            --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
            --add-opens=java.base/java.io=ALL-UNNAMED \
            --add-opens=java.base/java.util=ALL-UNNAMED"

  JAVA_OPTS="$JAVA_OPTS $CHRONICLE_EXPORTS"

  # Include JMX options if JMX_HOST and JMX_PORT are now set
  if [ -n "$JMX_HOST" ] && [ -n "$JMX_PORT" ]; then
    JAVA_OPTS="$JAVA_OPTS \
      -Dcom.sun.management.jmxremote \
      -Djava.rmi.server.hostname=$JMX_HOST \
      -Dcom.sun.management.jmxremote.port=$JMX_PORT \
      -Dcom.sun.management.jmxremote.local.only=true \
      -Dcom.sun.management.jmxremote.authenticate=false \
      -Dcom.sun.management.jmxremote.ssl=false"
  fi
fi

# Include Java agent
if [ -n "$JAVA_AGENT" ]; then
  JAVA_OPTS="$JAVA_OPTS -javaagent:$JAVA_AGENT"
fi

# Include logging configuration
if [ -f "$PAL_PEER_LOGGING_CONFIG" ]; then
  JAVA_OPTS="$JAVA_OPTS -Dpeer.logging=$PAL_PEER_LOGGING_CONFIG"
fi
if [ -f "$PAL_CLI_LOGGING_CONFIG" ]; then
  JAVA_OPTS="$JAVA_OPTS -Dcli.logging=$PAL_CLI_LOGGING_CONFIG"
fi

# Find pal's jar under $PAL_HOME/lib, fallback to lib/
PAL_JAR="${PAL_HOME:-.}/lib/pal-*.jar"

# Execute the Pal application
exec java $JAVA_OPTS -jar $PAL_JAR "$@"
