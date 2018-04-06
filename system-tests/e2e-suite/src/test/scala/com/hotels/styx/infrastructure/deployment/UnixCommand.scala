/*
  Copyright (C) 2013-2018 Expedia Inc.

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
package com.hotels.styx.infrastructure.deployment

import java.io.{BufferedReader, InputStream, InputStreamReader}

import com.hotels.styx.Logging

trait CommandOutputListener {
  def outputReceived(line: String): Unit
}

class LoggingCommandOutputListener extends CommandOutputListener with Logging {
  override def outputReceived(line: String): Unit = {
    logger.info(line)
  }
}

class ProcessOutputWatcher(stream: InputStream, commandOutputListener: CommandOutputListener) extends Thread {
  override def run(): Unit = {
    val reader: BufferedReader = new BufferedReader(new InputStreamReader(stream))
    Iterator.continually(reader.readLine())
      .takeWhile(_ != null)
      .foreach(commandOutputListener.outputReceived(_))

  }
}

object UnixCommand {
  def newUnixCommand(cmdText: String): UnixCommand = {
    new UnixCommand(cmdText.split(" "))
  }
}

class UnixCommand(args: Array[String]) {

  private def startProcess() = {
    val builder = new ProcessBuilder(args: _*)
    builder.redirectErrorStream(true)
    builder.start()
  }

  def execute(commandOutputListener: CommandOutputListener = new LoggingCommandOutputListener): Unit = {
    val process = startProcess()
    val stdoutWatcher = new ProcessOutputWatcher(process.getInputStream(), commandOutputListener)

    stdoutWatcher.setDaemon(true)
    stdoutWatcher.start()

    process.waitFor()
    stdoutWatcher.join()
  }
}
