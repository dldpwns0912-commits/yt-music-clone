#!/bin/sh

# Gradle startup script for POSIX systems
APP_BASE_NAME=`basename "$0"`
APP_HOME="`pwd -P`"

# Resolve links
while [ -h "$APP_HOME/$APP_BASE_NAME" ]; do
    ls=`ls -ld "$APP_HOME/$APP_BASE_NAME"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        APP_HOME=`dirname "$link"`
    else
        APP_HOME=`dirname "$APP_HOME/$link"`
    fi
done

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
fi

exec "$JAVACMD" "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
