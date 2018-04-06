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
package com.hotels.styx.support

object ImplicitScalaRxConversions {
  // Necessary type-mangling to make scala Observable[T] to java rx.Observable[T] possible.
  // The original toJavaObservable returned a type rx.Observable[_ <: T] which is not compatible
  // with required rx.Observable[T]. We whould investigate the root cause if this is a problem
  // with plugin interface, or rx.lang.scala toJavaObservable, or something else.
  implicit def toJavaObservable[T](s: rx.lang.scala.Observable[T]): rx.Observable[T] = rx.lang.scala.JavaConversions.toJavaObservable(s).asInstanceOf[rx.Observable[T]]
}
