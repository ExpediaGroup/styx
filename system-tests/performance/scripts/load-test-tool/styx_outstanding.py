#!/usr/bin/python
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


import urllib2
import json
import time

from settings import STYX_ADMIN_HOST_AND_PORT

def download_metrics(url):
    f = urllib2.urlopen(url)
    metrics = f.read()
    f.close()
    return metrics


def poll_outstanding_count(url):
    metrics = json.loads(download_metrics(url))
    return metrics["counters"]["requests.outstanding"]["count"]


def wait_for_outstanding_count(url, max_seconds = 15):
    print("Waiting max. %d seconds for outstanding requests to settle down ..." % max_seconds)

    while max_seconds > 0:
        outstanding = poll_outstanding_count(url)
        if outstanding == 0:
            break

        max_seconds -= 1
        time.sleep(1)

    print("requests.outstanding: %d" % outstanding)


if __name__ == "__main__":
    admin_url = "http://%s/admin/metrics" % STYX_ADMIN_HOST_AND_PORT
    wait_for_outstanding_count(admin_url, 15)
