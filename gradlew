#!/bin/sh

APP_HOME=$(cd "$(dirname "$0")" >/dev/null 2>&1 && pwd)
GRADLE_HOME="$HOME/.gradle/wrapper/dists/gradle-9.3.0-bin/79n14ral3mx1ozqr3csh2u872/gradle-9.3.0"

if [ -z "$JAVA_HOME" ] && [ -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"

if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec "$JAVA_CMD" -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
fi

if [ -x "$GRADLE_HOME/bin/gradle" ]; then
  exec "$GRADLE_HOME/bin/gradle" "$@"
fi

echo "Gradle 9.3.0 is not installed. Open this project in Android Studio or install Gradle, then run the same task again." >&2
exit 1
