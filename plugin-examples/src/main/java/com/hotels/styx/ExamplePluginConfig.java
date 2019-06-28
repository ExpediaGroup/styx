/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx;


import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This configuration class exists purely to demonstrate how configuration can be provided to Styx plugins.
 *
 * You should rename it to something relevant to your project.
 */
public class ExamplePluginConfig {
    private final String requestHeaderValue;
    private final String responseHeaderValue;

    /**
     * The constructor will be called using the values provided in the YAML configuration files.
     *
     * @param requestHeaderValue value from config
     * @param responseHeaderValue value from config
     */
    public ExamplePluginConfig(
            @JsonProperty("requestHeaderValue") String requestHeaderValue,
            @JsonProperty("responseHeaderValue") String responseHeaderValue) {
        this.requestHeaderValue = requestHeaderValue;
        this.responseHeaderValue = responseHeaderValue;
    }

    /*
     * Typically, your config object will simply return the values it is configured with.
     * Transformations to them are probably more suited to the constructor.
     */
    public String requestHeaderValue() {
        return requestHeaderValue;
    }

    public String responseHeaderValue() {
        return responseHeaderValue;
    }
}
