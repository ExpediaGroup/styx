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

#
# This script runs as-is using default Python installation on Mac OSX.
#
# To run this script using python-3.4.x:
#
# 1) Install a distribution from www.python.org/download
#
# 2) Install matplotlib using pip3 command:
#
#         $ pip3 install matplotlib
#

import os
import subprocess
import shlex
import datetime
import re
import time
from os.path import join
from optparse import OptionParser
import sys

from wrk import Wrk
from wrk import WrkConfig
from wrk import WrkResultParser
from wrk import WrkCommandLineConfig
from styx_outstanding import wait_for_outstanding_count
from metrics import download_metrics_formatted
from urlparse import urlparse

from settings import JFC_FILE
from settings import STYX_ADMIN_PORT

def run_command(command):
    cmd_process = subprocess.Popen(shlex.split(command), stdout=subprocess.PIPE)
    # TODO, should read input using communicate() method:
    cmd_process.wait()
    return [line.decode('unicode_escape') for line in cmd_process.stdout.readlines()]


class JavaFlightRecording(object):
    jfr_id_pattern = re.compile("Started recording ([0-9]+).*")

    @classmethod
    def get_recording_id(cls, output):
        for line in output:
            match = cls.jfr_id_pattern.match(line)
            if match:
                return int(match.group(1))
        raise Exception("Unable to start JFR recording. Output: %s" % "".join(output))

    @classmethod
    def start(cls, pid, settings):
        output = run_command("jcmd %d JFR.start settings=%s" % (pid, settings))
        id = JavaFlightRecording.get_recording_id(output)
        return JavaFlightRecording(pid, id)

    def __init__(self, pid, recording_id):
        self.recording_id = recording_id
        self.pid = pid

    def stop(self, filename):
        run_command("jcmd %d JFR.stop filename=%s recording=%d" % (self.pid, filename, self.recording_id))


class WrkResults(object):
    def __init__(self, output, metrics, start_time, end_time):
        self.output = output
        self.metrics = metrics
        self.start_time = start_time
        self.end_time = end_time


def styx_admin_host_and_port(hostname, port):
    return "%s:%d" % (hostname, port)


class LoadTest(object):
    def __init__(self, styx_pid, styx_host, wrk_config, times, output_dir, wrk_class=Wrk, jfr_settings=""):
        self.styx_pid = styx_pid
        self.styx_host = styx_host
        self.wrk_config = wrk_config
        self.times = times
        self.output_dir = join(os.getcwd(), output_dir)
        self.wrk_class = wrk_class
        self.jfr_settings = jfr_settings


    def __sorted_keys(self, result):
        keys = [key for key in result.keys()]
        keys.sort()
        return keys


    def write_run_result(self, run_number, wrk_result):
        wrk_output_file = join(self.output_dir, "wrk-output.txt")
        with open(wrk_output_file, 'a') as ofh:
            ofh.write("Run: %d\n" % run_number)
            ofh.write(wrk_result.output)
            ofh.write("\n\n")

        wrk_metrics_file = join(self.output_dir, "wrk-metrics.txt")
        with open(wrk_metrics_file, 'a') as ofh:
            ofh.write("Run: %d\n" % run_number)
            keys = self.__sorted_keys(wrk_result.metrics)
            for key in keys:
                ofh.write("%s: %s\n" % (key, wrk_result.metrics[key]))
            ofh.write("\n\n")

        metrics = download_metrics_formatted(styx_admin_host_and_port(self.styx_host, STYX_ADMIN_PORT))
        styx_metrics_file = join(self.output_dir, "styx-metrics-run-%d.json" % run_number)
        with open(styx_metrics_file, 'w') as ofh:
            ofh.write(metrics)


    def write_summary(self, wrk_results):
        def formatted_values(values):
            if '.' in values[0]:
                return ["%13s" % ("%10.3f" % float(value)) for value in values]
            else:
                return ["%13s" % value for value in values]

        def summary_text(wrk_results):
            def time_string(timestamp):
                time_format = "%H:%M:%S.%f"
                return timestamp.strftime(time_format)

            summary_lines = []

            summary_lines.append("Load test with %d wrk runs.\n" % len(wrk_results))
            for result in wrk_results:
                summary_lines.append("  Run %d: started: %s, ended: %s" % \
                                     (wrk_results.index(result),
                                      time_string(result.start_time),
                                      time_string(result.end_time)))
            summary_lines.append("")

            keys = self.__sorted_keys(wrk_results[0].metrics)
            for key in keys:
                values = []
                for wrk_result in wrk_results:
                    values.append(wrk_result.metrics[key])
                summary_lines.append("%20s %s" % (key, ' '.join(formatted_values(values))))
            return "\n".join(summary_lines)

        summary_file = join(self.output_dir, "summary.txt")
        with open(summary_file, 'w') as ofh:
            ofh.write(summary_text(wrk_results))
            ofh.write("\n")

        print("")
        print("Load test completed. Summary of results: ")
        print("")
        print(summary_text(wrk_results))


    def run_load_test(self, run_number):
        # if hasattr(self.wrk_config, "latency_file"):
        #     self.wrk_config.latency_file = join(self.output_dir, "wrk-latencies-%03d.txt" % run_number)

        wrk = self.wrk_class(self.wrk_config)
        print("Running: %s" % self.wrk_config.command())

        run_started = datetime.datetime.now()
        output = wrk.run()
        run_stopped = datetime.datetime.now()

        output, metrics = WrkResultParser(output).parse()
        return WrkResults(output, metrics, run_started, run_stopped)


    def run(self):
        create_output_dir(self.output_dir)
        recording = JavaFlightRecording.start(self.styx_pid, self.jfr_settings)

        results = []
        for i in range(self.times):
            wrk_result = self.run_load_test(i)
            results.append(wrk_result)
            self.write_run_result(i, wrk_result)
            time.sleep(2)
            wait_for_outstanding_count("http://%s/admin/metrics" % styx_admin_host_and_port(self.styx_host, STYX_ADMIN_PORT), max_seconds=15)
            print("")

        recording.stop(join(self.output_dir, "load-test.jfr"))
        self.write_summary(results)


def create_output_dir(path):
    if not os.path.exists(path):
        os.makedirs(path)


def write_git_status_file(output_dir):
    git_status = run_command("git status")
    git_log1 = run_command("git log -1")

    output_file = join(output_dir, "git-status")
    with open(output_file, "w") as ofh:
        ofh.write("".join(git_status))
        ofh.write("\n")
        ofh.write("".join(git_log1))
        ofh.write("\n")


def styx_pid():
    def pid_from(line):
        fields = line.split()
        if len(fields) == 2:
            return int(fields[0]), fields[1]
        else:
            return int(fields[0]), ""

    output = run_command("jps")
    for line in output:
        pid, command = pid_from(line)
        if command == "StyxServer":
            return int(pid)
    raise Exception("Styx is not running!")


if __name__ == "__main__":
    usage = "usage: %prog [options] URL"
    parser = OptionParser(usage=usage)
    parser.add_option('-d', '--duration', dest='duration', type='int',    default=30,   help="Load test duration in seconds.")
    parser.add_option('-c', '--connections', dest='connections', type='int',    default=200,   help="Load test connections.")
    parser.add_option('-i', '--times',    dest='times',    type='int',    default=3,    help="Number of times to run the load tester tool.")
    parser.add_option('-o', '--output',   dest='output',   type='string', default='load-test', help="Output directory prefix.")
    parser.add_option('-R', '--rate',     dest='rate',     type='int',    default=5000, help="Request rate for wrk2.")
    parser.add_option('-w', '--wrkargs',  dest='wrkargs',  type='string', default=None, help="Command line options for wrk.")
    (options, args) = parser.parse_args()

    styx_pid = styx_pid()

    output_dir = os.path.join(os.getcwd(), options.output)
    jfr_settings = os.path.join(os.getcwd(), JFC_FILE)
    try:
        url = args[0]
    except IndexError:
        print parser.get_usage()
        print "Use -h option to print information about options."
        sys.exit(0)

    print("Running Styx load test tool with duration=%d and %d invocations." % (options.duration, options.times))
    print("The total load test will run approximately %d seconds. The actual" % ((2+options.duration)*options.times))
    print("runtime will be little longer because there will be a small delay")
    print("due to waiting between two WRK runs.")
    print("")
    print("Styx URL: %s" % url)
    print("Styx PID: %d" % styx_pid)
    print("Saving results to %s" % output_dir)
    print("")

    create_output_dir(output_dir)

    write_git_status_file(output_dir)

    wrk_config = WrkConfig(url, options.duration, connections=options.connections, rate=options.rate)
    if options.wrkargs:
        wrk_config = WrkCommandLineConfig(url, options.wrkargs)
    styx_host = urlparse(url).hostname

    test = LoadTest(styx_pid, styx_host, wrk_config, options.times, output_dir, jfr_settings=jfr_settings)
    test.run()

