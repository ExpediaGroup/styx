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
import sys

def metrics_url(host_and_port):
    return "http://%s/admin/metrics" % host_and_port

def download_metrics(host_and_port):
    f = urllib2.urlopen(metrics_url(host_and_port))
    metrics = f.read()
    f.close()
    return metrics

def download_metrics_formatted(host_and_port):
    metrics = download_metrics(host_and_port)
    return json.dumps(json.loads(metrics), indent=4, separators=(',', ':'), sort_keys=True)

if __name__ == "__main__":
    host_and_port = sys.argv[1]
    print download_metrics_formatted(host_and_port)

