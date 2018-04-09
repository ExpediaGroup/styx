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
package com.hotels.styx.generators

import java.net.URL

import com.fasterxml.jackson.dataformat.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http.{DefaultHttpRequest, HttpMethod, HttpRequest, HttpVersion}
import org.scalacheck.Gen.oneOf
import org.scalacheck._

import scala.io.Source.fromURL

object HttpRequestGenerator {
  type Version = String
  type Method = String
  type Header = (String, String)
  type Host = String

  final case class MaliciousRequest(version: Version, method: Method, uri: String, headers: Seq[Header]) {
    override def toString: String = {
      s"$method $uri $version\r\n" +
        headers.map { case (k, v) => s"$k: $v" }.mkString("\r\n") +
        "\r\n\r\n"
    }

    def toBase64 = Base64Coder.encodeString(this.toString)
  }

  val resource: URL = getClass.getResource("/bad-uris.txt").toURI.toURL
  val maliciousURIs = fromURL(resource, "UTF-8").getLines().toSeq

}

trait HttpRequestGenerator {
  def uris() = {
    oneOf(HttpRequestGenerator.maliciousURIs).retryUntil(_.nonEmpty)
  }

  def protocols: Gen[HttpVersion] = Gen.oneOf(
    HTTP_1_1,
    HTTP_1_0)

  def methods: Gen[String] = Gen.oneOf(
    "OPTIONS",
    "GET",
    "HEAD",
    "POST",
    "PUT",
    "PATCH",
    "DELETE",
    "TRACE",
    "CONNECT")

  def badHeaderNames: Gen[String] = Gen.oneOf(
    "foo=bar1",
    "foo,bar2",
    "foo;bar3"
  )

  def normalUris: Gen[String] = Gen.alphaStr.suchThat(!_.isEmpty)

  def requestsTo(host: String): Gen[HttpRequest] = for {
    version <- protocols
    method <- methods
    uri <- uris()
  } yield {
      new DefaultHttpRequest(version, HttpMethod.valueOf(method), uri, false)
    }

  def badRequests(host: String): Gen[HttpRequest] = for {
    version <- protocols
    method <- methods
    uri <- uris
  } yield {
      val request = new DefaultHttpRequest(version, HttpMethod.valueOf(method), uri)
      request.headers().add("Host", host)
      request
    }

  def requestsWithBadHeaderNames(host: String): Gen[HttpRequest] = for {
    version <- protocols
    method <- methods
    uri <- normalUris
    headerName <- badHeaderNames
  } yield {
      val request = new DefaultHttpRequest(version, HttpMethod.valueOf(method), "http://" + host + "/" + uri, false)
      request.headers().add("Host", host)
      request.headers().add(headerName, "value")
      request
    }


}
