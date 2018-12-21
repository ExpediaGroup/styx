#!/usr/bin/env bash

# Styx JVM startup parameters
#
# JVM sizing options
JVM_DIRECT_MEMORY="${JVM_DIRECT_MEMORY:=-XX:MaxDirectMemorySize=512m}"
JVM_HEAP_OPTS="${JVM_HEAP_OPTS:=-XX:+AlwaysPreTouch}"

JVM_GC_LOG="${JVM_GC_LOG:=-XX:+PrintGCDetails -Xloggc:/styx/logs/gc.log.$(/bin/date +%Y-%m-%d-%H%M%S)}"
JVM_HEAP_DUMP="${JVM_HEAP_DUMP:=-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/tmp}"

# Set java VM type
JVM_FLAVOUR="-server"

JVM_OTHER_OPTS="${JVM_OTHER_OPTS:=-XX:+DisableExplicitGC -Djava.awt.headless=true -XX:+OptimizeStringConcat -XX:+UseBiasedLocking}"

# Increase Netty String builder maximum size to 70KB.
# See: https://github.com/netty/netty/issues/7092
JAVA_OPTS="$JAVA_OPTS -Dio.netty.threadLocalMap.stringBuilder.maxSize=71680"

# Set Netty leak detection level
# JAVA_OPTS="$JAVA_OPTS -Dio.netty.leakDetectionLevel=advanced"

# Enable remote debugging
# JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000"

# Enable extended Java DTrace probes
# JAVA_OPTS="$JAVA_OPTS -XX:+ExtendedDTraceProbes"

export JAVA_OPTS

for var in ${!APP_*}; do
  export $var
done

for var in ${!JVM_*}; do
  export $var
done
