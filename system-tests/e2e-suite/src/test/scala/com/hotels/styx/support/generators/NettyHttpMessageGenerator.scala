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

import HttpHeadersGenerator.{HeaderTuple, httpHeaders}
import UrlGenerator.genUrlForHost
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpHeaders.Values.CHUNKED
import io.netty.handler.codec.http.HttpMethod._
import io.netty.handler.codec.http.HttpResponseStatus.OK
import io.netty.handler.codec.http.HttpVersion.{HTTP_1_0, HTTP_1_1}
import io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT
import io.netty.handler.codec.http._
import org.scalacheck.Gen

case class HttpTransaction(requestObjects: List[HttpObject], responseObjects: List[HttpObject])

case class HttpObjects(objects: List[HttpObject])

class NettyHttpMessageGenerator(hostHeader: String) {

  val maxContentChunkSize = 2000

  def nettyRequest: Gen[HttpObjects] = for {
    version <- Gen.const(HTTP_1_1)
    method <- Gen.oneOf(GET, POST, PUT)
    url <- genUrlForHost(hostHeader)
    headers <- httpHeaders
    content <- content(headers)
  } yield {
    val request = new DefaultHttpRequest(version, method, url)
    addHeaders(request, headers)
    addContentLengthOrTransferEncodingHeader(request, content)
    addHostHeader(request, hostHeader)
    HttpObjects(request :: content)
  }

  def nettyResponse: Gen[HttpObjects] = for {
    version <- Gen.oneOf(HTTP_1_0, HTTP_1_1)
    status <- statusCode()
    headers <- httpHeaders
    content <- content(headers)
  } yield {
      val response = new DefaultHttpResponse(version, status)
      addHeaders(response, headers)
      addContentLengthOrTransferEncodingHeader(response, content)
      HttpObjects(List(response) ::: content)
  }

  def addHostHeader(httpMessage: HttpMessage, hostHeader: String) = {
    httpMessage.headers().add(HOST, hostHeader)
  }

  def addContentLengthOrTransferEncodingHeader(httpMessage: HttpMessage, objects: List[HttpObject]) = {
    objects match {
      case List(first: DefaultLastHttpContent) =>
        httpMessage.headers.add(CONTENT_LENGTH, first.content.readableBytes)
      case _ =>
        httpMessage.headers.add(TRANSFER_ENCODING, CHUNKED)
    }
  }

  def addHeaders(httpMessage: HttpMessage, headers: List[HeaderTuple]) = {
    for (header <- headers) {
      httpMessage.headers.add(header._1, header._2)
    }
  }

  def statusCode(): Gen[HttpResponseStatus] = OK

  def content(headers: List[HeaderTuple]): Gen[List[HttpContent]] = Gen.oneOf(bodyContentList(headers), chunkedContent(headers), emptyContentList)

  def bodyContentList(headers: List[HeaderTuple]): Gen[List[HttpContent]] = Gen.listOfN(1, bodyContent(headers))

  def emptyContentList: Gen[List[HttpContent]] = Gen.listOfN(1, EMPTY_LAST_CONTENT)

  def bodyContent(headers: List[HeaderTuple]): Gen[HttpContent] = for {
    contentDataLen <- Gen.choose(0, maxContentChunkSize)
    contentData <- Gen.listOfN(contentDataLen, Gen.alphaLowerChar).map(_.mkString)
  } yield {
    val contentType = HttpHeadersGenerator.contentTypeCharset(headers)
    new DefaultLastHttpContent(Unpooled.copiedBuffer(contentData.getBytes(contentType)))
  }

  def chunkedContent(headers: List[HeaderTuple]): Gen[List[HttpContent]] = for {
    chunks <- Gen.listOf(bodyContent(headers))
  } yield chunks :+ EMPTY_LAST_CONTENT

  def header(header: String): Gen[(String, String)] = for {
    value <- Gen.alphaStr
  } yield (header, value)

}

object NettyHttpMessageGenerator {
  def apply(hostHeader: String) = new NettyHttpMessageGenerator(hostHeader)
}

