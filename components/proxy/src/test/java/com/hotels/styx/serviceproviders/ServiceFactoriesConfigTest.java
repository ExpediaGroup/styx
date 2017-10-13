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
package com.hotels.styx.serviceproviders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.Environment;
import com.hotels.styx.support.api.SimpleEnvironment;
import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.configuration.ServiceFactory;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Map;

import static ch.qos.logback.classic.Level.INFO;
import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static com.hotels.styx.support.matchers.MapMatcher.isMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;

public class ServiceFactoriesConfigTest {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);

    private final Environment environment = new SimpleEnvironment.Builder().build();

    private LoggingTestSupport log;

    private final String factoryConfigsYaml = "" +
            "factories:\n" +
            "  factory1:\n" +
            "    enabled: true\n" +
            "    class: " + MyFactoryA.class.getName() + "\n" +
            "    config: {configValue: expectedValue1}\n" +
            "  factory2:\n" +
            "    enabled: true\n" +
            "    class: " + MyFactoryB.class.getName() + "\n" +
            "    config: {configValue: expectedValue2}\n" +
            "  factory3:\n" +
            "    enabled: true\n" +
            "    class: " + MyFactoryA.class.getName() + "\n" +
            "    config: {configValue: expectedValue3}\n";

    private final String factoryConfigsWithFailureYaml = "" +
            "factories:\n" +
            "  goodFactory:\n" +
            "    enabled: true\n" +
            "    class: " + MyFactoryA.class.getName() + "\n" +
            "    config: {configValue: expectedValue1}\n" +
            "  badFactory:\n" +
            "    enabled: true\n" +
            "    class: " + FailingFactory.class.getName() + "\n" +
            "    config: {configValue: expectedValue2}\n";

    private final String factoryConfigsWithDisabledFactories = "" +
            "factories:\n" +
            "  factory1:\n" +
            "    enabled: true\n" +
            "    class: " + MyFactoryA.class.getName() + "\n" +
            "    config: {configValue: expectedValue1}\n" +
            "  inactiveFactory1:\n" +
            "    enabled: false\n" +
            "    class: " + MyFactoryA.class.getName() + "\n" +
            "    config: {configValue: unexpectedValue1}\n" +
            "  factory2:\n" +
            "    class: " + MyFactoryB.class.getName() + "\n" +
            "    config: {configValue: expectedValue2}\n" +
            "  inactiveFactory2:\n" +
            "    enabled: false\n" +
            "    class: " + MyFactoryA.class.getName() + "\n" +
            "    config: {configValue: unexpectedValue2}\n";

    @BeforeMethod
    public void startRecordingLogs() {
        log = new LoggingTestSupport(ServiceFactoriesConfig.class);
    }

    @AfterMethod
    public void stopRecordingLogs() {
        log.stop();
    }

    @Test
    public void createsServicesFromConfiguration() throws IOException {
        ServiceFactoriesConfig factoriesConfig = loadFromYaml(factoryConfigsYaml, ServiceFactoriesConfig.class);

        assertThat(factoriesConfig.names(), contains("factory1", "factory2", "factory3"));

        Map<String, String> services = factoriesConfig.loadServices(environment, String.class);

        assertThat(services, isMap(ImmutableMap.of(
                "factory1", "expectedValue1_from_factory_type_A",
                "factory2", "expectedValue2_from_factory_type_B",
                "factory3", "expectedValue3_from_factory_type_A"
        )));
    }

    @Test
    public void excludesDisabledServices() throws IOException {
        ServiceFactoriesConfig factoriesConfig = loadFromYaml(factoryConfigsWithDisabledFactories, ServiceFactoriesConfig.class);

        assertThat(factoriesConfig.names(), contains("factory1", "inactiveFactory1", "factory2", "inactiveFactory2"));

        Map<String, String> services = factoriesConfig.loadServices(environment, String.class);

        assertThat(services, isMap(ImmutableMap.of(
                "factory1", "expectedValue1_from_factory_type_A",
                "factory2", "expectedValue2_from_factory_type_B"
        )));

        assertThat(log.log(), containsInAnyOrder(
                loggingEvent(INFO, "Service 'factory1' is ENABLED"),
                loggingEvent(INFO, "Service 'inactiveFactory1' is DISABLED"),
                loggingEvent(INFO, "Service 'factory2' is ENABLED"),
                loggingEvent(INFO, "Service 'inactiveFactory2' is DISABLED")
        ));
    }

    @Test(expectedExceptions = ConfigurationException.class, expectedExceptionsMessageRegExp = "Error creating service")
    public void reportsWhichFactoryFailedUponError() throws IOException {
        ServiceFactoriesConfig factoriesConfig = loadFromYaml(factoryConfigsWithFailureYaml, ServiceFactoriesConfig.class);

        factoriesConfig.loadServices(environment, String.class);
    }

    private <T> T loadFromYaml(String yaml, Class<T> tClass) {
        try {
            return YAML_MAPPER.readValue(yaml, tClass);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    public static class MyFactoryA extends StringFactory {
        public MyFactoryA() {
            super("_from_factory_type_A");
        }
    }

    public static class MyFactoryB extends StringFactory {
        public MyFactoryB() {
            super("_from_factory_type_B");
        }
    }

    private static class StringFactory implements ServiceFactory<String> {
        private final String suffix;

        public StringFactory(String suffix) {
            this.suffix = suffix;
        }

        @Override
        public String create(Environment environment, Configuration serviceConfiguration) {
            return serviceConfiguration.get("configValue")
                    .map(value -> value + suffix)
                    .orElseThrow(() -> new RuntimeException("Configuration Value Not Found"));
        }
    }

    public static class FailingFactory implements ServiceFactory<String> {
        @Override
        public String create(Environment environment, Configuration serviceConfiguration) {
            throw new RuntimeException("I am testing the error handling");
        }
    }
}