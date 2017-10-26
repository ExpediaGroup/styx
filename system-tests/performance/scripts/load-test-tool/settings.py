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

import os

JFC_FILE = "scripts/load-test-tool/configuration/load_test.jfc"

WRK_CMD = "../../wrk2/wrk"

LUA_SCRIPTS_DIR = "scripts/load-test-tool/configuration"
LUA_CONFIG_SCRIPT = "scripts/load-test-tool/configuration/wrk_config.lua"

STYX_HOST = "localhost"
STYX_ADMIN_PORT = 9000

STYX_ADMIN_HOST_AND_PORT = "%s:%d" % (STYX_HOST, STYX_ADMIN_PORT)
