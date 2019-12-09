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
package com.hotels.styx.services

import io.kotlintest.Spec
import io.kotlintest.TestCase
import io.kotlintest.eventually
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class FileMonitoringServiceTest : StringSpec() {
    val LOGGER = LoggerFactory.getLogger(FileMonitoringServiceTest::class.java)

    val tempDir = createTempDir(suffix = "-${this.javaClass.simpleName}")
    val monitoredFile = File("${tempDir.absolutePath}/config.yml")

    override fun beforeSpec(spec: Spec) {
        LOGGER.info("Temp directory: " + tempDir.absolutePath)
    }

    override fun beforeTest(testCase: TestCase) {
        monitoredFile.writeText("Hello, world!")
    }

    override fun afterSpec(spec: Spec) {
        tempDir.deleteRecursively()
    }

    init {
        "Triggers action when starts" {
            val result = AtomicReference("")

            val service = FileMonitoringService("FileMonitoringService", monitoredFile.absolutePath) {
                result.set(it)
            }

            try {
                service.start()
                eventually(2.seconds, AssertionError::class.java) {
                    result.get() shouldBe "Hello, world!"
                }
            } finally {
                service.stop()
            }
        }


        "When starts, fails to read file (file doesn't exist)" {
            val result = AtomicReference("")

            kotlin.runCatching {
                val service = FileMonitoringService("FileMonitoringService", "/abc") {
                    result.set(it)
                }
            }.let {
                it.isFailure shouldBe true
            }
        }

        "Notifies listener when file content changes." {
            val result = AtomicReference("")

            val service = FileMonitoringService("FileMonitoringService", monitoredFile.absolutePath) {
                result.set(it)
            }

            try {
                service.start()
                eventually(2.seconds, AssertionError::class.java) {
                    result.get() shouldBe "Hello, world!"
                }

                monitoredFile.writeText("New Content Populated!", UTF_8)

                eventually(2.seconds, AssertionError::class.java) {
                    result.get() shouldBe "New Content Populated!"
                }
            } finally {
                service.stop()
            }
        }

        // TODO: Investigate this:
        "!Doesn't notify listeners when file is updated but MD5 checksum doesn't change." {
            val result = AtomicReference("")
            val updateCount = AtomicInteger(0)

            val service = FileMonitoringService("FileMonitoringService", monitoredFile.absolutePath) {
                result.set(it)
                updateCount.incrementAndGet()
            }

            try {
                service.start()
                eventually(2.seconds, AssertionError::class.java) {
                    result.get() shouldBe "Hello, world!"
                }

                monitoredFile.writeText("Hello, world!", UTF_8)
                Thread.sleep(2.seconds.toMillis())

                // TODO: Fails due to underlying FileChangeMonitor bug. It emits a change even if md5 sum stays the same.
                result.get() shouldBe "Hello, world!"
                updateCount.get() shouldBe (1)
            } finally {
                service.stop()
            }
        }

    }

}