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
package com.hotels.styx.client.ssl;

import com.hotels.styx.api.extension.service.Certificate;
import com.hotels.styx.api.extension.service.TlsSettings;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Throwables.propagate;
import static java.util.Objects.requireNonNull;
import static javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm;

/**
 * Factory used to create SSL context.
 */
public final class SslContextFactory {
    private static final Map<TlsSettings, SslContext> SSL_CONTEXT_CACHE = new ConcurrentHashMap<>();

    private static final String DEFAULT_KEY_STORE_TYPE = "JKS";
    private static final String DEFAULT_CERTIFICATE_FACTORY_TYPE = "X.509";

    private SslContextFactory() {
    }

    public static SslContext get(TlsSettings tlsSettings) {
        return SSL_CONTEXT_CACHE.computeIfAbsent(tlsSettings, SslContextFactory::create);
    }

    private static SslContext create(TlsSettings tlsSettings) {
        try {
            return createSslContext(tlsSettings);
        } catch (Exception e) {
            throw propagate(e);
        }
    }

    private static SslContext createSslContext(TlsSettings tlsSettings) throws IOException, NoSuchAlgorithmException, KeyStoreException, CertificateException {
        return SslContextBuilder
                .forClient()
                .sslProvider(SslProvider.valueOf(tlsSettings.sslProvider()))
                .trustManager(trustManagerFactory(tlsSettings))
                .protocols(toNettyProtocols(tlsSettings.protocols()))
                .ciphers(toNettyCiphers(tlsSettings.cipherSuites()))
                .build();
    }

    private static List<String> toNettyCiphers(List<String> strings) {
        if (strings == null || strings.isEmpty()) {
            return null;
        } else {
            return strings;
        }
    }

    private static String[] toNettyProtocols(List<String> protocols) {
        if (protocols == null || protocols.isEmpty()) {
            return null;
        } else {
            return protocols.toArray(new String[protocols.size()]);
        }
    }

    private static TrustManagerFactory trustManagerFactory(TlsSettings tlsSettings) throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        return tlsSettings.trustAllCerts()
                ? InsecureTrustManagerFactory.INSTANCE
                : initializeTrustManager(tlsSettings);
    }

    private static TrustManagerFactory initializeTrustManager(TlsSettings tlsSettings) throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(getDefaultAlgorithm());
        trustManagerFactory.init(initializeTrustStore(tlsSettings));
        return trustManagerFactory;
    }

    private static KeyStore initializeTrustStore(TlsSettings tlsSettings) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore keyStore = keyStore(tlsSettings.trustStorePath(), tlsSettings.trustStorePassword());
        tlsSettings.additionalCerts().forEach(certificate -> addCertificateToKeyStore(keyStore, certificate));
        return keyStore;
    }

    private static KeyStore keyStore(String trustStorePath, char[] trustStorePassword) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        requireNonNull(trustStorePath);
        requireNonNull(trustStorePassword);

        KeyStore keyStore = KeyStore.getInstance(DEFAULT_KEY_STORE_TYPE);

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(trustStorePath))) {
            keyStore.load(inputStream, trustStorePassword);
        }

        return keyStore;
    }

    private static void addCertificateToKeyStore(KeyStore keyStore, Certificate aCertificate) {
        try {
            keyStore.setCertificateEntry(aCertificate.getAlias(), certificate(aCertificate));
        } catch (Exception e) {
            throw propagate(e);
        }
    }

    private static java.security.cert.Certificate certificate(Certificate aCertificate) throws IOException, CertificateException {
        try (FileInputStream inputStream = new FileInputStream(aCertificate.getCertificatePath())) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance(DEFAULT_CERTIFICATE_FACTORY_TYPE);

            return  certificateFactory.generateCertificate(inputStream);
        }
    }
}
