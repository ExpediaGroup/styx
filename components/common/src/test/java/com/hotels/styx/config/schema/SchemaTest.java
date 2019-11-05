/*
  Copyright (C) 2013-2019 Expedia Inc.

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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.hotels.styx.config.schema.SchemaDsl.atLeastOne;
import static com.hotels.styx.config.schema.SchemaDsl.bool;
import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.integer;
import static com.hotels.styx.config.schema.SchemaDsl.list;
import static com.hotels.styx.config.schema.SchemaDsl.map;
import static com.hotels.styx.config.schema.SchemaDsl.object;
import static com.hotels.styx.config.schema.SchemaDsl.opaque;
import static com.hotels.styx.config.schema.SchemaDsl.optional;
import static com.hotels.styx.config.schema.SchemaDsl.schema;
import static com.hotels.styx.config.schema.SchemaDsl.string;
import static com.hotels.styx.config.schema.SchemaDsl.union;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SchemaTest {
    private final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);
    private final Function<String, Schema.FieldType> NO_EXTENSIONS = key -> null;


    @Test
    public void integer_validatesIntegerValues() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(""
                + "  myOkValue: 5 \n"
                + "  myNokValue: true \n");

        integer().validate(ImmutableList.of("myOkValue"), root, root.get("myOkValue"), NO_EXTENSIONS);
        Exception e = assertThrows(SchemaValidationException.class,
                () -> integer().validate(ImmutableList.of("myNokValue"), root, root.get("myNokValue"), NO_EXTENSIONS));
        assertEquals("Unexpected field type. Field 'myNokValue' should be INTEGER, but it is BOOLEAN", e.getMessage());
    }

    @Test
    public void string_validatesStringValues() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(""
                + "  myOkValue: abc \n"
                + "  myNokValue: 34 \n");

        string().validate(ImmutableList.of("myOkValue"), root, root.get("myOkValue"), NO_EXTENSIONS);
        Exception e = assertThrows(SchemaValidationException.class,
                () -> string().validate(ImmutableList.of("myNokValue"), root, root.get("myNokValue"), NO_EXTENSIONS));
        assertEquals("Unexpected field type. Field 'myNokValue' should be STRING, but it is NUMBER", e.getMessage());
    }

    @Test
    public void integer_convertsNumericStringsToNumbers() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(""
                + "  myInt: '5' \n");

        integer().validate(ImmutableList.of("myInt"), root, root.get("myInt"), NO_EXTENSIONS);
    }


    @Test
    public void bool_validatesBooleanValues() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(""
                + "  myOkValue1: true \n"
                + "  myOkValue2: false \n"
                + "  myNokValue: x \n");

        bool().validate(ImmutableList.of("myOkValue1"), root, root.get("myOkValue1"), NO_EXTENSIONS);
        bool().validate(ImmutableList.of("myOkValue2"), root, root.get("myOkValue2"), NO_EXTENSIONS);
        Exception e = assertThrows(SchemaValidationException.class,
                () -> bool().validate(ImmutableList.of("myNokValue"), root, root.get("myNokValue"), NO_EXTENSIONS));
        assertEquals("Unexpected field type. Field 'myNokValue' should be BOOLEAN, but it is STRING", e.getMessage());
    }


    @Test
    public void bool_convertsStringToBooleanValues() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "  myBool_01: 'true' \n"
                + "  myBool_02: 'false' \n"
                + "  myBool_03: 'True' \n"
                + "  myBool_04: 'False' \n"
                + "  myBool_05: 'TRUE' \n"
                + "  myBool_06: 'FALSE' \n"
        );

        bool().validate(ImmutableList.of(), rootObject, rootObject.get("myBool_01"), NO_EXTENSIONS);
        bool().validate(ImmutableList.of(), rootObject, rootObject.get("myBool_02"), NO_EXTENSIONS);
        bool().validate(ImmutableList.of(), rootObject, rootObject.get("myBool_03"), NO_EXTENSIONS);
        bool().validate(ImmutableList.of(), rootObject, rootObject.get("myBool_04"), NO_EXTENSIONS);
        bool().validate(ImmutableList.of(), rootObject, rootObject.get("myBool_05"), NO_EXTENSIONS);
        bool().validate(ImmutableList.of(), rootObject, rootObject.get("myBool_06"), NO_EXTENSIONS);
    }


    @Test
    public void object_AllMandatoryFieldsShouldBePresent() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  name: John \n"
                + "  age: 5\n");

        Exception e = assertThrows(SchemaValidationException.class,
                () -> object(
                        field("root", object(
                                field("name", string()),
                                field("surname", string()),
                                field("age", integer())
                        ))
                ).validate(ImmutableList.of(), rootObject, rootObject, x -> null));
        assertEquals("Missing a mandatory field 'root.surname'", e.getMessage());
    }

    @Test
    public void object_OptionalFieldsDoesntHaveToBePresent() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  name: John \n"
                + "  age: 5\n");

        object(
                field("root", object(
                        field("name", string()),
                        optional("favouriteFood", string()),
                        field("age", integer())
                ))
        ).validate(ImmutableList.of("root"), rootObject, rootObject, NO_EXTENSIONS);
    }


    @Test
    public void object_verifiesOptionalFields() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  name: John \n"
                + "  favouriteFood: 43 \n"
                + "  age: 5\n");

        Exception e = assertThrows(SchemaValidationException.class,
                () -> object(
                        field("root", object(
                                field("name", string()),
                                optional("favouriteFood", string()),
                                field("age", integer())
                        ))
                ).validate(ImmutableList.of(), rootObject, rootObject, NO_EXTENSIONS));
        assertEquals("Unexpected field type. Field 'root.favouriteFood' should be STRING, but it is NUMBER", e.getMessage());
    }

    @Test
    public void object_ensuresNoExtraFieldsPresent() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  name: John \n"
                + "  surname: Doe \n"
                + "  age: 5\n"
                + "  xyxz: 'not supposed to be here'\n");

        Exception e = assertThrows(SchemaValidationException.class,
                () -> object(
                        field("root", object(
                                field("name", string()),
                                field("surname", string()),
                                field("age", integer())
                        ))
                ).validate(ImmutableList.of(), rootObject, rootObject, NO_EXTENSIONS));
        assertEquals("Unexpected field: 'root.xyxz'", e.getMessage());
    }


    @Test
    public void object_checksIntegerFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  myInt: 'y' \n");

        Exception e = assertThrows(SchemaValidationException.class,
                () -> object(
                        field("root", object(
                                field("myInt", integer())
                        ))
                ).validate(ImmutableList.of(), rootObject, rootObject, NO_EXTENSIONS));
        assertEquals("Unexpected field type. Field 'root.myInt' should be INTEGER, but it is STRING", e.getMessage());
    }


    @Test
    public void object_checksStringFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  myString: 5.0 \n");

        Exception e = assertThrows(SchemaValidationException.class,
                () -> object(
                        field("root", object(
                                field("myString", string())
                        ))
                ).validate(ImmutableList.of(), rootObject, rootObject, NO_EXTENSIONS));
        assertEquals("Unexpected field type. Field 'root.myString' should be STRING, but it is NUMBER", e.getMessage());
    }


    @Test
    public void object_checksBoolFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  myBool: 5.0 \n");

        Exception e = assertThrows(SchemaValidationException.class,
                () -> object(
                        field("root", object(
                                field("myBool", bool())
                        ))
                ).validate(ImmutableList.of(), rootObject, rootObject, NO_EXTENSIONS));
        assertEquals("Unexpected field type. Field 'root.myBool' should be BOOLEAN, but it is NUMBER", e.getMessage());
    }


    @Test
    public void object_checksSubObjectFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "  myChild: 5.0 \n");

        Exception e = assertThrows(SchemaValidationException.class,
                () -> object(
                        field("myChild", object(
                                field("age", integer())
                        ))
                ).validate(emptyList(), rootObject, rootObject, NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'myChild' should be OBJECT\\(age\\), but it is NUMBER"));
    }

    @Test
    public void object_validatesSubobjects() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "person: \n"
                + "  details: \n"
                + "    name: John \n"
                + "    surname: Doe \n"
                + "    age: 5\n"
                + "  id: '005-001-006'\n");

        object(
                field("id", string()),
                field("details", object(
                        field("name", string()),
                        field("surname", string()),
                        field("age", integer())
                ))
        ).validate(emptyList(), rootObject, rootObject.get("person"), NO_EXTENSIONS);
    }

    @Test
    public void object_ignoresOpaqueSubobjectValidation() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(""
                + "  opaque: \n"
                + "    x: 5\n"
                + "    y: 6\n"
        );

        object(opaque()).validate(emptyList(), root, root.get("opaque"), NO_EXTENSIONS);
    }

    @Test
    public void object_rejectsInvalidObjects() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: x\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> object(
                        field("parent", object(
                                field("child", string())))
                ).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'parent' should be OBJECT\\(parent\\), but it is STRING"));
    }


    /*
     * Validates related aspects of `atLeastOne` constraint:
     *
     *  1) One required field is present.
     *  2) Another required field is present.
     *  3) Both required fields are present
     *  4) Neither of the required fields are present.
     */
    @Test
    public void object_validatesAtLeastOneConstraintCorrectly() throws Exception {
        JsonNode first = YAML_MAPPER.readTree(""
                + "connectors: \n"
                + "  http: 8080\n"
        );

        JsonNode second = YAML_MAPPER.readTree(""
                + "connectors: \n"
                + "  https: 8443\n"
        );

        JsonNode both = YAML_MAPPER.readTree(""
                + "connectors: \n"
                + "  http: 8080\n"
                + "  https: 8443\n"
        );

        JsonNode neither = YAML_MAPPER.readTree(""
                + "connectors: \n"
                + "  x: 8080\n"
        );

        Schema.FieldType objectType = object(
                field("connectors", object(
                        optional("x", integer()),
                        optional("http", integer()),
                        optional("https", integer()),
                        atLeastOne("http", "https")
                )));

        objectType.validate(ImmutableList.of(), first, first, NO_EXTENSIONS);
        objectType.validate(ImmutableList.of(), second, second, NO_EXTENSIONS);
        objectType.validate(ImmutableList.of(), both, both, NO_EXTENSIONS);
        Exception e = assertThrows(SchemaValidationException.class,
                () -> objectType.validate(ImmutableList.of(), neither, neither, NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Schema constraint failed. At least one of \\('http', 'https'\\) must be present."));
    }

    @Test
    public void list_checksListsOfElementaryTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "  myList: \n"
                + "   - b \n"
                + "   - 5 \n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> list(string()).validate(ImmutableList.of("myList"), rootObject, rootObject.get("myList"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'myList\\[1\\]' should be STRING, but it is NUMBER"));
    }

    @Test
    public void list_checksListsOfElementaryTypes_wrongIntegerType() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "  myList: \n"
                + "   - b \n"
                + "   - 5 \n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> list(integer()).validate(ImmutableList.of("myList"), rootObject, rootObject.get("myList"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'myList\\[0\\]' should be INTEGER, but it is STRING"));
    }

    @Test
    public void list_checksListsOfSubObjects() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "  myList: \n"
                + "   - x: 0 \n"
                + "     y: 0 \n"
                + "   - x: a \n"
                + "     y: 2 \n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> list(object(
                        field("x", integer()),
                        field("y", integer())
                )).validate(ImmutableList.of("myList"), rootObject, rootObject.get("myList"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'myList\\[1\\].x' should be INTEGER, but it is STRING"));
    }

    @Test
    public void list_checksListsOfSubObjects_shouldBeSubobjectButIsString() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "  myList: \n"
                + "   - x: 0 \n"
                + "     y: 0 \n"
                + "   - 'zz' \n"
        );

        Schema.FieldType subObject = object(
                field("x", integer()),
                field("y", integer())
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> list(subObject).validate(ImmutableList.of("myList"), rootObject, rootObject.get("myList"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'myList\\[1\\]' should be OBJECT\\(x, y\\), but it is STRING"));
    }

    @Test
    public void list_expectingListButIsString() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "myList: 'or not'\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> list(integer()).validate(ImmutableList.of("myList"), root, root.get("myList"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'myList' should be LIST\\(INTEGER\\), but it is STRING"));
    }

    @Test
    public void list_expectingListButIsObject() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "myList: \n"
                        + "  x: 1\n"
                        + "  y: 0\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> list(integer()).validate(ImmutableList.of("myList"), root, root.get("myList"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'myList' should be LIST\\(INTEGER\\), but it is OBJECT"));
    }

    @Test
    public void list_expectingListOfObjectButIsString() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "myList: \n"
                        + "  x: 1\n"
                        + "  y: 2\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> list(object(field("a", integer()), field("b", integer())))
                    .validate(ImmutableList.of("myList"), root, root.get("myList"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'myList' should be LIST\\(OBJECT\\(a, b\\)\\), but it is OBJECT"));
    }


    @Test
    public void routingObject_routingObjectDefinition() throws Exception {

        Map<String, Schema.FieldType> extensions = ImmutableMap.of(
                "ProxyTo", object(
                        field("id", string()),
                        field("destination", string())
                ),
                "Redirection", object(
                        field("status", integer()),
                        field("location", string())
                )
        );

        JsonNode root = YAML_MAPPER.readTree(""
                + "object1: \n"
                + "  type: 'ProxyTo'\n"
                + "  config:\n"
                + "    id: 'local-01'\n"
                + "    destination: 'localhost:8080'\n"
                + "object2: \n"
                + "  type: 'Redirection'\n"
                + "  config:\n"
                + "    status: 301\n"
                + "    location: /new/location\n"
        );

        new Schema.RoutingObjectSpec().validate(emptyList(), root, root.get("object1"), extensions::get);
        new Schema.RoutingObjectSpec().validate(emptyList(), root, root.get("object2"), extensions::get);
    }

    @Test
    public void union_errorsWhenUnionDiscriminatorIsNotStringValue() throws IOException {
        JsonNode root = YAML_MAPPER.readTree(""
                + "httpPipeline: \n"
                + "  config:\n"
                + "    id: 'local-01'\n"
                + "    destination: 'localhost:8080'\n"
                + "  type: 123\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> object(
                        field("config", union("type")),
                        field("type", string())
                ).validate(ImmutableList.of("httpPipeline"), root, root.get("httpPipeline"), NO_EXTENSIONS));
        assertEquals("Union discriminator 'httpPipeline.config.type': Unexpected field type. Field 'httpPipeline.config.type' should be STRING, but it is NUMBER", e.getMessage());
    }


    @Test
    public void union_errorsWhenUnionSchemaNotFound() throws IOException {
        JsonNode root = YAML_MAPPER.readTree(""
                + "httpPipeline: \n"
                + "  type: Foo\n"
                + "  config:\n"
                + "    id: 'local-01'\n"
                + "    destination: 'localhost:8080'\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> object(
                        field("type", string()),
                        field("config", union("type"))
                ).validate(ImmutableList.of("httpPipeline"), root, root.get("httpPipeline"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unknown union discriminator type 'Foo' for union 'httpPipeline.config'. Union type is UNION\\(type\\)"));
    }


    @Test
    public void union_validatesDiscriminatedUnions() throws Exception {

        Map<String, Schema.FieldType> extensions = ImmutableMap.of(
                "ProxyTo", object(
                        field("id", string()),
                        field("destination", string())
                ),
                "Redirection", object(
                        field("status", integer()),
                        field("location", string())
                )
        );

        JsonNode root = YAML_MAPPER.readTree(""
                + "httpPipeline1: \n"
                + "  type: 'ProxyTo'\n"
                + "  config:\n"
                + "    id: 'local-01'\n"
                + "    destination: 'localhost:8080'\n"
                + "httpPipeline2: \n"
                + "  type: 'Redirection'\n"
                + "  config:\n"
                + "    status: 301\n"
                + "    location: /new/location\n"
        );

        Schema.FieldType unionSchema = object(
                field("type", string()),
                field("config", union("type"))
        );

        unionSchema.validate(ImmutableList.of(), root, root.get("httpPipeline1"), extensions::get);
        unionSchema.validate(ImmutableList.of(), root, root.get("httpPipeline2"), extensions::get);
    }


    @Test
    public void map_rejectsInvalidMaps() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: x\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> map(string()).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'parent' should be MAP\\(STRING\\), but it is STRING"));
    }

    @Test
    public void map_acceptsMapOfObjects() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: \n"
                        + "    x: 1\n"
                        + "    y: 2\n"
                        + "  key2: \n"
                        + "    x: 3\n"
                        + "    y: 4\n"
        );

        map(object(
                field("x", integer()),
                field("y", integer())
        )).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS);

    }

    @Test
    public void map_validatesMapOfObjects() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: 1\n"
                        + "  key2: 5\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> map(object(
                        field("x", integer()),
                        field("y", integer())
                )).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'parent\\[key.\\]' should be OBJECT\\(x, y\\), but it is NUMBER"));
    }

    @Test
    public void map_acceptsMapOfIntegers() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: 23\n"
                        + "  key2: 24\n"
        );

        map(integer()).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS);
    }

    @Test
    public void map_validatesMapOfIntegers() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: 'xyz'\n"
                        + "  key2: 24\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> map(integer()).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'parent\\[key1\\]' should be INTEGER, but it is STRING"));
    }

    @Test
    public void map_acceptsMapOfStrings() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: 'one'\n"
                        + "  key2: 'two'\n"
        );

        map(string()).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS);
    }

    @Test
    public void map_validatesMapOfStrings() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: 5\n"
                        + "  key2: 'two'\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> map(string()).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'parent\\[key1\\]' should be STRING, but it is NUMBER"));
    }

    @Test
    public void map_acceptsMapOfBooleans() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  ok: true\n"
                        + "  nok: False\n"
        );

        map(bool()).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS);
    }

    @Test
    public void map_validatesMapOfBooleans() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  ok: nonbool\n"
                        + "  nok: False\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> map(bool()).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'parent\\[ok\\]' should be BOOLEAN, but it is STRING"));
    }

    @Test
    public void map_acceptsMapOfListOfInts() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: \n"
                        + "    - 1\n"
                        + "    - 2\n"
                        + "  key2: \n"
                        + "    - 3\n"
                        + "    - 4\n"
        );

        map(list(integer())).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS);
    }

    @Test
    public void map_validatesMapOfListOfInts() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: \n"
                        + "    x: 1\n"
                        + "    y: 2\n"
                        + "  key2: \n"
                        + "    - 3\n"
                        + "    - 4\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> map(list(integer())).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'parent\\[key1\\]' should be LIST\\(INTEGER\\), but it is OBJECT"));
    }

    @Test
    public void map_acceptssMapOfListOfObjects() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: \n"
                        + "    - x: 1\n"
                        + "      y: 2\n"
                        + "    - x: 3\n"
                        + "      y: 4\n"
        );

        map(list(
                object(
                        field("x", integer()),
                        field("y", integer()
                        )
                )
        )).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS);
    }

    @Test
    public void map_validatesMapOfListOfObjects() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  mapKey: \n"
                        + "    - ImString \n"
                        + "    - x: 3\n"
                        + "      y: 4\n"
        );

        Exception e = assertThrows(SchemaValidationException.class,
                () -> map(list(
                        object(
                                field("x", integer()),
                                field("y", integer()
                                )
                        )
                )).validate(ImmutableList.of("parent"), root, root.get("parent"), NO_EXTENSIONS));
        assertThat(e.getMessage(), matchesPattern("Unexpected field type. Field 'parent\\[mapKey\\]\\[0\\]' should be OBJECT\\(x, y\\), but it is STRING"));
    }

    @Test
    public void list_checksThatSubobjectUnionDiscriminatorAttributeExists() throws Exception {
        Exception e = assertThrows(InvalidSchemaException.class,
                () -> schema(field("name", string()),
                        field("config", union("type"))
                ));
        assertEquals("Discriminator attribute 'type' not present.", e.getMessage());
    }

    @Test
    public void checksThatSubobjectUnionDiscriminatorAttributeIsString() throws Exception {
        Exception e = assertThrows(InvalidSchemaException.class,
                () -> schema(field("name", string()),
                        field("type", integer()),
                        field("config", union("type"))
                ));
        assertThat(e.getMessage(), matchesPattern("Discriminator attribute 'type' must be a string \\(but it is not\\)"));
    }

}