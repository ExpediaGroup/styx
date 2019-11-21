/*
  Copyright (C) 2013-2019 Expedia Inc.

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

private const val LBGROUP = "lbGroup"
private val LBGROUP_REGEX = "$LBGROUP=(.+)".toRegex()
fun lbGroupTag(name: String) = "lbGroup=$name"
fun lbGroupTag(tags: Set<String>) = tags.firstOrNull(::isLbGroupTag)
fun isLbGroupTag(tag: String) = LBGROUP_REGEX.matches(tag)
fun lbGroupTagValue(tags: Set<String>) = lbGroupTagValue(lbGroupTag(tags)?:"")
fun lbGroupTagValue(tag: String): String? = LBGROUP_REGEX.matchEntire(tag)
        ?.groupValues
        ?.get(1)

fun sourceTag(creator: String) = "source=$creator"
fun sourceTag(tags: Set<String>) = tags.firstOrNull { it.startsWith("source=") }
fun sourceTagValue(tags: Set<String>) = sourceTag(tags)?.substring("source".length + 1)

private const val STATE = "state"
const val STATE_ACTIVE = "active"
const val STATE_UNREACHABLE = "unreachable"
const val STATE_CLOSED = "closed"
private val STATE_REGEX = "$STATE=(.+)".toRegex()
fun stateTag(value: String) = "$STATE=$value"
fun stateTag(tags: Set<String>) = tags.firstOrNull(::isStateTag)
fun isStateTag(tag: String) = STATE_REGEX.matches(tag)
fun stateTagValue(tags: Set<String>) = stateTagValue(stateTag(tags)?:"")
fun stateTagValue(tag: String) = STATE_REGEX.matchEntire(tag)
        ?.groupValues
        ?.get(1)

private const val HEALTHCHECK = "healthcheck"
const val HEALTHCHECK_PASSING = "passing"
const val HEALTHCHECK_FAILING = "failing"
private val HEALTHCHECK_REGEX = "$HEALTHCHECK=(.+):([0-9]+)".toRegex()
fun healthcheckTag(value: Pair<String, Int>?) =
        if (value != null && value.first.isNotBlank() && value.second > 0)
            "$HEALTHCHECK=${value.first}:${value.second}"
        else null
fun healthcheckTag(tags: Set<String>) = tags.firstOrNull(::isHealthcheckTag)
fun isHealthcheckTag(tag: String) = HEALTHCHECK_REGEX.matches(tag)
fun healthcheckTagValue(tags: Set<String>) = healthcheckTagValue(healthcheckTag(tags)?:"")
fun healthcheckTagValue(tag: String) = HEALTHCHECK_REGEX.matchEntire(tag)
        ?.groupValues
        ?.let { Pair(it[1], it[2].toInt()) }
