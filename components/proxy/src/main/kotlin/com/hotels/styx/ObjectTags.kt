/*
  Copyright (C) 2013-2021 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx

/*
 * TAG: lbGroup
 */
val lbGroupTag = SafeValueTag(
        "lbGroup",
        { it },
        { it })

/*
 * TAG: source
 */
val sourceTag = SafeValueTag(
        "source",
        { it },
        { it })

/*
 * TAG: target
 */
val targetTag = SafeValueTag(
        "target",
        { it },
        { it })


/*
 * TAG: state
 */
const val STATE_ACTIVE = "active"
const val STATE_UNREACHABLE = "unreachable"
const val STATE_INACTIVE = "inactive"

val stateTag = SafeValueTag(
        "state",
        { it },
        { it })

/*
 * TAG: healthCheck
 * healthCheck=on
 * healthCheck=on;probes-OK:2
 * healthCheck=on;probes-FAIL:1
 */
const val HEALTHCHECK_PASSING = "probes-OK"
const val HEALTHCHECK_FAILING = "probes-FAIL"
const val HEALTHCHECK_ON = "on"
private val HEALTHCHECK_REGEX = "$HEALTHCHECK_ON(?:;(.+):([0-9]+))?".toRegex()


val healthCheckTag = NullableValueTag(
        "healthCheck",
        { value -> if (value.first.isNotBlank() && value.second > 0) {
                "$HEALTHCHECK_ON;${value.first}:${value.second}"
            } else if (value.first.isNotBlank() && value.second == 0) {
                HEALTHCHECK_ON
            } else {
                null
            }
        },
        { tagValue -> HEALTHCHECK_REGEX.matchEntire(tagValue)
                ?.groupValues
                ?.let {
                    if (it[1].isNotEmpty()) {
                        Pair(it[1], it[2].toInt())
                    } else {
                        Pair(HEALTHCHECK_ON, 0)
                    }}
        })
