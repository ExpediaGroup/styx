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
package com.hotels.styx.support.configuration

import com.hotels.styx.api.extension
import com.hotels.styx.support.configuration.TlsSettings.default

import scala.collection.JavaConverters._

case class Certificate(alias: String, certificatePath: String) {
  def asJava: extension.service.Certificate = extension.service.Certificate.certificate(alias, certificatePath)
}

object Certificate {
  def fromJava(from: extension.service.Certificate): Certificate = Certificate(from.getAlias, from.getCertificatePath)
}

case class TlsSettings(authenticate: Boolean = default.authenticate(),
                       sslProvider: String = default.sslProvider(),
                       addlCerts: Seq[Certificate] = Seq.empty,
                       trustStorePath: String = default.trustStorePath,
                       trustStorePassword: String = default.trustStorePassword.toString,
                       protocols: Seq[String] = default.protocols.asScala,
                       cipherSuites: Seq[String] = default.cipherSuites.asScala
) {
  def asJava: extension.service.TlsSettings = {
    new extension.service.TlsSettings.Builder()
      .authenticate(authenticate)
      .sslProvider(sslProvider)
      .additionalCerts(
        addlCerts.map(_.asJava): _*)
      .trustStorePath(trustStorePath)
      .trustStorePassword(trustStorePassword)
      .protocols(protocols.asJava)
      .cipherSuites(cipherSuites.asJava)
      .build()
  }
}

object TlsSettings {
  private val default = new extension.service.TlsSettings.Builder().build()

  def fromJava(from: extension.service.TlsSettings): TlsSettings =
    apply(
      authenticate = from.authenticate(),
      sslProvider = from.sslProvider(),
      addlCerts = from.additionalCerts().asScala.map(Certificate.fromJava).toSeq,
      trustStorePath = from.trustStorePath,
      trustStorePassword = from.trustStorePassword.toString,
      protocols = from.protocols().asScala,
      cipherSuites = from.cipherSuites().asScala
    )
}
