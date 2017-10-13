#!/bin/bash
#
# Copyright (C) 2013-2017 Expedia Inc.
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


if [ $# -lt 1 ]; then
	echo $0 java-class-name [options]
	exit 1
fi

base_dir=$(dirname $0)/..


echo "Base dir is $base_dir"

for file in $base_dir/target/*.jar;
do
  echo $file
  CLASSPATH=$CLASSPATH:$file
done

for file in $base_dir/target/styx/lib/*.jar;
do
  CLASSPATH=$CLASSPATH:$file
done

CLASSPATH=$CLASSPATH:$base_dir/dist/resources

#DEBUG_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000 "

echo "RUNCLASS CLASSPATH: $CLASSPATH"

export CLASSPATH
java -cp $CLASSPATH $@
