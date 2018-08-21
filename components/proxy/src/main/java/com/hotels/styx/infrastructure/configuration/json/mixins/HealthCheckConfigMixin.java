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
package com.hotels.styx.infrastructure.configuration.json.mixins;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.hotels.styx.api.extension.service.HealthCheckConfig;

/**
 * Jackson annotations for {@link HealthCheckConfig}.
 */
@JsonDeserialize(builder = HealthCheckConfig.Builder.class)
public interface HealthCheckConfigMixin {
    @JsonProperty("uri")
    String getUri();

    @JsonProperty("intervalMillis")
    public long intervalMillis();

    @JsonProperty("timeoutMillis")
    public long timeoutMillis();

    @JsonProperty("healthyThreshold")
    public int healthyThreshold();

    @JsonProperty("unhealthyThreshold")
    public int unhealthyThreshold();

    @JsonIgnore
    public boolean isEnabled();

    /**
     * A builder of {@link HealthCheckConfigMixin}s.
     */
    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
    public interface Builder {
        @JsonProperty("uri")
        public Builder uri(String uri);

        @JsonProperty("intervalMillis")
        public Builder interval(long interval);

        @JsonProperty("timeoutMillis")
        public Builder timeout(long timeout);

        @JsonProperty("healthyThreshold")
        public Builder healthyThreshold(int healthyThreshold);

        @JsonProperty("unhealthyThreshold")
        public Builder unhealthyThreshold(int unhealthyThreshold);
    }
}
