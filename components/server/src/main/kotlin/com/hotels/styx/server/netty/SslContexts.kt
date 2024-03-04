/*
  Copyright (C) 2013-2024 Expedia Inc.

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
package com.hotels.styx.server.netty

import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.server.HttpsConnectorConfig
import io.netty.handler.ssl.OpenSslSessionContext
import io.netty.handler.ssl.OpenSslSessionStats
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import io.netty.handler.ssl.util.SelfSignedCertificate
import java.io.File
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * Produce an SslContext based on the provided configuration.
 *
 * @return SslContext
 */
fun HttpsConnectorConfig.newSSLContext(): SslContext =
    if (isConfigured) {
        sslContextFromConfiguration()
    } else {
        sslContextFromSelfSignedCertificate()
    }.build()

/**
 * Produce an SslContext that will record metrics, based on the provided configuration.
 *
 * @param httpsConnectorConfig configuration
 * @param metrics              metrics
 * @return SslContext
 */
fun newSSLContext(
    httpsConnectorConfig: HttpsConnectorConfig,
    metrics: CentralisedMetrics,
) = httpsConnectorConfig.newSSLContext().apply {
    registerOpenSslStats(this, metrics)
}

private fun registerOpenSslStats(
    sslContext: SslContext,
    metrics: CentralisedMetrics,
) {
    sslContext.sessionContext().ifInstanceOf<OpenSslSessionContext> {
        it.stats().let { stats ->
            metrics.proxy.server.openssl.run {
                openSslSessionNumber.register(stats, OpenSslSessionStats::number)
                openSslSessionAccept.register(stats, OpenSslSessionStats::accept)
                openSslSessionAcceptGood.register(stats, OpenSslSessionStats::acceptGood)
                openSslSessionAcceptRenegotiate.register(stats, OpenSslSessionStats::acceptRenegotiate)
                openSslSessionHits.register(stats, OpenSslSessionStats::hits)
                openSslSessionMisses.register(stats, OpenSslSessionStats::misses)
                openSslSessionCbHits.register(stats, OpenSslSessionStats::cbHits)
                openSslSessionCacheFull.register(stats, OpenSslSessionStats::cacheFull)
                openSslSessionTimeouts.register(stats, OpenSslSessionStats::timeouts)
            }
        }
    }
}

private fun HttpsConnectorConfig.sslContextFromSelfSignedCertificate(): SslContextBuilder {
    val certificate = SelfSignedCertificate()

    return SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey())
        .protocols(protocols()?.ifEmpty { null })
        .ciphers(ciphers()?.ifEmpty { null })
        .sslProvider(SslProvider.valueOf(sslProvider()))
}

private fun HttpsConnectorConfig.sslContextFromConfiguration() =
    SslContextBuilder.forServer(File(certificateFile()), File(certificateKeyFile()))
        .sslProvider(SslProvider.valueOf(sslProvider()))
        .ciphers(ciphers()?.ifEmpty { null })
        .sessionTimeout(MILLISECONDS.toSeconds(sessionTimeoutMillis()))
        .sessionCacheSize(sessionCacheSize())
        .protocols(protocols()?.ifEmpty { null })

private inline fun <reified T> Any.ifInstanceOf(block: (T) -> Unit) {
    if (this is T) {
        block(this)
    }
}
