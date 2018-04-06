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
package com.hotels.styx.support.generators

import org.scalacheck.Gen

class UrlGenerator(hostAndPort: Option[String] = None) {

  def genAbsolutePath: Gen[String] = for {
    depth <- Gen.choose(0, 25)
    pathElems <- Gen.listOfN(depth, Gen.alphaStr)
    search <- Gen.option(genSearch)
  } yield pathElems.filter(_.length > 0).mkString("/", "/", "") + search.getOrElse("")

  def genAbsoluteUri: Gen[String] = for {
    hostport <- hostAndPort match {
      case Some(x) => Gen.const(x)
      case _ => genHostPort
    }
    path <- Gen.option(genAbsolutePath)
    search <- Gen.option(genSearch)
  } yield "http://" + hostport + path.getOrElse("") + search.getOrElse("")

  def genHostPort: Gen[String] = for {
    host <- Gen.oneOf(genHostname, genHostNumber)
    port <- genPort
  } yield s"$host:$port"

  def genHostname: Gen[String] = for {
    length <- Gen.choose(1, 25)
    hostName <- Gen.listOfN(length, Gen.alphaStr)
  } yield hostName.filter(_.length > 0).mkString(".")

  def genHostNumber: Gen[String] = for {
    digits1 <- Gen.choose(0, 0xff)
    digits2 <- Gen.choose(0, 0xff)
    digits3 <- Gen.choose(0, 0xff)
    digits4 <- Gen.choose(0, 0xff)
  } yield s"$digits1.$digits2.$digits3.$digits4"

  def genPort: Gen[String] = for {
    portNum <- Gen.choose(0, 0xffff)
  } yield s"$portNum"

  def genSearch: Gen[String] = for {
    length <- Gen.choose(1, 25)
    search <- Gen.listOfN(length, Gen.alphaStr)
  } yield "?" + search.mkString("+")
}


object UrlGenerator {
  def genUrl: Gen[String] = {
    val generator = new UrlGenerator(None)
    for {
      url <- Gen.oneOf(generator.genAbsolutePath, generator.genAbsoluteUri)
    } yield url
  }

  def genUrlForHost(hostAndPort: String) = {
    val generator = new UrlGenerator(Some(hostAndPort))
    for {
      url <- Gen.oneOf(generator.genAbsolutePath, generator.genAbsoluteUri)
    } yield url
  }

}
