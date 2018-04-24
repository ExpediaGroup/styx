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
package com.hotels.styx.config.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.hotels.styx.config.schema.SchemaDsl.atLeastOne;
import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.integer;
import static com.hotels.styx.config.schema.SchemaDsl.schema;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AtLeastOneFieldPresenceConstraintTest {
    private final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);

    @Test
    public void describesItsBehaviour() {
        AtLeastOneFieldPresenceConstraint constraint = new AtLeastOneFieldPresenceConstraint("http", "https");

        assertThat(constraint.describe(), is("At least one of ('http', 'https') must be present."));
    }

    @Test
    public void passesWhenOneRequiredFieldIsPresent() throws IOException {
        AtLeastOneFieldPresenceConstraint constraint = new AtLeastOneFieldPresenceConstraint("http", "https");
        boolean result = constraint.evaluate(
                schema(
                        field("x", integer()),
                        field("http", integer()),
                        field("https", integer()),
                        atLeastOne("http", "https")),
                jsonNode(
                        "  http: 8080\n"
                ));

        assertThat(result, is(true));
    }

    @Test
    public void allowsSeveralFieldsToBePresent() throws IOException {
        AtLeastOneFieldPresenceConstraint constraint = new AtLeastOneFieldPresenceConstraint("http", "https");
        boolean result = constraint.evaluate(
                schema(
                        field("x", integer()),
                        field("http", integer()),
                        field("https", integer()),
                        atLeastOne("http", "https")),
                jsonNode(
                        "  https: 8443\n"
                                + "  http: 8080\n"
                ));

        assertThat(result, is(true));
    }

    @Test
    public void throwsExceptionWhenNoneRequiredFieldsArePresent() throws IOException {
        AtLeastOneFieldPresenceConstraint constraint = new AtLeastOneFieldPresenceConstraint("http", "https");
        boolean result = constraint.evaluate(
                schema(
                        field("x", integer()),
                        field("http", integer()),
                        field("https", integer()),
                        atLeastOne("http", "https")),
                jsonNode(
                         "  x: 43\n"
                ));

        assertThat(result, is(false));
    }

    private JsonNode jsonNode(String text) throws IOException {
        return YAML_MAPPER.readTree(text);
    }
}