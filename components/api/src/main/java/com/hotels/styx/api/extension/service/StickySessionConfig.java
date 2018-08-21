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
package com.hotels.styx.api.extension.service;

import com.google.common.base.Objects;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Objects.toStringHelper;
import static java.util.concurrent.TimeUnit.HOURS;

/**
 * A Configuration used by a load balancer for session stickiness.
 */
public class StickySessionConfig {
    private static final int TWELVE_HOURS = (int) HOURS.toSeconds(12);

    private final boolean enabled;
    private final int timeoutSeconds;

    private StickySessionConfig() {
        this(false, TWELVE_HOURS);
    }

    StickySessionConfig(boolean enabled, Integer timeoutSeconds) {
        this.enabled = enabled;
        this.timeoutSeconds = Optional.ofNullable(timeoutSeconds).orElse(TWELVE_HOURS);
    }

    public static StickySessionConfig stickySessionDisabled() {
        return new StickySessionConfig();
    }

    public static Builder newStickySessionConfigBuilder() {
        return new Builder();
    }

    private StickySessionConfig(Builder builder) {
        this(builder.enabled, builder.timeoutSeconds);
    }

    public boolean stickySessionEnabled() {
        return enabled;
    }

    public int stickySessionTimeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("enabled", enabled)
                .add("timeoutSeconds", timeoutSeconds)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.enabled, this.timeoutSeconds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        StickySessionConfig that = (StickySessionConfig) obj;

        return this.enabled == that.enabled
                && this.timeoutSeconds == that.timeoutSeconds;
    }

    /**
     * A builder for the {StickySessionConfig}.
     */
    public static final class Builder {
        private boolean enabled;
        private int timeoutSeconds = TWELVE_HOURS;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder timeout(int timeout, TimeUnit timeUnit) {
            this.timeoutSeconds = (int) timeUnit.toSeconds(timeout);
            return this;
        }

        public StickySessionConfig build() {
            return new StickySessionConfig(this);
        }
    }
}
