#
# Copyright (C) 2013-2018 Expedia Inc.
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

import subprocess
import shlex

from settings import WRK_CMD
from settings import LUA_CONFIG_SCRIPT

def run_command(command):
    cmd_process = subprocess.Popen(shlex.split(command), stdout=subprocess.PIPE)
    # TODO, should read input using communicate() method:
    cmd_process.wait()
    return [line.decode('unicode_escape') for line in cmd_process.stdout.readlines()]


class WrkConfig(object):
    def __init__(self, url, duration, rate=5000, threads=2, connections=200, latency_file=None):
        self.url = url
        self.duration = duration
        self.rate = rate
        self.threads = threads
        self.connections = connections
        self.latency_file = latency_file

    def command(self):
        options = [
            "-d %d" % self.duration,
            "-t %d" % self.threads,
            "-c %d" % self.connections,
            "--latency",
            "--script %s" % LUA_CONFIG_SCRIPT
        ]

        if self.rate != None:
            options.append("-R %d" % self.rate)

        if self.latency_file != None:
            options.append("-o'%s'" % self.latency_file)

        return "%s %s %s" % (WRK_CMD, " ".join(options), self.url)


class WrkCommandLineConfig(object):
    def __init__(self, url, args_string):
        self.url = url
        self.args_string = args_string

    def command(self):
        return "%s %s %s" % (WRK_CMD, self.args_string, self.url)


class Wrk(object):
    def __init__(self, wrk_config):
        self.wrk_config = wrk_config

    def run(self):
        return run_command(self.wrk_config.command())


class WrkResultParser(object):
    def __init__(self, wrk_output):
        self.wrk_output = wrk_output

    def __valid_keyval(self, keyval):
        if len(keyval) == 2:
            if ' ' not in keyval[0]:
                return True
        return False

    def parse(self):
        results = {}
        for line in self.wrk_output:
            keyval = line.split(",")
            keyval = [field.strip() for field in keyval]
            if self.__valid_keyval(keyval) == False:
                continue

            results[keyval[0].strip()] = keyval[1].strip()
        return "".join(self.wrk_output), results
