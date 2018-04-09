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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.infrastructure.configuration.UnresolvedPlaceholder;
import com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.Placeholder;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

public class PlaceholderResolverTest {
    private static final Map<String, String> NO_EXTERNAL_PROPERTIES = emptyMap();

    @Test
    public void resolvesPropertyFromExternalProperties() throws IOException {
        Map<String, String> externalProperties = singletonMap("CONFIG_LOCATION", "foo");

        JsonNode root = parseYaml("{ \"include\" : \"${CONFIG_LOCATION:classpath:}/conf/environment/default.yaml\" }");

        PlaceholderResolver.resolvePlaceholders(root, externalProperties);

        assertThat(root.get("include").textValue(), is("foo/conf/environment/default.yaml"));
    }

    @Test
    public void resolvesPropertyFromOtherField() throws IOException {
        JsonNode root = parseYaml("{ " +
                "\"include\" : \"${CONFIG_LOCATION:classpath:}/conf/environment/default.yaml\"," +
                "\"CONFIG_LOCATION\" : \"foo\"" +
                " }");

        PlaceholderResolver.resolvePlaceholders(root, NO_EXTERNAL_PROPERTIES);

        assertThat(root.get("include").textValue(), is("foo/conf/environment/default.yaml"));
    }

    @Test
    public void resolvesPropertyFromOtherFieldsWhichAreDependentOnPlaceholdersThemselves() throws IOException {
        JsonNode root = parseYaml("{ " +
                "\"include\" : \"${CONFIG_LOCATION:classpath:}/conf/environment/default.yaml\"," +
                "\"CONFIG_LOCATION\" : \"${FOO}\"," +
                "\"FOO\" : \"foo\"" +
                " }");

        PlaceholderResolver.resolvePlaceholders(root, NO_EXTERNAL_PROPERTIES);

        assertThat(root.get("include").textValue(), is("foo/conf/environment/default.yaml"));
    }

    @Test
    public void resolvesPropertyWithNesting() throws IOException {
        JsonNode root = parseYaml("{ " +
                "\"include\" : \"${parent.CONFIG_LOCATION:classpath:}/conf/environment/default.yaml\"," +
                "\"parent\" : {" +
                "\"CONFIG_LOCATION\" : \"foo\"" +
                " }" +
                " }");

        PlaceholderResolver.resolvePlaceholders(root, NO_EXTERNAL_PROPERTIES);

        assertThat(root.get("include").textValue(), is("foo/conf/environment/default.yaml"));
    }

    @Test
    public void usesDefaultWhenPropertyCannotBeResolved() throws IOException {
        JsonNode root = parseYaml("{ " +
                "\"include\" : \"${CONFIG_LOCATION:classpath:}/conf/environment/default.yaml\"," +
                "\"FOO\" : \"foo\"" +
                " }");

        PlaceholderResolver.resolvePlaceholders(root, NO_EXTERNAL_PROPERTIES);

        assertThat(root.get("include").textValue(), is("classpath:/conf/environment/default.yaml"));
    }

    @Test
    public void throwExceptionWhenPropertyCannotBeResolvedAndNoDefaultIsProvided() throws IOException {
        JsonNode root = parseYaml("{ " +
                "\"include\" : \"${CONFIG_LOCATION}/conf/environment/default.yaml\"," +
                "\"FOO\" : \"foo\"" +
                " }");

        Collection<UnresolvedPlaceholder> unresolvedPlaceholders = PlaceholderResolver.resolvePlaceholders(root, NO_EXTERNAL_PROPERTIES);

        assertThat(unresolvedPlaceholders.toString(), is("[${CONFIG_LOCATION} in include=${CONFIG_LOCATION}/conf/environment/default.yaml]"));
    }

    @Test
    public void resolvesMultiplePropertiesInOneField() throws IOException {
        JsonNode root = parseYaml("{ " +
                "\"include\" : \"${CONFIG_LOCATION:classpath:}/conf/environment/${NAME}.yaml\"," +
                "\"CONFIG_LOCATION\" : \"foo\"," +
                "\"NAME\" : \"bar\"" +
                " }");

        PlaceholderResolver.resolvePlaceholders(root, NO_EXTERNAL_PROPERTIES);

        assertThat(root.get("include").textValue(), is("foo/conf/environment/bar.yaml"));
    }

    @Test
    public void extractsPlaceholders() {
        String valueWithPlaceholders = "foo ${with.default:defaultValue} bar ${without.default} ${configLocation:classpath:}foo";

        List<String> placeholders = PlaceholderResolver.extractPlaceholderStrings(valueWithPlaceholders);

        assertThat(placeholders, contains("${with.default:defaultValue}", "${without.default}", "${configLocation:classpath:}"));
    }

    @Test
    public void extractsPlaceholdersWithEmptyDefaults() {
        String valueWithPlaceholders = "foo: \"${FOO:}\"";

        List<String> placeholders = PlaceholderResolver.extractPlaceholderStrings(valueWithPlaceholders);

        assertThat(placeholders, contains("${FOO:}"));
    }

    @Test
    public void extractsPlaceholderNamesAndDefaults() {
        String valueWithPlaceholders = "foo ${with.default:defaultValue} bar ${without.default} ${configLocation:classpath:}foo";

        List<Placeholder> placeholders = PlaceholderResolver.extractPlaceholders(valueWithPlaceholders);

        List<Placeholder> expected = ImmutableList.of(
                new Placeholder("with.default", "defaultValue"),
                new Placeholder("without.default"),
                new Placeholder("configLocation", "classpath:"));

        assertThat(placeholders, is(expected));
    }

    @Test
    public void extractsPlaceholderNamesAndEmptyDefaults() {
        String valueWithPlaceholders = "${FOO:}";

        List<Placeholder> placeholders = PlaceholderResolver.extractPlaceholders(valueWithPlaceholders);

        assertThat(placeholders, is(singletonList(new Placeholder("FOO", ""))));
    }

    @Test
    public void replacesPlaceholder() {
        String original = "foo ${with.default:defaultValue} bar ${without.default}";

        assertThat(PlaceholderResolver.replacePlaceholder(original, "with.default", "replacement"), is("foo replacement bar ${without.default}"));
        assertThat(PlaceholderResolver.replacePlaceholder(original, "without.default", "replacement"), is("foo ${with.default:defaultValue} bar replacement"));
    }

    private static JsonNode parseYaml(String yaml) throws IOException {
        return new ObjectMapper().readTree(yaml);
    }
}