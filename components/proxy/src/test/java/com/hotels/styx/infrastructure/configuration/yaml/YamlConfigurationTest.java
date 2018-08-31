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
package com.hotels.styx.infrastructure.configuration.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.infrastructure.configuration.ConfigurationParser;
import com.hotels.styx.support.matchers.IsOptional;
import com.hotels.styx.support.matchers.MapMatcher;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Objects.toStringHelper;
import static com.hotels.styx.infrastructure.configuration.ConfigurationSource.configSource;
import static com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat.YAML;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.support.matchers.IsOptional.matches;
import static com.hotels.styx.support.matchers.MapMatcher.isMap;
import static io.netty.util.CharsetUtil.UTF_8;
import static java.io.File.createTempFile;
import static java.lang.String.format;
import static java.nio.file.Files.write;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

public class YamlConfigurationTest {
    private final String yaml = "" +
            "simpleProperty: \"simpleValue\"\n" +
            "jvmRouteName: \"${jvm.route:noJvmRouteSet}\"\n" +
            "proxy:\n" +
            "  connectors:\n" +
            "  - type: http\n" +
            "    port: 8080\n" +
            "metrics:\n" +
            "  graphite:\n" +
            "    enabled: true\n" +
            "    host: \"data.internal.com\"\n" +
            "    port: 2003\n" +
            "    intervalMillis: 5000\n" +
            "propWithPlaceholder: ${my.substitute}\n" +
            "my:\n" +
            "  substitute: placeholderValue\n";

    private final YamlConfiguration yamlConfig = config(yaml);

    private static YamlConfiguration config(String yaml) {
        return new ConfigurationParser.Builder<YamlConfiguration>()
                .format(YAML)
                .overrides(emptyMap())
                .build()
                .parse(configSource(yaml));
    }

    private static YamlConfiguration config(String yaml, Map<String, String> overrides) {
        return new ConfigurationParser.Builder<YamlConfiguration>()
                .format(YAML)
                .overrides(overrides)
                .build()
                .parse(configSource(yaml));
    }

    @Test
    public void getsProperty() {
        assertThat(yamlConfig.get("simpleProperty", String.class), isValue("simpleValue"));
    }

    @Test
    public void getsNestedProperties() {
        assertThat(yamlConfig.get("metrics.graphite.host", String.class), isValue("data.internal.com"));
    }

    @Test
    public void resolvesPlaceholderFromYaml() {
        assertThat(yamlConfig.get("propWithPlaceholder", String.class), isValue("placeholderValue"));
    }

    @Test
    public void usesDefaultIfPlaceholderCannotBeResolved() {
        assertThat(yamlConfig.get("jvmRouteName", String.class), isValue("noJvmRouteSet"));
    }

    @Test
    public void resolvesPlaceholderFromSystemProperty() {
        YamlConfiguration yamlConfig = config(yaml, singletonMap("jvm.route", "staging"));

        assertThat(yamlConfig.get("jvmRouteName", String.class), isValue("staging"));
    }

    @Test
    public void nonTextValuesCanBeSubstitutes() {
        String yaml = "" +
                "prop1: ${prop2}\n" +
                "prop2: 1234\n";

        YamlConfiguration yamlConfig = config(yaml);

        assertThat(yamlConfig.get("prop1", Integer.class), isValue(1234));
    }

    @Test
    public void getsAndConvertsTypes() {
        assertThat(yamlConfig.get("metrics.graphite.port", Integer.class), isValue(2003));
    }

    @Test
    public void getsListItems() {
        assertThat(yamlConfig.get("proxy.connectors[0].port", Integer.class), isValue(8080));
    }

    @Test
    public void getsPrimitiveListItems() {
        String yaml = "" +
                "proxy:\n" +
                "    supportedProtocols:\n" +
                "        - http\n" +
                "        - https";

        YamlConfiguration yamlConfig = config(yaml);

        assertThat(yamlConfig.get("proxy.supportedProtocols[0]", String.class), isValue("http"));
        assertThat(yamlConfig.get("proxy.supportedProtocols[1]", String.class), isValue("https"));
    }

    @Test
    public void getsListWithinListItems() {
        String yaml = "" +
                "list:\n" +
                "-\n" +
                "  - nested1\n" +
                "  - nested2\n";

        YamlConfiguration yamlConfig = config(yaml);

        assertThat(yamlConfig.get("list[0][0]", String.class), isValue("nested1"));
        assertThat(yamlConfig.get("list[0][1]", String.class), isValue("nested2"));
    }

    @Test
    public void getsAsMap() {
        Map expected = ImmutableMap.of(
                "type", "http",
                "port", 8080);

        assertThat(yamlConfig.get("proxy.connectors[0]", Map.class), matches(MapMatcher.isMap(expected)));
    }

    @Test
    public void getsLists() {
        Map expected = ImmutableMap.of(
                "type", "http",
                "port", 8080);

        assertThat(yamlConfig.get("proxy.connectors", List.class), matches(contains(isMap(expected))));

    }

    @Test
    public void getsAndConvertsToAnnotatedClass() {
        assertThat(yamlConfig.get("metrics.graphite", StubGraphiteConfig.class), isValue(new StubGraphiteConfig("data.internal.com", 2003)));
    }

    @Test
    public void doesNotGetNonOverridingSystemProperty() {
        YamlConfiguration yamlConfig = config(yaml, singletonMap("foo", "bar"));

        assertThat(yamlConfig.get("foo", String.class), isAbsent());
    }

    @Test
    public void systemPropertyOverridesSimpleProperty() {
        YamlConfiguration yamlConfig = config(yaml, singletonMap("simpleProperty", "foo"));

        assertThat(yamlConfig.get("simpleProperty", String.class), isValue("foo"));
    }

    @Test
    public void systemPropertyOverridesCanBeConvertedToNonStringTypes() {
        YamlConfiguration yamlConfig = config(yaml, singletonMap("simpleProperty", "1234"));

        assertThat(yamlConfig.get("simpleProperty", Integer.class), isValue(1234));
    }

    @Test
    public void systemPropertyOverridesPropertyInAnnotatedClass() {
        YamlConfiguration yamlConfig = config(yaml, singletonMap("metrics.graphite.host", "example.org"));

        assertThat(yamlConfig.get("metrics.graphite", StubGraphiteConfig.class), isValue(new StubGraphiteConfig("example.org", 2003)));
    }

    @Test
    public void systemPropertyOverridesPropertyInAnnotatedClassEvenIfTypeIsNotString() {
        YamlConfiguration yamlConfig = config(yaml, singletonMap("metrics.graphite.port", "1234"));

        assertThat(yamlConfig.get("metrics.graphite", StubGraphiteConfig.class), isValue(new StubGraphiteConfig("data.internal.com", 1234)));
    }

    @Test
    public void systemPropertyOverridesComplexProperty() {
        YamlConfiguration yamlConfig = config(yaml, singletonMap("proxy.connectors[0].port", "5555"));

        assertThat(yamlConfig.get("proxy.connectors[0].port", Integer.class), isValue(5555));
    }

    @Test
    public void systemPropertiesCannotAddStructuresNotPresentInOriginalYaml() {
        String yaml = "foo: bar\n";

        YamlConfiguration yamlConfig = config(yaml, ImmutableMap.of(
                "metrics.graphite.host", "data.internal.com",
                "metrics.graphite.port", "2003"));

        assertThat(yamlConfig.get("metrics.graphite", StubGraphiteConfig.class), isAbsent());
    }

    @Test
    public void systemPropertyDoesNotOverridesStructuresNotPresentInOriginalYaml() {
        String yaml = "" +
                "proxy.connectors[0].port: 5555\n";

        YamlConfiguration yamlConfig = config(yaml, singletonMap("foo", "bar"));

        assertThat(yamlConfig.get("foo"), isAbsent());
    }

    @Test
    public void systemPropertyDoesNotOverridesListWithinListItemsNotPresentInOriginalYaml() {
        String yaml = "" +
                "foo: 1\n";

        YamlConfiguration yamlConfig = config(yaml, ImmutableMap.of(
                "list[0][0]", "nested1",
                "list[0][1]", "nested2"));

        assertThat(yamlConfig.get("list[0][0]", String.class), isAbsent());
        assertThat(yamlConfig.get("list[0][1]", String.class), isAbsent());
    }

    @Test
    public void systemPropertyWithInvalidPathHasNoEffect() {
        YamlConfiguration yamlConfig = config(yaml, singletonMap("metrics.graphite[0]", "example.org"));

        assertThat(yamlConfig.get("metrics.graphite", StubGraphiteConfig.class), isValue(new StubGraphiteConfig("data.internal.com", 2003)));
    }

    @Test
    public void nonOverridingSystemPropertiesDoNotHaveToFollowNamingRules() {
        config(yaml, ImmutableMap.of(
                "java.vendor.url", "http://java.oracle.com/",
                "java.vendor.url.bug", "http://bugreport.sun.com/bugreport/"
        ));

        // No exceptions expected
    }

    @Test
    public void canIncludeFiles() throws Exception {
        String yaml = "" +
                "include: %s\n" +
                "metrics:\n" +
                "  graphite:\n" +
                "    port: 2004\n";

        String include = "" +
                "metrics:\n" +
                "  graphite:\n" +
                "    port: 2003\n" +
                "    intervalMillis: 5000\n";

        testYamlWithInclude(yaml, include, yamlConfig -> {
            assertThat(yamlConfig.get("metrics.graphite.intervalMillis", Integer.class), isValue(5000));
            assertThat(yamlConfig.get("metrics.graphite.port", Integer.class), isValue(2004));
        });
    }

    @Test
    public void overwritesArraysInInclude() throws Exception {
        String yaml = "" +
                "include: %s\n" +
                "array:\n" +
                "- \"right1\"\n" +
                "- \"right2\"\n";

        String include = "" +
                "array:\n" +
                "- \"wrong1\"\n" +
                "- \"wrong2\"\n";

        testYamlWithInclude(yaml, include, yamlConfig ->
                assertThat(yamlConfig.get("array", List.class), IsOptional.isIterable(contains("right1", "right2"))));
    }

    @Test
    public void resolvesPlaceholdersInIncludeFromMainYaml() throws Exception {
        String yaml = "" +
                "include: %s\n" +
                "resolution: \"resolved\"\n";

        String include = "" +
                "myprop: \"${resolution}\"\n";

        testYamlWithInclude(yaml, include, yamlConfig ->
                assertThat(yamlConfig.get("myprop", String.class), isValue("resolved")));
    }

    @Test
    public void canSubstituteArrayElements() {
        String yaml = "" +
                "array:\n" +
                "- ${element1}\n" +
                "- ${element2}\n" +
                "- ${element3:gamma}\n" +
                "- ${element4}\n";

        YamlConfiguration yamlConfig = config(yaml, ImmutableMap.of("element1", "alpha", "element2", "beta", "element4", "delta"));

        assertThat(yamlConfig.get("array[0]"), isValue("alpha"));
        assertThat(yamlConfig.get("array[1]"), isValue("beta"));
        assertThat(yamlConfig.get("array[2]"), isValue("gamma"));
        assertThat(yamlConfig.get("array[3]"), isValue("delta"));
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Unresolved placeholders: \\[\\$\\{cannotResolveMe\\} in bar=abc \\$\\{cannotResolveMe\\} xyz\\]", enabled = false)
    public void throwsExceptionIfPlaceholdersCannotBeResolved() {
        String yaml = "" +
                "foo: ok\n" +
                "bar: abc ${cannotResolveMe} xyz\n";

        config(yaml);
    }

    @Test
    public void resolvesPlaceholdersInMainYamlFromInclude() throws Exception {
        String yaml = "" +
                "include: %s\n" +
                "myprop: \"${resolution}\"\n";

        String include = "" +
                "resolution: \"resolved\"\n";

        testYamlWithInclude(yaml, include, yamlConfig ->
                assertThat(yamlConfig.get("myprop", String.class), isValue("resolved")));
    }

    @Test
    public void mainYamlOverridesIncludedYaml() throws Exception {
        String yaml = "" +
                "include: %s\n" +
                "foo: main\n";

        String include = "" +
                "foo: included\n";

        testYamlWithInclude(yaml, include, yamlConfig ->
                assertThat(yamlConfig.get("foo", String.class), isValue("main")));
    }

    @Test
    public void overridesPropertiesWithReplacedPlaceholders() throws Exception {
        Map<String, String> systemProperties = ImmutableMap.of("FOO", "production1");

        String yaml = "" +
                "include: %s\n" +
                "foo: \"\"\n";

        String include = "" +
                "foo: ${FOO:}-\n" +
                "domain: de.${foo}example.com\n";

        File file = createTestFile();

        try {
            writeFile(file, include);

            String yamlWithResolvedInclude = format(yaml, file);

            YamlConfiguration yamlConfig = config(yamlWithResolvedInclude, systemProperties);

            assertThat(yamlConfig.get("foo", String.class), isValue(""));
            assertThat(yamlConfig.get("domain", String.class), isValue("de.example.com"));
        } finally {
            deleteFile(file);
        }
    }

    @Test
    public void includePlaceholdersAreResolved() throws Exception {
        Map<String, String> systemProperties = ImmutableMap.of(
                "FOO", "production1",
                "LOCATION", "");

        String yaml = "" +
                "include: ${LOCATION}%s\n" +
                "foo: \"\"\n";

        String include = "" +
                "foo: ${FOO:}-\n" +
                "domain: de.${foo}example.com\n";

        File file = createTestFile();

        try {
            writeFile(file, include);

            String yamlWithResolvedInclude = format(yaml, file);

            YamlConfiguration yamlConfig = config(yamlWithResolvedInclude, systemProperties);

            assertThat(yamlConfig.get("foo", String.class), isValue(""));
            assertThat(yamlConfig.get("domain", String.class), isValue("de.example.com"));
        } finally {
            deleteFile(file);
        }
    }

    @Test
    public void supportsMultipleIncludes() throws Exception {
        String yaml2 = "" +
                "include: %s\n" +
                "onlyIn2: alpha\n" +
                "override1With2: beta\n" +
                "override0With2: gamma\n" +
                "override0With1Then2: delta\n";

        String yaml1 = "" +
                "include: %s\n" +
                "onlyIn1: abc\n" +
                "override1With2: def\n" +
                "override0With1: ghi\n" +
                "override0With1Then2: jkl\n";

        String yaml0 = "" +
                "onlyIn0: foo\n" +
                "override0With1: bar\n" +
                "override0With2: baz\n" +
                "override0With1Then2: hw\n";

        File[] testFiles = createTestFiles(2);

        yaml1 = format(yaml1, testFiles[0]);
        yaml2 = format(yaml2, testFiles[1]);

        try {
            writeFile(testFiles[0], yaml0);
            writeFile(testFiles[1], yaml1);

            YamlConfiguration yamlConfig = config(yaml2);

            assertThat(yamlConfig.get("onlyIn0"), isValue("foo"));
            assertThat(yamlConfig.get("onlyIn1"), isValue("abc"));
            assertThat(yamlConfig.get("onlyIn2"), isValue("alpha"));


            assertThat(yamlConfig.get("override0With1"), isValue("ghi"));
            assertThat(yamlConfig.get("override1With2"), isValue("beta"));
            assertThat(yamlConfig.get("override0With2"), isValue("gamma"));

            assertThat(yamlConfig.get("override0With1Then2"), isValue("delta"));
        } finally {
            deleteFiles(testFiles);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Unresolved placeholders: \\[\\$\\{baz\\} in unresolve0=\\$\\{baz\\}, \\$\\{bar\\} in unresolve1=\\$\\{bar\\}, \\$\\{foo\\} in unresolve2=\\$\\{foo\\}\\]")
    public void listsUnresolvedPlaceholdersFromMultipleIncludes() throws Exception {
        String yaml2 = "" +
                "include: %s\n" +
                "unresolve2: ${foo}\n";

        String yaml1 = "" +
                "include: %s\n" +
                "unresolve1: ${bar}\n";

        String yaml0 = "" +
                "unresolve0: ${baz}\n";

        File[] testFiles = createTestFiles(2);

        yaml1 = format(yaml1, testFiles[0]);
        yaml2 = format(yaml2, testFiles[1]);

        try {
            writeFile(testFiles[0], yaml0);
            writeFile(testFiles[1], yaml1);

            new ConfigurationParser.Builder<YamlConfiguration>()
                    .format(YAML)
                    .overrides(emptyMap())
                    .build()
                    .parse(configSource(yaml2));
        } finally {
            deleteFiles(testFiles);
        }
    }

    private static void writeFile(File file, String string) throws IOException {
        write(file.toPath(), string.getBytes(UTF_8));
    }

    private static File[] createTestFiles(int number) throws IOException {
        File[] files = new File[number];

        try {
            for (int i = 0; i < number; i++) {
                files[i] = createTempFile("pre", "suf");
            }

            return files;
        } catch (IOException e) {
            deleteFiles(files);
            throw e;
        }
    }

    private static File createTestFile() throws IOException {
        return createTestFiles(1)[0];
    }

    private static void deleteFiles(File... files) {
        for (File file : files) {
            if (file != null) {
                deleteFile(file);
            }
        }
    }

    private static void deleteFile(File file) {
        boolean deleted = file.delete();
        assertThat("temp file " + file + "not deleted", deleted, is(true));
    }

    private static void testYamlWithInclude(String yaml, String include, Callback callback) throws Exception {
        File file = createTestFile();

        try {
            writeFile(file, include);

            String yamlWithResolvedInclude = format(yaml, file);

            callback.execute(config(yamlWithResolvedInclude));
        } finally {
            deleteFile(file);
        }
    }

    interface Callback {
        void execute(YamlConfiguration yamlConfig) throws Exception;
    }

    static class StubGraphiteConfig {
        private final String host;
        private final Integer port;

        StubGraphiteConfig(
                @JsonProperty("host") String host,
                @JsonProperty("port") Integer port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            StubGraphiteConfig that = (StubGraphiteConfig) o;
            return Objects.equals(host, that.host) &&
                    Objects.equals(port, that.port);
        }

        @Override
        public int hashCode() {
            return Objects.hash(host, port);
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("host", host)
                    .add("port", port)
                    .toString();
        }
    }
}