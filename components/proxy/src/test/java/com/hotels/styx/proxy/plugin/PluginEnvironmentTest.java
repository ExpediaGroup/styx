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
package com.hotels.styx.proxy.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.spi.config.SpiExtension;
import com.hotels.styx.spi.config.SpiExtensionFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PluginEnvironmentTest {

    private MetricRegistry styxMetrics;
    private Environment styxEnvironment;
    private JsonNode config = new IntNode(5);

    @BeforeMethod
    public void setUp() throws Exception {
        styxMetrics = new CodaHaleMetricRegistry();
        styxEnvironment = mock(PluginFactory.Environment.class);
        when(styxEnvironment.metricRegistry()).thenReturn(styxMetrics);
    }

    @Test
    public void emptyStringMapsToRootScope() throws Exception {
        pluginEnvironment("PluginX", "").metricRegistry().counter("x.count").inc();
        assertThat(styxMetrics.counter("PluginX.x.count").getCount(), is(1L));
    }

    @Test
    public void nullScopeIsTreatedAsEmptyString() throws Exception {
        pluginEnvironment("PluginX", null).metricRegistry().counter("x.count").inc();
        assertThat(styxMetrics.counter("PluginX.x.count").getCount(), is(1L));
    }

    @Test
    public void exposesPluginMetricsInIsolatedScope() throws Exception {
        String styxScope = "styx.plugins";

        pluginEnvironment("PluginX", styxScope).metricRegistry().counter("x.count").inc();
        pluginEnvironment("PluginY", styxScope).metricRegistry().counter("x.count").inc();

        assertThat(styxMetrics.counter("styx.plugins.PluginX.x.count").getCount(), is(1L));
        assertThat(styxMetrics.counter("styx.plugins.PluginY.x.count").getCount(), is(1L));
    }


    private PluginEnvironment pluginEnvironment(String pluginName, String styxScope) {
        return new PluginEnvironment(pluginName, styxEnvironment, pluginMetadata(pluginName), styxScope);
    }

    private SpiExtension pluginMetadata(String pluginName) {
        SpiExtensionFactory factory = new SpiExtensionFactory("PluginXFactory", "/path");

        return new SpiExtension(factory, config, null);
    }

}
