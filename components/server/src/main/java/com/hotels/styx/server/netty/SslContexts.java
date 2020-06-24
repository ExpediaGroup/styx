/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.server.netty;

import com.hotels.styx.server.HttpsConnectorConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.handler.ssl.OpenSslSessionContext;
import io.netty.handler.ssl.OpenSslSessionStats;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import java.io.File;
import java.security.cert.CertificateException;
import java.util.List;

import static com.hotels.styx.api.Metrics.name;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Methods for producing {@link SslContext} classes.
 */
public final class SslContexts {

    public static final String SSL_PREFIX = "connections.openssl.session";

    private SslContexts() {
    }

    /**
     * Produce an SslContext based on the provided configuration.
     *
     * @param httpsConnectorConfig configuration
     * @return SslContext
     */
    public static SslContext newSSLContext(HttpsConnectorConfig httpsConnectorConfig) {
        SslContextBuilder builder = httpsConnectorConfig.isConfigured()
                ? sslContextFromConfiguration(httpsConnectorConfig)
                : sslContextFromSelfSignedCertificate(httpsConnectorConfig);

        try {
            return builder.build();
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Produce an SslContext that will record metrics, based on the provided configuration.
     *
     * @param httpsConnectorConfig configuration
     * @param metricRegistry       metric registry
     * @param metricPrefix         prepended to all meter names
     * @return SslContext
     */
    public static SslContext newSSLContext(HttpsConnectorConfig httpsConnectorConfig, MeterRegistry metricRegistry, String metricPrefix) {
        SslContext sslContext = newSSLContext(httpsConnectorConfig);
        registerOpenSslStats(sslContext, metricRegistry, metricPrefix);
        return sslContext;
    }

    private static void registerOpenSslStats(SslContext sslContext, MeterRegistry metricRegistry, String metricPrefix) {
        SSLSessionContext sslSessionContext = sslContext.sessionContext();
        if (sslSessionContext instanceof OpenSslSessionContext) {
            OpenSslSessionStats stats = ((OpenSslSessionContext) sslSessionContext).stats();
            metricRegistry.gauge(name(metricPrefix, SSL_PREFIX, "number"), stats, OpenSslSessionStats::number);
            metricRegistry.gauge(name(metricPrefix, SSL_PREFIX, "accept"), stats, OpenSslSessionStats::accept);
            metricRegistry.gauge(name(metricPrefix, SSL_PREFIX, "acceptGood"), stats, OpenSslSessionStats::acceptGood);
            metricRegistry.gauge(name(metricPrefix, SSL_PREFIX, "acceptRenegotiate"), stats, OpenSslSessionStats::acceptRenegotiate);
            metricRegistry.gauge(name(metricPrefix, SSL_PREFIX, "hits"), stats, OpenSslSessionStats::hits);
            metricRegistry.gauge(name(metricPrefix, SSL_PREFIX, "misses"), stats, OpenSslSessionStats::misses);
            metricRegistry.gauge(name(metricPrefix, SSL_PREFIX, "cbHits"), stats, OpenSslSessionStats::cbHits);
            metricRegistry.gauge(name(metricPrefix, SSL_PREFIX, "cacheFull"), stats, OpenSslSessionStats::cacheFull);
            metricRegistry.gauge(name(metricPrefix, SSL_PREFIX, "timeouts"), stats, OpenSslSessionStats::timeouts);
        }
    }

    private static SelfSignedCertificate newSelfSignedCertificate() {
        try {
            return new SelfSignedCertificate();
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    private static SslContextBuilder sslContextFromSelfSignedCertificate(HttpsConnectorConfig httpsConnectorConfig) {
        SelfSignedCertificate certificate = newSelfSignedCertificate();
        return SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey())
                .protocols(toProtocolsOrDefault(httpsConnectorConfig.protocols()))
                .ciphers(toCiphersOrDefault(httpsConnectorConfig.ciphers()))
                .sslProvider(SslProvider.valueOf(httpsConnectorConfig.sslProvider()));
    }

    private static SslContextBuilder sslContextFromConfiguration(HttpsConnectorConfig httpsConnectorConfig) {
        return SslContextBuilder.forServer(new File(httpsConnectorConfig.certificateFile()), new File(httpsConnectorConfig.certificateKeyFile()))
                .sslProvider(SslProvider.valueOf(httpsConnectorConfig.sslProvider()))
                .ciphers(toCiphersOrDefault(httpsConnectorConfig.ciphers()))
                .sessionTimeout(MILLISECONDS.toSeconds(httpsConnectorConfig.sessionTimeoutMillis()))
                .sessionCacheSize(httpsConnectorConfig.sessionCacheSize())
                .protocols(toProtocolsOrDefault(httpsConnectorConfig.protocols()));
    }

    private static Iterable<String> toCiphersOrDefault(List<String> ciphers) {
        return ciphers.isEmpty() ? null : ciphers;
    }

    private static String[] toProtocolsOrDefault(List<String> elems) {
        return (elems != null && elems.size() > 0)
                ? elems.toArray(new String[elems.size()])
                : null;
    }
}
