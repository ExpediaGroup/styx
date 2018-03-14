package com.hotels.styx.infrastructure.configuration.yaml;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 *
 */
public class YamlConfigurationFormatTest {
    @Test
    public void canResolvePlaceholdersInInclude() throws IOException {
        Map<String, String> overrides = ImmutableMap.of("CONFIG_LOCATION", "some_location");

        String yaml = "${CONFIG_LOCATION:classpath:}/conf/environment/default.yaml";

        String resolved = YamlConfigurationFormat.YAML.resolvePlaceholdersInText(yaml, overrides);

        assertThat(resolved, is("some_location/conf/environment/default.yaml"));
    }
}