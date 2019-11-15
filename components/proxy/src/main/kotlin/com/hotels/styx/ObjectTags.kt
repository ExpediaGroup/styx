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

fun lbGroupTag(name: String) = "lbGroup=$name"
fun lbGroupTagValue(tag: String): String? = "lbGroup=(.+)".toRegex()
        .matchEntire(tag)
        ?.groupValues
        ?.get(1)

fun sourceTag(creator: String) = "source=$creator"

private const val STATE = "state"
const val STATE_ACTIVE = "active"
const val STATE_UNREACHABLE = "unreachable"
const val STATE_CLOSED = "closed"
private val STATE_REGEX = "$STATE=(.+)".toRegex()
fun stateTag(value: String) = "$STATE=$value"
fun stateTag(tags: Set<String>) = tags.firstOrNull { isStateTag(it) }
fun isStateTag(tag: String) = STATE_REGEX.matches(tag)
fun stateTagValue(tags: Set<String>) = STATE_REGEX.matchEntire(stateTag(tags)?:"")
        ?.groupValues
        ?.get(1)

private const val HEALTH = "health"
const val HEALTH_SUCCESS = "success"
const val HEALTH_FAIL = "fail"
fun healthTag(value: String?) = if (value != null) "$HEALTH=$value" else null
fun healthTag(tags: Set<String>) = tags.firstOrNull { it.startsWith("$HEALTH=") }
fun isHealthTag(tag: String) = tag.startsWith("$HEALTH=")
fun healthTagValue(tags: Set<String>) = healthTag(tags)?.substring(HEALTH.length + 1)
