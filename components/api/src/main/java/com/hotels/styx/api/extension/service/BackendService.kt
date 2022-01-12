/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.api.extension.service

import com.hotels.styx.api.Id
import com.hotels.styx.api.Identifiable
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.Origin.checkThatOriginsAreDistinct
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import java.net.URI
import java.util.*

/**
 * Represents the configuration of an application (i.e. a backend service) that Styx can proxy to.
 */
class BackendService(builder: Builder) : Identifiable {
    /**
     * A protocol used for the backend service. This can be either HTTP or HTTPS.
     */
    enum class Protocol {
        HTTP, HTTPS
    }

    private val id: Id = Objects.requireNonNull(builder.id, "id")
    private val path: String = Objects.requireNonNull(builder.path, "path")
    private val connectionPoolSettings: ConnectionPoolSettings = Objects.requireNonNull(builder.connectionPoolSettings)
    private val origins: Set<Origin> = java.util.Set.copyOf(builder.origins)
    private val healthCheckConfig: HealthCheckConfig? = nullIfDisabled(builder.healthCheckConfig)
    private val stickySessionConfig: StickySessionConfig = Objects.requireNonNull(builder.stickySessionConfig)
    private val rewrites: List<RewriteConfig> = Objects.requireNonNull(builder.rewrites)
    private val responseTimeoutMillis: Int = if (builder.responseTimeoutMillis == 0) DEFAULT_RESPONSE_TIMEOUT_MILLIS else builder.responseTimeoutMillis
    private val overrideHostHeader: Boolean = builder.overrideHostHeader
    private val maxHeaderSize: Int = builder.maxHeaderSize
    private val tlsSettings: TlsSettings? = builder.tlsSettings

    init {
        checkThatOriginsAreDistinct(origins)
        if (responseTimeoutMillis < 0) {
            throw IllegalArgumentException("Request timeout must be greater than or equal to zero")
        }
    }

    private fun nullIfDisabled(healthCheckConfig: HealthCheckConfig?): HealthCheckConfig? {
        return if (healthCheckConfig != null && healthCheckConfig.isEnabled) healthCheckConfig else null
    }

    override fun id(): Id {
        return id
    }

    fun idAsString(): String {
        return id.toString()
    }

    fun path(): String {
        return path
    }

    fun origins(): Set<Origin> {
        return origins
    }

    fun connectionPoolConfig(): ConnectionPoolSettings {
        return connectionPoolSettings
    }

    fun healthCheckConfig(): HealthCheckConfig? {
        return healthCheckConfig
    }

    fun stickySessionConfig(): StickySessionConfig {
        return stickySessionConfig
    }

    fun rewrites(): List<RewriteConfig> {
        return rewrites
    }

    fun responseTimeoutMillis(): Int {
        return responseTimeoutMillis
    }

    fun maxHeaderSize(): Int {
        return maxHeaderSize
    }

    fun tlsSettings(): Optional<TlsSettings> {
        return Optional.ofNullable(tlsSettings)
    }

    fun isOverrideHostHeader(): Boolean {
        return overrideHostHeader
    }

    fun getTlsSettings(): TlsSettings? {
        return tlsSettings().orElse(null)
    }

    fun protocol(): Protocol {
        return if (tlsSettings == null) {
            Protocol.HTTP
        } else {
            Protocol.HTTPS
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(
            id, path, connectionPoolSettings, origins,
            healthCheckConfig, stickySessionConfig, rewrites,
            responseTimeoutMillis, maxHeaderSize
        )
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other = obj as BackendService
        return (id == other.id
                && path == other.path
                && connectionPoolSettings == other.connectionPoolSettings
                && origins == other.origins
                && healthCheckConfig == other.healthCheckConfig
                && stickySessionConfig == other.stickySessionConfig
                && rewrites == other.rewrites
                && tlsSettings == other.tlsSettings
                && responseTimeoutMillis == other.responseTimeoutMillis
                && maxHeaderSize == other.maxHeaderSize)
    }

    override fun toString(): String {
        return StringBuilder(128)
            .append(this.javaClass.simpleName)
            .append("{id=")
            .append(id)
            .append(", path=")
            .append(path)
            .append(", origins=")
            .append(origins)
            .append(", connectionPoolSettings=")
            .append(connectionPoolSettings)
            .append(", healthCheckConfig=")
            .append(healthCheckConfig)
            .append(", stickySessionConfig=")
            .append(stickySessionConfig)
            .append(", rewrites=")
            .append(rewrites)
            .append(", tlsSettings=")
            .append(tlsSettings)
            .append('}')
            .toString()
    }

    fun newCopy(): Builder {
        return Builder(this)
    }

    /**
     * Application builder.
     */
    class Builder(backendService: BackendService) {
        var id: Id = Id.GENERIC_APP
        var path: String = "/"
        var origins: Set<Origin> = emptySet()
        var connectionPoolSettings: ConnectionPoolSettings = ConnectionPoolSettings.defaultConnectionPoolSettings()
        var stickySessionConfig: StickySessionConfig = StickySessionConfig.stickySessionDisabled()
        var healthCheckConfig: HealthCheckConfig? = null
        var rewrites: List<RewriteConfig> = emptyList()
        var overrideHostHeader = false
        var responseTimeoutMillis = DEFAULT_RESPONSE_TIMEOUT_MILLIS
        var maxHeaderSize = USE_DEFAULT_MAX_HEADER_SIZE
        var tlsSettings: TlsSettings? = null

        init {
            id = backendService.id
            path = backendService.path
            origins = backendService.origins
            connectionPoolSettings = backendService.connectionPoolSettings
            stickySessionConfig = backendService.stickySessionConfig
            healthCheckConfig = backendService.healthCheckConfig
            rewrites = backendService.rewrites
            responseTimeoutMillis = backendService.responseTimeoutMillis
            maxHeaderSize = backendService.maxHeaderSize
            tlsSettings = backendService.tlsSettings().orElse(null)
            overrideHostHeader = backendService.overrideHostHeader
        }

        /**
         * Adds an ID.
         *
         * @param id an ID
         * @return this builder
         */
        fun id(id: Id?): Builder {
            this.id = Objects.requireNonNull(id)!!
            return this
        }

        /**
         * Sets an ID.
         *
         * @param id an ID
         * @return this builder
         */
        fun id(id: String?): Builder {
            return id(Id.id(id))
        }

        /**
         * Sets a path.
         *
         * @param path a path
         * @return this builder
         */
        fun path(path: String): Builder {
            this.path = checkValidPath(Objects.requireNonNull(path))
            return this
        }

        private fun checkValidPath(path: String): String {
            return try {
                URI.create(path)
                path
            } catch (cause: Throwable) {
                val message = String.format("Invalid path. Path='%s'", path)
                throw IllegalArgumentException(message, cause)
            }
        }

        /**
         * Sets the response timeout in milliseconds.
         *
         * @param timeout a response timeout in milliseconds.
         * @return this builder
         */
        fun responseTimeoutMillis(timeout: Int): Builder {
            responseTimeoutMillis = timeout
            return this
        }

        /**
         * Sets the response max header size in bytes.
         * 0 means use the default.
         *
         * @param maxHeaderSize
         * @return
         */
        fun maxHeaderSize(maxHeaderSize: Int): Builder {
            this.maxHeaderSize = maxHeaderSize
            return this
        }

        /**
         * Sets hosts.
         *
         * @param origins origins
         * @return this builder
         */
        fun origins(origins: Set<Origin>): Builder {
            this.origins = Objects.requireNonNull(origins)
            return this
        }

        /**
         * Sets the https settings.
         * For Jackson JSON serialiser that de-serialises from Option&lt;TlsSettings&gt;.
         */
        fun https(tlsSettings: Optional<TlsSettings?>): Builder {
            this.tlsSettings = tlsSettings.orElse(null)
            return this
        }

        /**
         * Sets the https settings.
         * For programmatic use
         */
        fun httpsOld(tlsSettings: TlsSettings?): Builder {
            this.tlsSettings = tlsSettings
            return this
        }

        /**
         * Sets the https settings.
         * For programmatic use
         */
        fun https(tlsSettings: TlsSettings?): Builder {
            this.tlsSettings = tlsSettings
            return this
        }

        /**
         * Sets hosts.
         *
         * @param origins origins
         * @return this builder
         */
        fun origins(vararg origins: Origin): Builder {
            return origins(mutableSetOf(*origins))
        }

        /**
         * Sets rewrites to be performed on URLs.
         *
         * @param rewriteConfigs rewrite configuration
         * @return this builder
         */
        fun rewrites(vararg rewriteConfigs: RewriteConfig): Builder {
            return rewrites(listOf(*rewriteConfigs))
        }

        /**
         * Sets rewrites to be performed on URLs.
         *
         * @param rewriteConfigs rewrite configuration
         * @return this builder
         */
        fun rewrites(rewriteConfigs: List<RewriteConfig>?): Builder {
            rewrites = java.util.List.copyOf(rewriteConfigs)
            return this
        }

        /**
         * Sets whether incoming host header value should be replaced with origin host
         */
        fun overrideHostHeader(overrideHostHeader: Boolean): Builder {
            this.overrideHostHeader = overrideHostHeader
            return this
        }

        /**
         * Sets connection pool configuration.
         *
         * @param connectionPoolSettings connection pool configuration
         * @return this builder
         */
        fun connectionPoolConfig(connectionPoolSettings: ConnectionPoolSettings?): Builder {
            this.connectionPoolSettings = Objects.requireNonNull(connectionPoolSettings)!!
            return this
        }

        /**
         * Sets sticky-session configuration.
         *
         * @param stickySessionConfig sticky-session configuration.
         * @return this builder
         */
        fun stickySessionConfig(stickySessionConfig: StickySessionConfig?): Builder {
            this.stickySessionConfig = Objects.requireNonNull(stickySessionConfig)!!
            return this
        }

        /**
         * Sets health-check configuration.
         *
         * @param healthCheckConfig health-check configuration
         * @return this builder
         */
        fun healthCheckConfig(healthCheckConfig: HealthCheckConfig?): Builder {
            this.healthCheckConfig = healthCheckConfig
            return this
        }

        /**
         * Builds the application.
         *
         * @return the application
         */
        fun build(): BackendService {
            return BackendService(this)
        }
    }

    companion object {
        const val DEFAULT_RESPONSE_TIMEOUT_MILLIS = 1000
        const val USE_DEFAULT_MAX_HEADER_SIZE = 0

        /**
         * Creates an Application builder.
         *
         * @return a new builder
         */
        fun newBackendServiceBuilder(): Builder {
            return Builder()
        }

        /**
         * Creates an Application builder that inherits from an existing Application.
         *
         * @param backendService application
         * @return a new builder
         */
        fun newBackendServiceBuilder(backendService: BackendService): Builder {
            return Builder(backendService)
        }
    }
}
