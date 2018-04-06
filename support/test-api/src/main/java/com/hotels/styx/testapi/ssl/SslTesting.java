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
package com.hotels.styx.testapi.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;

import static com.google.common.base.Throwables.propagate;
import static javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier;
import static javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory;

/**
 * Utilities to allow easy testing of SSL.
 */
public final class SslTesting {
    private SslTesting() {
    }

    /**
     * Prevents Java from throwing exceptions regarding SSL certificates. This should only be called in test code.
     */
    public static void acceptAllSslRequests() {
        try {
            setUpSsl();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            propagate(e);
        }
    }

    private static void setUpSsl() throws NoSuchAlgorithmException, KeyManagementException {
        setDefaultHostnameVerifier((hostname, sslSession) -> Objects.equals(hostname, "localhost"));

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{new AcceptAllTrustManager()};

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new SecureRandom());
        setDefaultSSLSocketFactory(sc.getSocketFactory());
        System.setProperty("javax.net.debug", "ALL");
    }

    private static class AcceptAllTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
}
