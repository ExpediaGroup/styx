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

import ch.qos.logback.classic.Level.ERROR
import ch.qos.logback.classic.Level.INFO
import com.hotels.styx.config.schema.SchemaValidationException
import com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent
import com.hotels.styx.support.matchers.LoggingTestSupport
import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.specs.StringSpec
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import kotlin.test.assertFailsWith

class StyxConfigValidationTest : StringSpec() {
    var log : LoggingTestSupport? = null

    override fun beforeTest(testCase: TestCase) {
        log = LoggingTestSupport("com.hotels.styx")
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        log?.stop()
    }

    init {
        "Does no validation when not using yaml" {
            val config = StyxConfig()

            validate(config)

            assertThat(log!!.lastMessage(), `is`(loggingEvent(INFO, "Configuration could not be validated as it does not use YAML")))
        }

        "Valid config passes validation" {
            val valid = "" +
                    "proxy:\n" +
                    "  connectors:\n" +
                    "    http:\n" +
                    "      port: 8080\n" +
                    "admin:\n" +
                    "  connectors:\n" +
                    "    http:\n" +
                    "      port: 8081\n" +
                    "services:\n" +
                    "  factories:\n" +
                    "    serv1:\n" +
                    "      foo: bar\n"

            val config = StyxConfig(valid)

            validate(config)

            assertThat(log!!.lastMessage(), `is`(loggingEvent(INFO, "Configuration validated successfully.")))
        }

        "Invalid config fails validation" {
            val valid = "" +
                    "proxy:\n" +
                    "  connectors:\n" +
                    "    http:\n" +
                    "      sport: 8080\n" +
                    "admin:\n" +
                    "  connectors:\n" +
                    "    http:\n" +
                    "      port: 8081\n" +
                    "services:\n" +
                    "  factories:\n" +
                    "    serv1:\n" +
                    "      foo: bar\n"

            val config = StyxConfig(valid)

            assertFailsWith<SchemaValidationException>{
                validate(config)
            }

            assertThat(log!!.lastMessage(), `is`(loggingEvent(ERROR, "Missing a mandatory field 'proxy.connectors.http.port'")))
        }
    }
}
