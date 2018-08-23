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
package com.hotels.styx.serviceproviders;

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.AggregatedConfiguration;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicyFactory;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.configuration.ServiceFactory;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.support.api.SimpleEnvironment;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static com.hotels.styx.serviceproviders.ServiceProvision.loadRetryPolicy;
import static com.hotels.styx.serviceproviders.ServiceProvision.loadServices;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.MapMatcher.isMap;
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class ServiceProvisionTest {
    Path FIXTURES_CLASS_PATH = fixturesHome(ServiceProvisionTest.class, "/plugins");

    private final String yaml = "" +
            "my:\n" +
            "  factory:\n" +
            "    enabled: true\n" +
            "    class: " + MyFactory.class.getName() + "\n" +
            "    config:\n" +
            "      stringValue: expectedValue\n" +
            "myRetry:\n" +
            "  factory:\n" +
            "    enabled: true\n" +
            "    class: " + MyRetryFactory.class.getName() + "\n" +
            "    config:\n" +
            "      stringValue: expectedValue\n" +
            "not:\n" +
            "  real:\n" +
            "    class: my.FakeClass\n" +
            "multi:\n" +
            "  factories:\n" +
            "    one:\n" +
            "      enabled: true\n" +
            "      class: " + MyFactory.class.getName() + "\n" +
            "      config:\n" +
            "        stringValue: valueNumber1\n" +
            "    two:\n" +
            "      enabled: true\n" +
            "      class: " + MyFactory.class.getName() + "\n" +
            "      config:\n" +
            "        stringValue: valueNumber2\n" +
            "    three:\n" +
            "      enabled: true\n" +
            "      class: " + MyFactory.class.getName() + "\n" +
            "      config:\n" +
            "        stringValue: valueNumber3\n";

    private final String yamlForServices = "" +
            "my:\n" +
            "  factory:\n" +
            "    enabled: true\n" +
            "    class: " + MyFactory.class.getName() + "\n" +
            "    config:\n" +
            "      stringValue: expectedValue\n" +
            "not:\n" +
            "  real:\n" +
            "    class: my.FakeClass\n" +
            "multi:\n" +
            "  factories:\n" +
            "    backendProvider:\n" +
            "      enabled: true\n" +
            "      class: " + TestBackendServiceProviderFactory.class.getName() + "\n" +
            "      config:\n" +
            "        stringValue: valueNumber1\n" +
            "    routingProvider:\n" +
            "      enabled: true\n" +
            "      class: " + TestRoutingObjectProviderFactory.class.getName() + "\n" +
            "      config:\n" +
            "        stringValue: valueNumber2\n";

    private final String yamlForServiceFactories = "" +
            "multi:\n" +
            "  factories:\n" +
            "    backendProvider:\n" +
            "      factory:\n" +
            "        class: " + TestBackendServiceProviderFactory.class.getName() + "\n" +
            "        classPath: " + FIXTURES_CLASS_PATH.toString() + "\n" +
            "      config:\n" +
            "        stringValue: valueNumber1\n" +
            "    routingProvider:\n" +
            "      factory:\n" +
            "        class: " + TestRoutingObjectProviderFactory.class.getName() + "\n" +
            "        classPath: " + FIXTURES_CLASS_PATH.toString() + "\n" +
            "      config:\n" +
            "          stringValue: valueNumber2\n";



    private final String yamlForMixedServiceFactories = "" +
            "multi:\n" +
            "  factories:\n" +
            "    backendProvider:\n" +
            "      enabled: true\n" +
            "      class: " + TestBackendServiceProviderFactory.class.getName() + "\n" +
            "      config:\n" +
            "        stringValue: valueNumber1\n" +
            "    routingProvider:\n" +
            "      factory:\n" +
            "        class: " + TestRoutingObjectProviderFactory.class.getName() + "\n" +
            "        classPath: " + FIXTURES_CLASS_PATH.toString() + "\n" +
            "      config:\n" +
            "        stringValue: valueNumber2\n";

    private final String mixedDisabledServices = "" +
            "multi:\n" +
            "  factories:\n" +
            "    backendProvider:\n" +
            "      enabled: false\n" +
            "      class: " + TestBackendServiceProviderFactory.class.getName() + "\n" +
            "      config:\n" +
            "        stringValue: valueNumber1\n" +
            "    routingProvider:\n" +
            "      enabled: false\n" +
            "      factory:\n" +
            "        class: " + TestRoutingObjectProviderFactory.class.getName() + "\n" +
            "        classPath: " + FIXTURES_CLASS_PATH.toString() + "\n" +
            "      config:\n" +
            "        stringValue: valueNumber2\n";


    private final Environment environment = environmentWithConfig(yaml);

    @Test
    public void serviceReturnsCorrectlyFromCall() {
        assertThat(loadRetryPolicy(environment.configuration(), environment, "myRetry.factory", RetryPolicy.class).get(), is(instanceOf(RetryPolicy.class)));
    }

    @Test
    public void serviceReturnsEmptyWhenFactoryKeyDoesNotExist() {
        assertThat(loadRetryPolicy(environment.configuration(), environment, "invalid.key", RetryPolicy.class), isAbsent());
    }

    @Test
    public void servicesReturnCorrectlyFromCall() {
        Map<String, String> services = loadServices(environment.configuration(), environment, "multi", String.class);

        assertThat(services, isMap(ImmutableMap.of(
                "one", "valueNumber1",
                "two", "valueNumber2",
                "three", "valueNumber3"
        )));
    }

    @Test
    public void isInstanceWorks() {
        Environment env = environmentWithConfig(yamlForServices);
        Map<String, StyxService> services = loadServices(env.configuration(), env, "multi", StyxService.class);

        assertThat(services.get("backendProvider"), instanceOf(BackendServiceProvider.class));
        assertThat(services.get("routingProvider"), instanceOf(RoutingObjectProvider.class));
    }

    @Test
    public void loadsNewConfigurationFormat() {
        Environment env = environmentWithConfig(yamlForServiceFactories);
        Map<String, StyxService> services = loadServices(env.configuration(), env, "multi", StyxService.class);

        assertThat(services.get("backendProvider"), instanceOf(BackendServiceProvider.class));
        assertThat(services.get("routingProvider"), instanceOf(RoutingObjectProvider.class));
    }


    @Test
    public void loadsFromMixedConfigFormat() {
        Environment env = environmentWithConfig(yamlForMixedServiceFactories);
        Map<String, StyxService> services = loadServices(env.configuration(), env, "multi", StyxService.class);

        assertThat(services.get("backendProvider"), instanceOf(BackendServiceProvider.class));
        assertThat(services.get("routingProvider"), instanceOf(RoutingObjectProvider.class));
    }

    @Test
    public void ignoresDisabledServices() {
        Environment env = environmentWithConfig(mixedDisabledServices);
        Map<String, StyxService> services = loadServices(env.configuration(), env, "multi", StyxService.class);

        assertThat(services.isEmpty(), is(true));
    }


    @Test
    public void servicesReturnEmptyWhenFactoryKeyDoesNotExist() {
        Map<String, String> services = loadServices(environment.configuration(), environment, "invalid.key", String.class);

        assertThat(services, isMap(emptyMap()));
    }

    @Test(expectedExceptions = Exception.class, expectedExceptionsMessageRegExp = "(?s).*No such class 'my.FakeClass'.*")
    public void throwsExceptionWhenClassDoesNotExist() {
        loadRetryPolicy(environment.configuration(), environment, "not.real", RetryPolicy.class);
    }

    @Test(expectedExceptions = ConfigurationException.class,
            expectedExceptionsMessageRegExp = "Unexpected configuration object 'services.factories.backendProvider', Configuration.*'")
    public void throwsExceptionForInvalidServiceFactoryConfig() {
        String config = "" +
                "multi:\n" +
                "  factories:\n" +
                "    backendProvider:\n" +
                "      enabled: false\n" +
                "      config:\n" +
                "        stringValue: valueNumber1\n";

        Environment env = environmentWithConfig(config);
        loadServices(env.configuration(), env, "multi", StyxService.class);
    }

    @Test(expectedExceptions = ConfigurationException.class,
            expectedExceptionsMessageRegExp = "Unexpected configuration object 'services.factories.backendProvider', Configuration.*'")
    public void throwsExceptionForInvalidSpiExtensionFactory() {
        String config = "" +
                "multi:\n" +
                "  factories:\n" +
                "    backendProvider:\n" +
                "      enabled: false\n" +
                "      factory:\n" +
                "        classPath: /path/to/jar\n" +
                "      config:\n" +
                "         attribute: x\n";

        Environment env = environmentWithConfig(config);
        loadServices(env.configuration(), env, "multi", StyxService.class);
    }

    public static class MyRetryFactory implements RetryPolicyFactory {
        @Override
        public RetryPolicy create(Environment environment, Configuration serviceConfiguration) {
            return (context, loadBalancer, lbContext) -> new RetryPolicy.Outcome() {
                @Override
                public long retryIntervalMillis() {
                    return 0;
                }

                @Override
                public Optional<RemoteHost> nextOrigin() {
                    return Optional.empty();
                }

                @Override
                public boolean shouldRetry() {
                    return false;
                }
            };
        }
    }

    public static class MyFactory implements ServiceFactory<String> {
        @Override
        public String create(Environment environment, Configuration serviceConfiguration) {
            return serviceConfiguration.get("stringValue").orElse("VALUE_ABSENT");
        }
    }


    public static class TestBackendServiceProviderFactory implements ServiceFactory<StyxService> {
        @Override
        public StyxService create(Environment environment, Configuration serviceConfiguration) {
            return new TestBackendServiceProvider();
        }
    }


    public static class TestRoutingObjectProviderFactory implements ServiceFactory<StyxService> {
        @Override
        public StyxService create(Environment environment, Configuration serviceConfiguration) {
            return new TestRoutingObjectProvider();
        }
    }

    interface BackendServiceProvider {

    }

    interface RoutingObjectProvider {

    }

    static class TestBackendServiceProvider extends AbstractStyxService implements BackendServiceProvider {
        public TestBackendServiceProvider() {
            super("TestBackendServiceProvider");
        }
    }

    static class TestRoutingObjectProvider extends AbstractStyxService implements RoutingObjectProvider {
        public TestRoutingObjectProvider() {
            super("TestBackendServiceProvider");
        }
    }


    private Environment environmentWithConfig(String yaml) {
        Configuration conf = new AggregatedConfiguration(StyxConfig.fromYaml(yaml));

        return new SimpleEnvironment.Builder()
                .configuration(conf)
                .build();
    }
}