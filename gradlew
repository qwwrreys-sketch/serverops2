#!/bin/sh
# Gradle wrapper launcher.
# If gradle/wrapper/gradle-wrapper.jar is missing (it is a binary file), it is
# downloaded once from the official Gradle repository, then Gradle is started.
set -e

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.8.0/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "gradle-wrapper.jar not found - downloading it once..." >&2
  mkdir -p "$APP_HOME/gradle/wrapper"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR"
  else
    echo "ERROR: need curl or wget to fetch $WRAPPER_URL" >&2
    exit 1
  fi
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

exec "$JAVACMD" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew \
  -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
