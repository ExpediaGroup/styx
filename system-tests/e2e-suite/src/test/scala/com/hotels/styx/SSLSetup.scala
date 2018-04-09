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
package com.hotels.styx

import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier
import javax.net.ssl._
import javax.security.cert.X509Certificate

trait SSLSetup {

  setDefaultHostnameVerifier(new HostnameVerifier {
    override def verify(hostname: String, sslSession: SSLSession): Boolean = {
      if (hostname == "localhost") {
        return true
      }
      return false
    }
  })

  // Create a trust manager that does not validate certificate chains
  val trustAllCerts: Array[TrustManager] = Array[TrustManager](
    new TrustManager {
      def getAcceptedIssuers: Array[X509Certificate] = {
        return null
      }

      def checkClientTrusted(certs: Array[X509Certificate], authType: String) {
      }

      def checkServerTrusted(certs: Array[X509Certificate], authType: String) {
      }
    })

  // Install the all-trusting trust manager
  val sc: SSLContext = SSLContext.getInstance("SSL")
  sc.init(null, trustAllCerts, new SecureRandom)
  HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory)
  System.setProperty("javax.net.debug", "ALL")

}
