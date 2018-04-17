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
package com.hotels.styx.config.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.common.Joiners;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.hotels.styx.config.validator.ObjectValidator.newDocument;
import static com.hotels.styx.config.validator.ObjectValidator.pass;
import static com.hotels.styx.config.validator.ObjectValidator.schema;
import static com.hotels.styx.config.validator.Schema.Field.bool;
import static com.hotels.styx.config.validator.Schema.Field.field;
import static com.hotels.styx.config.validator.Schema.Field.integer;
import static com.hotels.styx.config.validator.Schema.Field.list;
import static com.hotels.styx.config.validator.Schema.Field.object;
import static com.hotels.styx.config.validator.Schema.Field.string;
import static com.hotels.styx.config.validator.Schema.Field.union;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ObjectValidatorTest {
    private final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);

    @Test
    public void validatesElementaryTypes() throws Exception {

        boolean result = newDocument()
                .rootSchema(schema("")
                        .field("root", object(
                                schema()
                                        .field("myInt", integer())
                                        .field("myBool", bool())
                                        .field("myString", string())
                        )))
                .build()
                .validateObject(YAML_MAPPER.readTree(
                        "root: \n"
                                + "  myInt: 5 \n"
                                + "  myBool: true \n"
                                + "  myString: styx\n"));
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Missing a mandatory field 'root.surname'")
    public void ensuresAllMandatoryFieldsArePresent() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "root: \n"
                        + "  name: John \n"
                        + "  age: 5\n");

        boolean result = newDocument()
                .rootSchema(schema("")
                        .field("root", object(
                                schema("root")
                                        .field("name", string())
                                        .field("surname", string())
                                        .field("age", integer())
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test
    public void optionalFieldsDoesntHaveToBePresent() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "root: \n"
                        + "  name: John \n"
                        + "  age: 5\n");

        boolean result = newDocument()
                .rootSchema(schema("")
                        .field("root", object(
                                schema("root")
                                        .field("name", string())
                                        .optional("favouriteFood", string())
                                        .field("age", integer())
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'root.favouriteFood' should be STRING, but it is INTEGER")
    public void verifiesOptionalFields() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "root: \n"
                        + "  name: John \n"
                        + "  favouriteFood: 43 \n"
                        + "  age: 5\n");

        boolean result = newDocument()
                .rootSchema(schema("")
                        .field("root", object(
                                schema("root")
                                        .field("name", string())
                                        .optional("favouriteFood", string())
                                        .field("age", integer())
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field: 'root.xyxz'")
    public void ensuresNoExtraFieldsPresent() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "root: \n"
                        + "  name: John \n"
                        + "  surname: Doe \n"
                        + "  age: 5\n"
                        + "  xyxz: 'not supposed to be here'\n");

        boolean result = newDocument()
                .rootSchema(schema("")
                        .field("root", object(
                                schema("foo")
                                        .field("name", string())
                                        .field("surname", string())
                                        .field("age", integer())
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'root.myInt' should be INTEGER, but it is STRING")
    public void checksIntegerFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "root: \n"
                        + "  myInt: 'y' \n");

        boolean result = newDocument()
                .rootSchema(schema("")
                        .field("root", object(
                                schema("root")
                                        .field("myInt", integer())
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'root.myString' should be STRING, but it is INTEGER")
    public void checksStringFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "root: \n"
                        + "  myString: 5.0 \n");

        boolean result = newDocument()
                .rootSchema(schema("")
                        .field("root", object(
                                schema("foo")
                                        .field("myString", string())
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'root.myBool' should be BOOLEAN, but it is INTEGER")
    public void checksBoolFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "root: \n"
                        + "  myBool: 5.0 \n");

        boolean result = newDocument()
                .rootSchema(schema("")
                        .field("root", object(
                                schema("foo")
                                        .field("myBool", bool())
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected list element type. Field 'parent.myList\\[1\\]' should be STRING, but it is INTEGER")
    public void checksListsOfElementaryTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  myList: \n"
                        + "   - b \n"
                        + "   - 5 \n"
        );

        boolean result = newDocument()
                .rootSchema(schema("")
                        .field("parent", object(
                                schema("child")
                                        .field("myList", list(string()))
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected list element type. Field 'parent.myList\\[0\\]' should be INTEGER, but it is STRING")
    public void checksListsOfElementaryTypes_wrongIntegerType() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  myList: \n"
                        + "   - b \n"
                        + "   - 5 \n"
        );

        boolean result = newDocument()
                .rootSchema(schema("")
                        .field("parent", object(
                                schema("child")
                                        .field("myList", list(integer()))
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'parent.myList\\[1\\].x' should be INTEGER, but it is STRING")
    public void checksListsOfSubObjects() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  myList: \n"
                        + "   - x: 0 \n"
                        + "     y: 0 \n"
                        + "   - x: a \n"
                        + "     y: 2 \n"
        );

        boolean result = newDocument()
                .subSchema("SubObjectSchema",
                        schema("SubObjectSchema")
                                .field("x", integer())
                                .field("y", integer()))
                .rootSchema(schema("")
                        .field("parent", object(
                                schema("child")
                                        .field("myList", list(object("SubObjectSchema")))
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected list element type. Field 'parent.myList\\[1\\]' should be OBJECT \\('SubObjectSchema'\\), but it is STRING")
    public void checksListsOfSubObjects_shouldBeSubobjectButIsString() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  myList: \n"
                        + "   - x: 0 \n"
                        + "     y: 0 \n"
                        + "   - 'zz' \n"
        );

        boolean result = newDocument()
                .subSchema("SubObjectSchema",
                        schema("SubObjectSchema")
                                .field("x", integer())
                                .field("y", integer()))
                .rootSchema(schema("")
                        .field("parent", object(
                                schema("child")
                                        .field("myList", list(object("SubObjectSchema")))
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }


    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'parent.myChild' should be OBJECT \\('child'\\), but it is INTEGER")
    public void checksSubObjectFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  myChild: 5.0 \n");

        boolean result = newDocument()
                .rootSchema(schema("")
                        .field("parent", object(
                                schema("child")
                                        .field("myChild", object(
                                                schema("child")
                                                        .field("age", integer())
                                        ))
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test
    public void validatesSubobjects() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "person: \n"
                        + "  details: \n"
                        + "    name: John \n"
                        + "    surname: Doe \n"
                        + "    age: 5\n"
                        + "  id: '005-001-006'\n")
                .get("person");

        boolean result = newDocument()
                .rootSchema(schema("person")
                        .field("id", string())
                        .field("details", object(
                                schema("personalDetails")
                                        .field("name", string())
                                        .field("surname", string())
                                        .field("age", integer())
                        )))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test
    public void reusesObjectSchemas() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "httpPipeline: \n"
                        + "  pipeline: 'interceptorsList'\n"
                        + "  handler: 'handlerName'\n"
                        + "plugins:\n"
                        + "  all: 'x, y z'\n");

        ObjectValidator validator = newDocument()
                .subSchema("HttpPipeline", schema()
                        .field("pipeline", string())
                        .field("handler", string())
                )
                .subSchema("PluginsList", schema()
                        .field("all", string())
                )
                .rootSchema(schema("")
                        .field("httpPipeline", object("HttpPipeline"))
                        .field("plugins", object("PluginsList")))
                .build();

        assertThat(validator.validateObject(rootObject), is(true));
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "No schema configured for lazy object reference 'notExists'")
    public void documentBuilderEnsuresNamedSchemaReferences() throws Exception {
        newDocument()
                .subSchema("PluginsList", schema()
                        .field("all", string())
                )
                .rootSchema(schema("")
                        .field("httpPipeline", object("notExists"))
                        .field("plugins", object("PluginsList")))
                .build();
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "No schema configured for lazy object reference 'notExists'")
    public void documentBuilderEnsuresNamedSchemaReferencesAreValidFromSubobjects() throws Exception {
        newDocument()
                .subSchema("PluginsList", schema()
                        .field("all", string()))
                .rootSchema(schema("")
                        .field("parent", object(
                                schema("subobject")
                                        .field("httpPipeline", object("notExists"))
                        ))
                        .field("plugins", object("PluginsList")))
                .build();
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "No schema configured for lazy object reference 'non-existing-HandlerConfig'")
    public void documentBuilderEnsuresNamedSchemaReferencesAreValidFromSubSchemas() throws Exception {
        newDocument()
                .subSchema("HttpPipeline", schema()
                        .field("handlers", object("non-existing-HandlerConfig"))

                )
                .subSchema("PluginsList", schema()
                        .field("all", string())
                )
                .rootSchema(schema("")
                        .field("plugins", object("PluginsList"))
                        .field("httpPipeline", object("HttpPipeline")))
                .build();
    }

    @Test
    public void validatesDiscriminatedUnions() throws Exception {

        ObjectValidator validator = newDocument()
                .subSchema("ProxyTo", schema()
                        .field("id", string())
                        .field("destination", string())
                )
                .subSchema("Redirection", schema()
                        .field("status", integer())
                        .field("location", string())
                )
                .rootSchema(schema("")
                        .field("httpPipeline", object(schema()
                                .field("type", string())
                                .field("config", union("type"))
                        )))
                .build();


        boolean outcome1 = validator.validateObject(
                YAML_MAPPER.readTree(
                        "httpPipeline: \n"
                                + "  type: 'ProxyTo'\n"
                                + "  config:\n"
                                + "    id: 'local-01'\n"
                                + "    destination: 'localhost:8080'\n"
                ));
        assertThat(outcome1, is(true));

        boolean outcome2 = validator.validateObject(
                YAML_MAPPER.readTree(
                        "httpPipeline: \n"
                                + "  type: 'Redirection'\n"
                                + "  config:\n"
                                + "    status: 301\n"
                                + "    location: /new/location\n"
                ));
        assertThat(outcome2, is(true));

    }

    @Test
    public void ignoresSubobjects() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  pass: \n"
                        + "    x: 5\n"
                        + "    y: 6\n"
        );

        ObjectValidator validator = newDocument()
                .rootSchema(schema()
                        .field("parent", object(schema()
                                .field("pass", object(pass()))
                        )))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }


    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Schema constraint failed. At least one of \\('http', 'https'\\) must be present.")
    public void requiresAtLeastOneOf() throws Exception {
        JsonNode first = YAML_MAPPER.readTree(
                "connectors: \n"
                        + "  http: 8080\n"
        );

        JsonNode second = YAML_MAPPER.readTree(
                "connectors: \n"
                        + "  https: 8443\n"
        );

        JsonNode both = YAML_MAPPER.readTree(
                "connectors: \n"
                        + "  http: 8080\n"
                        + "  https: 8443\n"
        );

        JsonNode neither = YAML_MAPPER.readTree(
                "connectors: \n"
                        + "  x: 8080\n"
        );

        ObjectValidator validator = newDocument()
                .rootSchema(schema()
                        .field("connectors", object(schema()
                                .optional("x", integer())
                                .atLeastOne(
                                        field("http", integer()),
                                        field("https", integer())
                                )
                        )))
                .build();


        assertThat(validator.validateObject(first), is(true));
        assertThat(validator.validateObject(second), is(true));
        assertThat(validator.validateObject(both), is(true));
        validator.validateObject(neither);
    }

    private JsonNode yamlFrom(Resource resource) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(resource.absolutePath()));
        String yaml = Joiners.JOINER_ON_NEW_LINE.join(lines);

        return YAML_MAPPER.readTree(yaml);
    }
}