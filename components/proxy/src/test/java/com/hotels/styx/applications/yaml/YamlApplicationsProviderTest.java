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
package com.hotels.styx.applications.yaml;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.RewriteConfig;
import com.hotels.styx.api.extension.service.TlsSettings;
import com.hotels.styx.applications.ApplicationsProvider;
import com.hotels.styx.applications.BackendServices;
import org.hamcrest.CoreMatchers;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.service.BackendService.Protocol.HTTP;
import static com.hotels.styx.api.extension.service.BackendService.Protocol.HTTPS;
import static com.hotels.styx.api.extension.service.Certificate.certificate;
import static com.hotels.styx.api.extension.service.HealthCheckConfig.newHealthCheckConfigBuilder;
import static com.hotels.styx.api.extension.service.StickySessionConfig.newStickySessionConfigBuilder;
import static com.hotels.styx.applications.yaml.YamlApplicationsProvider.loadApplicationsFrom;
import static com.hotels.styx.applications.yaml.YamlApplicationsProvider.loadFromPath;
import static com.hotels.styx.support.ApplicationConfigurationMatcher.anApplication;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

public class YamlApplicationsProviderTest {

    private static final String ORIGINS_FILE = fixturesHome() + "conf/origins/origins-for-https-test.yml";
    private static final String OLD_ORIGINS_FILE = fixturesHome() + "conf/origins/origins-for-https-test-ssl-settings.yml";

    @Test
    public void readsBackendsAppsWithHttpsConfiguration() throws Exception {
        List<BackendService> backends = backendsAsList(loadApplicationsFrom(ORIGINS_FILE));

        BackendService secureApp = backends.get(1);

        assertThat(secureApp.protocol(), is(HTTPS));
        assertThat(secureApp.tlsSettings(), is(Optional.of(
                new TlsSettings.Builder()
                        .authenticate(true)
                        .sslProvider("JDK")
                        .additionalCerts(
                                certificate("my certificate", "/path/to/mycert"),
                                certificate("alt certificatfe", "/path/to/altcert")
                        )
                        .trustStorePath("/path/to/truststore")
                        .trustStorePassword("truststore-123")
                        .build()))
        );
    }

    @Test
    public void readsBackendsAppsWithOldHttpsConfiguration() throws Exception {
        List<BackendService> backends = backendsAsList(loadApplicationsFrom(OLD_ORIGINS_FILE));

        BackendService secureApp = backends.get(1);

        assertThat(secureApp.protocol(), is(HTTPS));
        assertThat(secureApp.tlsSettings(), is(Optional.of(
                new TlsSettings.Builder()
                        .authenticate(true)
                        .sslProvider("JDK")
                        .additionalCerts(
                                certificate("my certificate", "/path/to/mycert"),
                                certificate("alt certificatfe", "/path/to/altcert")
                        )
                        .trustStorePath("/path/to/truststore")
                        .trustStorePassword("truststore-123")
                        .build()))
        );
    }

    @Test
    public void readsBackendAppsWithHttpConfiguration() throws Exception {
        List<BackendService> backends = backendsAsList(loadApplicationsFrom(ORIGINS_FILE));

        BackendService httpApp = backends.get(0);

        assertThat(httpApp.protocol(), is(HTTP));
        assertThat(httpApp.tlsSettings(), is(Optional.empty()));
    }

    @SuppressWarnings("unchecked")
    @Test(dataProvider = "validPaths")
    public void canGetApplications(String path) {
        ApplicationsProvider config = loadFromPath(path);

        StreamSupport.stream(config.get().spliterator(), false)
                .forEach(service -> System.out.println(service.id() + " - " + service.healthCheckConfig()));

        assertThat(config.get(), containsInAnyOrder(
                anApplication()
                        .withName("landing")
                        .withPath("/landing/")
                        .withOrigins(
                                origin("landing", "landing-01", "landinghost1", 9091),
                                origin("landing", "landing-02", "landinghost2", 9092),
                                origin("landing", "landing-03", "landinghost3", 9093))
                        .withConnectTimeout(4000)
                        .withHealthCheckConfig(newHealthCheckConfigBuilder()
                                .uri("/alternative.txt")
                                .interval(5000, MILLISECONDS)
                                .timeout(2000, MILLISECONDS)
                                .healthyThreshold(2)
                                .unhealthyThreshold(2)
                                .build()
                        )
                        .withStickySessionConfig(newStickySessionConfigBuilder()
                                .enabled(true)
                                .timeout(14321, SECONDS)
                                .build()),

                anApplication()
                        .withName("webapp")
                        .withPath("/")
                        .withOrigins(origin("webapp", "webapp-01", "webapphost1", 9094))
                        .withConnectTimeout(3000)
                        .withHealthCheckConfig(newHealthCheckConfigBuilder()
                                .uri("/version.txt")
                                .interval(23456, MILLISECONDS)
                                .timeout(1500, MILLISECONDS)
                                .healthyThreshold(3)
                                .unhealthyThreshold(5)
                                .build()
                        )
                        .withStickySessionConfig(newStickySessionConfigBuilder()
                                .enabled(true)
                                .timeout(43200, SECONDS)
                                .build()),

                anApplication()
                        .withName("shopping")
                        .withPath("/shop/")
                        .withOrigins(origin("shopping", "shopping-01", "shoppinghost1", 9090))
                        .withConnectTimeout(5000)
                        .withMaxConnectionsPerHost(200)
                        .withMaxPendingConnectionsPerHost(250)
                        .withStickySessionConfig(newStickySessionConfigBuilder()
                                .enabled(false)
                                .timeout(43200, SECONDS)
                                .build())
                        .withRewrites(
                                new RewriteConfig("/shop/(.*)", "/$1"),
                                new RewriteConfig("/shop2/(.*)/foobar/(.*)", "/$1/barfoo/$2"))
        ));
    }

    @DataProvider(name = "validPaths")
    private static Object[][] validPaths() {
        return new Object[][]{
                {"classpath:conf/origins/origins-for-configtest.yml"},
                {"classpath:/conf/origins/origins-for-configtest.yml"},
                {filePath("/conf/origins/origins-for-configtest.yml")}
        };
    }

    private static String filePath(String path) {
        return "file:" + YamlApplicationsProviderTest.class.getResource(path).getPath();
    }

    @Test
    public void stickySessionIsDisabledByDefault() {
        ApplicationsProvider config = loadFromPath("classpath:conf/origins/origins-for-configtest.yml");

        BackendService app = applicationFor(config, "shopping");
        assertThat(app.stickySessionConfig().stickySessionEnabled(), CoreMatchers.is(false));
    }

    @Test
    public void stickySessionEnabledWhenYamlStickySessionEnabledIsTrue() {
        ApplicationsProvider config = loadFromPath("classpath:conf/origins/origins-for-configtest.yml");

        BackendService app = applicationFor(config, "webapp");
        assertThat(app.stickySessionConfig().stickySessionEnabled(), CoreMatchers.is(true));
    }

    @Test(expectedExceptions = Exception.class, expectedExceptionsMessageRegExp = "Invalid YAML from classpath:conf/origins/empty-origins-for-configtest.yml: No content to map due to end-of-input\n at \\[Source: .*\\]")
    public void cannotLoadWithNoApplications() throws IOException {
        loadFromPath("classpath:/conf/origins/empty-origins-for-configtest.yml");
    }

    @Test(expectedExceptions = Exception.class, expectedExceptionsMessageRegExp = "Unable to load YAML from.*")
    public void doesNotLoadIfFileDoesNotExist() {
        loadFromPath("/sadiusadasd");
    }

    @Test(expectedExceptions = Exception.class, expectedExceptionsMessageRegExp = "Invalid YAML from classpath:conf/origins/origins-with-syntax-error-for-configtest.yml: Can not deserialize instance of java.util.ArrayList out of VALUE_STRING token\n at \\[Source: .*\\]")
    public void cannotLoadWithSyntaxErrors() throws IOException {
        loadFromPath("classpath:/conf/origins/origins-with-syntax-error-for-configtest.yml");
    }

    private static BackendService applicationFor(ApplicationsProvider provider, String appName) {
        return stream(provider.get().spliterator(), false)
                .filter(app -> app.id().equals(id(appName)))
                .findFirst()
                .get();
    }

    private static Origin origin(String applicationId, String originId, String host, int port) {
        return newOriginBuilder(host, port)
                .applicationId(applicationId)
                .id(originId)
                .build();
    }

    private List<BackendService> backendsAsList(BackendServices apps) {
        return StreamSupport.stream(apps.spliterator(), false).collect(Collectors.toList());
    }
}
