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
package com.hotels.styx.support

import io.kotlintest.Spec
import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.TestStatus
import io.kotlintest.extensions.TestListener
import org.slf4j.LoggerFactory

object TestErrorReporter: TestListener {
    val LOGGER = LoggerFactory.getLogger("StyxFT")

    override fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        LOGGER.info("Starting ${spec.description().fullName()}")
    }

    override fun afterSpec(spec: Spec) {
        LOGGER.info("Finished ${spec.description().fullName()}")
        super.afterSpec(spec)
    }

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)

        LOGGER.info("Running '${testCase.name}' - ${testCase.source.fileName}:${testCase.source.lineNumber}")
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        super.afterTest(testCase, result)

        LOGGER.info("${testCase.name} - ${result.status}")
        when (result.status) {
            TestStatus.Success -> { }
            TestStatus.Ignored -> { }
            TestStatus.Error -> {
                result.error?.let {
                    LOGGER.info(it.message)
                    it.printStackTrace()
                }
            }
            TestStatus.Failure -> {
                result.error?.let {
                    LOGGER.info(it.message)
                    it.printStackTrace()
                }
            }
        }
    }
}