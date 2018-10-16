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
package com.hotels.styx.routing

import com.hotels.styx.api.HttpInterceptor.Context
import com.hotels.styx.api._
import com.hotels.styx.api.plugins.spi.Plugin
import com.hotels.styx.client.BackendServiceClient
import com.hotels.styx.routing.ImplicitScalaRxConversions.toJavaObservable
import rx.lang.scala.Observable
import rx.{Observable => JavaObservable}

private class ChainAdapter(javaChain: HttpInterceptor.Chain) {
  def proceed(request: LiveHttpRequest): Eventual[HttpResponse] = javaChain.proceed(request)
}

private trait ScalaInterceptor {
  def intercept(request: LiveHttpRequest, chain: ChainAdapter): Observable[HttpResponse]
}

object ImplicitScalaRxConversions {
  // Necessary type-mangling to make scala Observable[T] to java rx.Observable[T] possible.
  // The original toJavaObservable returned a type rx.Observable[_ <: T] which is not compatible
  // with required rx.Observable[T]. We whould investigate the root cause if this is a problem
  // with plugin interface, or rx.lang.scala toJavaObservable, or something else.
  implicit def toJavaObservable[T](s: rx.lang.scala.Observable[T]): rx.Observable[T] = rx.lang.scala.JavaConversions.toJavaObservable(s).asInstanceOf[rx.Observable[T]]
}

class PluginAdapter(scalaInterceptor: (LiveHttpRequest, ChainAdapter) => Eventual[HttpResponse]) extends Plugin {
  def intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain): Eventual[HttpResponse] =
    scalaInterceptor(request, new ChainAdapter(chain))
}

class HttpClientAdapter(sendRequest: LiveHttpRequest => Observable[HttpResponse]) extends BackendServiceClient {
  override def sendRequest(request: LiveHttpRequest): JavaObservable[HttpResponse] =
    toJavaObservable(sendRequest(request))
}

class HttpHandlerAdapter(handler: (LiveHttpRequest, Context) => Eventual[HttpResponse]) extends HttpHandler {
  override def handle(request: LiveHttpRequest, context: Context): Eventual[HttpResponse] = handler(request, context)
}
