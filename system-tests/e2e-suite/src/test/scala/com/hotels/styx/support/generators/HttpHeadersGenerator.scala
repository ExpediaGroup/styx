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

import io.netty.handler.codec.http.HttpHeaders.Names._
import org.scalacheck.Gen

object HttpHeadersGenerator {
  type HeaderTuple = (String, String)
  type HeaderGeneratorFunction = String => Gen[HeaderTuple]

  private val knownHeaders = List[(String, HeaderGeneratorFunction)](
    (ACCEPT, randomHeader),
    (ACCEPT_CHARSET, randomHeader),
    (ACCEPT_ENCODING, randomHeader),
    (ACCEPT_LANGUAGE, randomHeader),
    (AUTHORIZATION, randomHeader),
    (EXPECT, randomHeader),
    (FROM, randomHeader),
    (IF_MATCH, randomHeader),
    (IF_MODIFIED_SINCE, randomHeader),
    (IF_NONE_MATCH, randomHeader),
    (IF_RANGE, randomHeader),
    (IF_UNMODIFIED_SINCE, randomHeader),
    (MAX_FORWARDS, randomHeader),
    (PROXY_AUTHORIZATION, randomHeader),
    (RANGE, randomHeader),
    (REFERER, randomHeader),
    (TE, randomHeader),
    (VIA, viaHeader),
    (DATE, dateHeader),
    (CONTENT_TYPE, contentTypeHeader),
    (COOKIE, cookieHeader),
    (USER_AGENT, userAgentHeader)
  )

  private val genlist = knownHeaders.map {
    case (name, headerGenerator) => headerGenerator(name)
  }

  def httpHeaders: Gen[List[HeaderTuple]] = for {
    count <- Gen.choose(0, knownHeaders.size)
    headers <- Gen.sequence[List, HeaderTuple](genlist).flatMap(Gen.pick(count, _))
  } yield headers.toList

  def randomHeader(header: String): Gen[HeaderTuple] = for {
    value <- Gen.alphaStr
  } yield (header, value)

  // Example: Mozilla/[version] ([system and browser information]) [platform] ([platform details]) [extensions]
  def userAgentHeader(name: String = USER_AGENT): Gen[HeaderTuple] = for {
    browser <- Gen.oneOf(Gen.const("Mozilla"), Gen.const("Opera"), Gen.alphaStr)
    version <- Gen.alphaStr
    sysAndBrowserInfo <- Gen.alphaStr
    platform <- Gen.alphaStr
    platformDetails <- Gen.alphaStr
    extensions <- Gen.alphaStr
  } yield (name, s"$browser/$version ($sysAndBrowserInfo) $platform ($platformDetails) $extensions")

  def cookieHeader(name: String = COOKIE): Gen[HeaderTuple] = for {
    cookieName <- Gen.alphaStr
    cookieValue <- Gen.alphaStr
  } yield (name, s"$cookieName=$cookieValue")

  // Example: Content-Type: text/html; charset=utf-8
  def contentTypeHeader(name: String = CONTENT_TYPE): Gen[HeaderTuple] = for {
    charset <- Gen.oneOf("utf-8", "utf-16", "utf-32", "us-ascii")
  } yield (name, s"text/plain; charset=$charset")

  def dateHeader(name: String = DATE): Gen[HeaderTuple] = for {
    httpDate <- Gen.oneOf(
      rfc1123DateFormat,
      rfc850DateFormat,
      ascTimeDateFormat)
  } yield (name, httpDate)

  def rfc1123DateFormat: Gen[String] = for {
    w <- wkday
    d <- date1
    t <- time
  } yield s"$w, $d $t GMT"

  def rfc850DateFormat: Gen[String] = for {
    w <- weekday
    d <- date2
    t <- time
  } yield s"$w, $d $t"

  def ascTimeDateFormat: Gen[String] = for {
    w <- wkday
    d <- date3
    t <- time
    y <- Gen.choose(0, 9999)
  } yield s"$w $d $t %04d".format(y)

  def wkday = Gen.oneOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

  def weekday = Gen.oneOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

  def date1 = for {
    d <- Gen.choose(1, 31)
    m <- month
    y <- Gen.choose(1, 9999)
  } yield "%02d %s %04d".format(d, m, y)

  def date2 = for {
    d <- Gen.choose(1, 31)
    m <- month
    y <- Gen.choose(0, 99)
  } yield "%02d-%s-%02d".format(d, m, y)

  def date3 = for {
    m <- month
    d <- Gen.choose(1, 31)
  } yield "%s %d".format(m, d)

  def month = Gen.oneOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

  def time = for {
    hours <- Gen.choose(0, 23)
    mins <- Gen.choose(0, 59)
    secs <- Gen.choose(0, 59)
  } yield "%02d:%02d:%02d".format(hours, mins, secs)

  def viaHeader(name: String = VIA): Gen[HeaderTuple] = for {
    viaEntries <- Gen.containerOf[List, String](viaHeaderEntry)
  } yield (name, viaEntries.mkString(", "))

  def viaHeaderEntry: Gen[String] = for {
    major <- Gen.numChar
    minor <- Gen.numChar
    name <- Gen.alphaStr
    comment <- Gen.option(Gen.alphaStr)
  } yield {
    val h1 = s"$major/$minor $name"
    val h2 = comment match {
      case Some(t: String) => s" ($t)"
      case _ => ""
    }
    h1 + h2
  }

  def contentTypeCharset(headers: List[HeaderTuple]): String = {
    val pattern = ".+charset=([-a-zA-Z0-9]+)" r
    val charset = headers
      .find { case (name, value) => name.toLowerCase == CONTENT_TYPE.toLowerCase}
      .flatMap {
      case (name, value) => {
        value match {
          case pattern(cs) =>
            Some(cs)
          case _ =>
            None
        }
      }
    }.getOrElse("utf-8")
    charset
  }

}
