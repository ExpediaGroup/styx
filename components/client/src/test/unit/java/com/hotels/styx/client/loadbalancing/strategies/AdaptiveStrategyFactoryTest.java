/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.client.loadbalancing.strategies;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.support.api.SimpleEnvironment;
import org.testng.annotations.Test;

import static com.hotels.styx.api.configuration.Configuration.EMPTY_CONFIGURATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AdaptiveStrategyFactoryTest {
    private final Environment environment = new SimpleEnvironment.Builder().build();

    @Test
    public void createsAdaptiveStrategyFromConfiguration() {
        Configuration configuration = new Configuration.MapBackedConfiguration().set("requestCount", 57);

        AdaptiveStrategy strategy = new AdaptiveStrategy.Factory().create(environment, configuration);

        assertThat(strategy.requestCount(), is(57));
    }

    @Test
    public void usesDefaultRequestCountIfNoneSpecified() {
        AdaptiveStrategy strategy = new AdaptiveStrategy.Factory().create(environment, EMPTY_CONFIGURATION);

        assertThat(strategy.requestCount(), is(AdaptiveStrategy.DEFAULT_REQUEST_COUNT));
    }
}