#!/usr/bin/env bash
#
# Copyright (C) ${license.git.copyrightYears} Expedia Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


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

export JAVA_OPTS

for var in ${!APP_*}; do
  export $var
done

for var in ${!JVM_*}; do
  export $var
done
