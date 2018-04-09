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
package com.hotels.styx.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.google.common.base.Objects.firstNonNull;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * HTTP configuration settings, used to connect to origins.
 */
public final class HttpConfig {
    private boolean compress;
    private int maxInitialLineLength = 4096;
    private int maxHeadersSize = 8192;
    private int maxChunkSize = 8192;
    private int maxContentLength = 65536;
    private Iterable<ChannelOptionSetting> settings = emptyList();

    @JsonCreator
    HttpConfig(@JsonProperty("maxChunkSize") Integer maxChunkSize,
               @JsonProperty("maxHeaderSize") Integer maxHeadersSize,
               @JsonProperty("maxInitialLength") Integer maxInitialLineLength) {
        this.maxChunkSize = firstNonNull(maxChunkSize, 8192);
        this.maxHeadersSize = firstNonNull(maxHeadersSize, 8192);
        this.maxInitialLineLength = firstNonNull(maxInitialLineLength, 4096);
    }

    private HttpConfig(Builder builder) {
        this.compress = builder.compress;
        this.maxInitialLineLength = builder.maxInitialLineLength;
        this.maxHeadersSize = builder.maxHeadersSize;
        this.maxChunkSize = builder.maxChunkSize;
        this.maxContentLength = builder.maxContentLength;
        this.settings = builder.settings;
    }

    /**
     * Whether the origin returns compressed content.
     *
     * @return true if compressed
     */
    public boolean compress() {
        return compress;
    }

    /**
     * The maximum length in bytes of the initial line of an HTTP message, e.g. {@code GET http://example.org/ HTTP/1.1}.
     *
     * @return maximum length of initial line
     */
    public int maxInitialLineLength() {
        return maxInitialLineLength;
    }

    /**
     * The maximum combined size of the HTTP headers in bytes.
     *
     * @return maximum combined size of headers
     */
    public int maxHeadersSize() {
        return maxHeadersSize;
    }

    /**
     * The maximum size of an HTTP chunk in bytes.
     *
     * @return maximum chunk size
     */
    public int maxChunkSize() {
        return maxChunkSize;
    }

    /**
     * The maximum size of the HTTP message body in bytes.
     *
     * @return maximum body size
     */
    public int maxContentLength() {
        return maxContentLength;
    }

    /**
     * Netty channel options to set in client Bootstrap.
     *
     * @return netty channel options
     */
    public Iterable<ChannelOptionSetting> channelSettings() {
        return settings;
    }

    /**
     * Create a new builder with default settings.
     *
     * @return a new builder
     */
    public static Builder newHttpConfigBuilder() {
        return new Builder();
    }

    /**
     * Create a new instance with default settings.
     *
     * @return a new instance
     */
    public static HttpConfig defaultHttpConfig() {
        return newHttpConfigBuilder().build();
    }

    /**
     * HttpConfig builder.
     */
    public static final class Builder {
        private boolean compress;
        private int maxInitialLineLength = 4096;
        private int maxHeadersSize = 8192;
        private int maxChunkSize = 8192;
        private int maxContentLength = 65536;
        private Iterable<ChannelOptionSetting> settings = emptyList();

        private Builder() {
        }

        /**
         * Set whether the origin returns compressed content.
         *
         * @param compress true if compressed
         * @return this builder
         */
        public Builder setCompress(boolean compress) {
            this.compress = compress;
            return this;
        }

        /**
         * Set the maximum size of an HTTP chunk in bytes.
         *
         * @param maxChunkSize maximum size of an HTTP chunk
         * @return this builder
         */
        public Builder setMaxChunkSize(int maxChunkSize) {
            this.maxChunkSize = maxChunkSize;
            return this;
        }

        /**
         * Set the maximum length in bytes of the initial line of an HTTP message, e.g. {@code GET http://example.org/ HTTP/1.1}.
         *
         * @param maxInitialLineLength maximum length in bytes of the initial line
         * @return this builder
         */
        public Builder setMaxInitialLineLength(int maxInitialLineLength) {
            this.maxInitialLineLength = maxInitialLineLength;
            return this;
        }

        /**
         * Set the maximum combined size of the HTTP headers in bytes.
         *
         * @param maxHeaderSize maximum combined size of the HTTP headers
         * @return this builder
         */
        public Builder setMaxHeadersSize(int maxHeaderSize) {
            this.maxHeadersSize = maxHeaderSize;
            return this;
        }

        /**
         * Set netty channel options to set in client Bootstrap.
         *
         * @param settings netty channel options
         * @return this builder
         */
        public Builder setSettings(ChannelOptionSetting... settings) {
            this.settings = asList(settings);
            return this;
        }

        /**
         * Set the maximum size of the HTTP message body in bytes.
         *
         * @param maxContentLength maximum size of the HTTP message body
         * @return this builder
         */
        public Builder setMaxContentLength(int maxContentLength) {
            this.maxContentLength = maxContentLength;
            return this;
        }

        /**
         * Create an instance of HttpConfig using the configured settings.
         *
         * @return a new instance
         */
        public HttpConfig build() {
            return new HttpConfig(this);
        }
    }
}
