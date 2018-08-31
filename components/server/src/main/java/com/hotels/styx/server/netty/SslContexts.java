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
package com.hotels.styx.server.netty;

import com.codahale.metrics.Gauge;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.server.HttpsConnectorConfig;
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

import static com.google.common.base.Throwables.propagate;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Methods for producing {@link SslContext} classes.
 */
public final class SslContexts {
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
            throw propagate(e);
        }
    }

    /**
     * Produce an SslContext that will record metrics, based on the provided configuration.
     *
     * @param httpsConnectorConfig configuration
     * @param metricRegistry       metric registry
     * @return SslContext
     */
    public static SslContext newSSLContext(HttpsConnectorConfig httpsConnectorConfig, MetricRegistry metricRegistry) {
        SslContext sslContext = newSSLContext(httpsConnectorConfig);
        registerOpenSslStats(sslContext, metricRegistry);
        return sslContext;
    }

    private static void registerOpenSslStats(SslContext sslContext, MetricRegistry metricRegistry) {
        SSLSessionContext sslSessionContext = sslContext.sessionContext();
        if (sslSessionContext instanceof OpenSslSessionContext) {
            OpenSslSessionStats stats = ((OpenSslSessionContext) sslSessionContext).stats();
            MetricRegistry sessionStatsRegistry = metricRegistry.scope("connections.openssl.session");
            sessionStatsRegistry.register("number", (Gauge<Long>) stats::number);
            sessionStatsRegistry.register("accept", (Gauge<Long>) stats::accept);
            sessionStatsRegistry.register("acceptGood", (Gauge<Long>) stats::acceptGood);
            sessionStatsRegistry.register("acceptRenegotiate", (Gauge<Long>) stats::acceptRenegotiate);
            sessionStatsRegistry.register("hits", (Gauge<Long>) stats::hits);
            sessionStatsRegistry.register("misses", (Gauge<Long>) stats::misses);
            sessionStatsRegistry.register("cbHits", (Gauge<Long>) stats::cbHits);
            sessionStatsRegistry.register("cacheFull", (Gauge<Long>) stats::cacheFull);
            sessionStatsRegistry.register("timeouts", (Gauge<Long>) stats::timeouts);
        }
    }

    private static SelfSignedCertificate newSelfSignedCertificate() {
        try {
            return new SelfSignedCertificate();
        } catch (CertificateException e) {
            throw propagate(e);
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
