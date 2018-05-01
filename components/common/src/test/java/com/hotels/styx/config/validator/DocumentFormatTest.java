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
import com.hotels.styx.config.schema.InvalidSchemaException;
import com.hotels.styx.config.schema.SchemaValidationException;
import org.testng.annotations.Test;

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
import static com.hotels.styx.config.validator.DocumentFormat.newDocument;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class DocumentFormatTest {
    private final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);

    @Test
    public void validatesElementaryTypes() throws Exception {
        boolean result = newDocument()
                .rootSchema(
                        schema(
                                field("root", object(
                                        field("myInt", integer()),
                                        field("myBool", bool()),
                                        field("myString", string())
                                ))
                        )
                )
                .build()
                .validateObject(YAML_MAPPER.readTree(""
                        + "root: \n"
                        + "  myInt: 5 \n"
                        + "  myBool: true \n"
                        + "  myString: styx\n"));
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Missing a mandatory field 'root.surname'")
    public void ensuresAllMandatoryFieldsArePresent() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  name: John \n"
                + "  age: 5\n");

        boolean result = newDocument()
                .rootSchema(
                        schema(
                                field("root", object(
                                        field("name", string()),
                                        field("surname", string()),
                                        field("age", integer())
                                ))
                        ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test
    public void optionalFieldsDoesntHaveToBePresent() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  name: John \n"
                + "  age: 5\n");

        boolean result = newDocument()
                .rootSchema(schema(
                        field("root", object(
                                field("name", string()),
                                optional("favouriteFood", string()),
                                field("age", integer())
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'root.favouriteFood' should be STRING, but it is INTEGER")
    public void verifiesOptionalFields() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  name: John \n"
                + "  favouriteFood: 43 \n"
                + "  age: 5\n");

        boolean result = newDocument()
                .rootSchema(schema(
                        field("root", object(
                                field("name", string()),
                                optional("favouriteFood", string()),
                                field("age", integer())
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field: 'root.xyxz'")
    public void ensuresNoExtraFieldsPresent() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  name: John \n"
                + "  surname: Doe \n"
                + "  age: 5\n"
                + "  xyxz: 'not supposed to be here'\n");

        boolean result = newDocument()
                .rootSchema(schema(
                        field("root", object(
                                field("name", string()),
                                field("surname", string()),
                                field("age", integer())
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'root.myInt' should be INTEGER, but it is STRING")
    public void checksIntegerFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  myInt: 'y' \n");

        boolean result = newDocument()
                .rootSchema(schema(
                        field("root", object(
                                field("myInt", integer())
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test
    public void convertsStringsToIntegerValues() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  myInt: '5' \n");

        boolean result = newDocument()
                .rootSchema(schema(
                        field("root", object(
                                field("myInt", integer())
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'root.myString' should be STRING, but it is INTEGER")
    public void checksStringFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  myString: 5.0 \n");

        boolean result = newDocument()
                .rootSchema(schema(
                        field("root", object(
                                field("myString", string())
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'root.myBool' should be BOOLEAN, but it is INTEGER")
    public void checksBoolFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  myBool: 5.0 \n");

        boolean result = newDocument()
                .rootSchema(schema(
                        field("root", object(
                                field("myBool", bool())
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test
    public void convertsStringToBooleanValues() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "root: \n"
                + "  myBool_01: 'true' \n"
                + "  myBool_02: 'false' \n"
                + "  myBool_03: 'True' \n"
                + "  myBool_04: 'False' \n"
                + "  myBool_05: 'TRUE' \n"
                + "  myBool_06: 'FALSE' \n"
        );

        boolean result = newDocument()
                .rootSchema(schema(
                        field("root", object(
                                field("myBool_01", bool()),
                                field("myBool_02", bool()),
                                field("myBool_03", bool()),
                                field("myBool_04", bool()),
                                field("myBool_05", bool()),
                                field("myBool_06", bool())
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected list element type. Field 'parent.myList\\[1\\]' should be STRING, but it is INTEGER")
    public void checksListsOfElementaryTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "parent: \n"
                + "  myList: \n"
                + "   - b \n"
                + "   - 5 \n"
        );

        boolean result = newDocument()
                .rootSchema(schema(
                        field("parent", object(
                                field("myList", list(string()))
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected list element type. Field 'parent.myList\\[0\\]' should be INTEGER, but it is STRING")
    public void checksListsOfElementaryTypes_wrongIntegerType() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "parent: \n"
                + "  myList: \n"
                + "   - b \n"
                + "   - 5 \n"
        );

        boolean result = newDocument()
                .rootSchema(schema(
                        field("parent", object(
                                field("myList", list(integer()))
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'parent.myList\\[1\\].x' should be INTEGER, but it is STRING")
    public void checksListsOfSubObjects() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "parent: \n"
                + "  myList: \n"
                + "   - x: 0 \n"
                + "     y: 0 \n"
                + "   - x: a \n"
                + "     y: 2 \n"
        );

        boolean result = newDocument()
                .subSchema("SubObjectSchema",
                        schema(
                                field("x", integer()),
                                field("y", integer())
                        ))
                .rootSchema(schema(
                        field("parent", object(
                                field("myList", list(object("SubObjectSchema")))
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected list element type. Field 'parent.myList\\[1\\]' should be OBJECT \\('SubObjectSchema'\\), but it is STRING")
    public void checksListsOfSubObjects_shouldBeSubobjectButIsString() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "parent: \n"
                + "  myList: \n"
                + "   - x: 0 \n"
                + "     y: 0 \n"
                + "   - 'zz' \n"
        );

        boolean result = newDocument()
                .subSchema("SubObjectSchema",
                        schema(
                                field("x", integer()),
                                field("y", integer())
                        ))
                .rootSchema(schema(
                        field("parent", object(
                                field("myList", list(object("SubObjectSchema")))
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'myList' should be LIST \\(INTEGER\\), but it is STRING")
    public void expectingListButIsString() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "myList: 'or not'\n"
        );

        newDocument()
                .rootSchema(schema(
                        field("myList", list(integer()))
                ))
                .build()
                .validateObject(root);
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'myList' should be LIST \\(INTEGER\\), but it is OBJECT")
    public void expectingListButIsObject() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "myList: \n"
                        + "  x: 1\n"
                        + "  y: 0\n"
        );

        newDocument()
                .rootSchema(schema(
                        field("myList", list(integer()))
                ))
                .build()
                .validateObject(root);
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'myList' should be LIST \\(OBJECT\\), but it is OBJECT")
    public void expectingListOfObjectButIsString() throws Exception {
        JsonNode root = YAML_MAPPER.readTree(
                "myList: \n"
                        + "  x: 1\n"
                        + "  y: 2\n"
        );

        newDocument()
                .rootSchema(schema(
                        field("myList", list(object(field("a", integer()), field("b", integer()))))
                ))
                .build()
                .validateObject(root);
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'parent.myChild' should be OBJECT \\('age'\\), but it is INTEGER")
    public void checksSubObjectFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "parent: \n"
                + "  myChild: 5.0 \n");

        boolean result = newDocument()
                .rootSchema(schema(
                        field("parent", object(
                                field("myChild", object(
                                        field("age", integer())
                                ))
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test
    public void validatesSubobjects() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "person: \n"
                + "  details: \n"
                + "    name: John \n"
                + "    surname: Doe \n"
                + "    age: 5\n"
                + "  id: '005-001-006'\n")
                .get("person");

        boolean result = newDocument()
                .rootSchema(schema(
                        field("id", string()),
                        field("details", object(
                                field("name", string()),
                                field("surname", string()),
                                field("age", integer())
                        ))
                ))
                .build()
                .validateObject(rootObject);
        assertThat(result, is(true));
    }

    @Test
    public void reusesObjectSchemas() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(""
                + "httpPipeline: \n"
                + "  pipeline: 'interceptorsList'\n"
                + "  handler: 'handlerName'\n"
                + "plugins:\n"
                + "  all: 'x, y z'\n");

        DocumentFormat validator = newDocument()
                .subSchema("HttpPipeline", schema(
                        field("pipeline", string()),
                        field("handler", string())
                ))
                .subSchema("PluginsList", schema(
                        field("all", string())
                ))
                .rootSchema(schema(
                        field("httpPipeline", object("HttpPipeline")),
                        field("plugins", object("PluginsList"))
                ))
                .build();

        assertThat(validator.validateObject(rootObject), is(true));
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "No schema configured for lazy object reference 'notExists'")
    public void documentBuilderEnsuresNamedSchemaReferences() throws Exception {
        newDocument()
                .subSchema("PluginsList", schema(field("all", string())))
                .rootSchema(schema(
                        field("httpPipeline", object("notExists")),
                        field("plugins", object("PluginsList"))
                ))
                .build();
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "No schema configured for lazy object reference 'notExists'")
    public void documentBuilderEnsuresNamedSchemaReferencesAreValidFromSubobjects() throws Exception {
        newDocument()
                .subSchema("PluginsList", schema(field("all", string())))
                .rootSchema(schema(
                        field("parent", object(
                                field("httpPipeline", object("notExists"))
                        )),
                        field("plugins", object("PluginsList"))
                ))
                .build();
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "No schema configured for lazy object reference 'non-existing-HandlerConfig'")
    public void documentBuilderEnsuresNamedSchemaReferencesAreValidFromSubSchemas() throws Exception {
        newDocument()
                .subSchema("HttpPipeline", schema(
                        field("handlers", object("non-existing-HandlerConfig"))
                ))
                .subSchema("PluginsList", schema(field("all", string())
                ))
                .rootSchema(schema(
                        field("plugins", object("PluginsList")),
                        field("httpPipeline", object("HttpPipeline"))
                ))
                .build();
    }

    @Test
    public void validatesDiscriminatedUnions() throws Exception {
        DocumentFormat validator = newDocument()
                .subSchema("ProxyTo", schema(
                        field("id", string()),
                        field("destination", string())
                ))
                .subSchema("Redirection", schema(
                        field("status", integer()),
                        field("location", string())
                ))
                .rootSchema(schema(
                        field("httpPipeline", object(
                                field("type", string()),
                                field("config", union("type"))
                        ))
                ))
                .build();

        boolean outcome1 = validator.validateObject(
                YAML_MAPPER.readTree(""
                        + "httpPipeline: \n"
                        + "  type: 'ProxyTo'\n"
                        + "  config:\n"
                        + "    id: 'local-01'\n"
                        + "    destination: 'localhost:8080'\n"
                ));
        assertThat(outcome1, is(true));

        boolean outcome2 = validator.validateObject(
                YAML_MAPPER.readTree(""
                        + "httpPipeline: \n"
                        + "  type: 'Redirection'\n"
                        + "  config:\n"
                        + "    status: 301\n"
                        + "    location: /new/location\n"
                ));
        assertThat(outcome2, is(true));

    }

    @Test
    public void ignoresOpaqueSubobjectValidation() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(""
                + "parent: \n"
                + "  opaque: \n"
                + "    x: 5\n"
                + "    y: 6\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", object(
                                field("opaque", object(opaque()))
                        ))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }

    @Test
    public void acceptsMapOfObjects() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: \n"
                        + "    x: 1\n"
                        + "    y: 2\n"
                        + "  key2: \n"
                        + "    x: 3\n"
                        + "    y: 4\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", map(
                                object(
                                        field("x", integer()),
                                        field("y", integer())
                                )))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected map element type. Field 'parent.key1' should be OBJECT \\('x, y'\\), but it is INTEGER")
    public void validatesMapOfObjects() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: 1\n"
                        + "  key2: 5\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", map(
                                object(
                                        field("x", integer()),
                                        field("y", integer())
                                )))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }

    @Test
    public void acceptsMapOfIntegers() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: 23\n"
                        + "  key2: 24\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", map(integer()))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected map element type. Field 'parent.key1' should be INTEGER, but it is STRING")
    public void validatesMapOfIntegers() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: 'xyz'\n"
                        + "  key2: 24\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", map(integer()))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }

    @Test
    public void acceptsMapOfStrings() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: 'one'\n"
                        + "  key2: 'two'\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", map(string()))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected map element type. Field 'parent.key1' should be STRING, but it is INTEGER")
    public void validatesMapOfStrings() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: 5\n"
                        + "  key2: 'two'\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", map(string()))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }

    @Test
    public void acceptsMapOfBooleans() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  ok: true\n"
                        + "  nok: False\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", map(bool()))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected map element type. Field 'parent.ok' should be BOOLEAN, but it is STRING")
    public void validatesMapOfBooleans() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  ok: nonbool\n"
                        + "  nok: False\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", map(bool()))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }

    @Test
    public void acceptsMapOfListOfInts() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: \n"
                        + "    - 1\n"
                        + "    - 2\n"
                        + "  key2: \n"
                        + "    - 3\n"
                        + "    - 4\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", map(list(integer())))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Field 'parent.key1' should be LIST \\(INTEGER\\), but it is OBJECT")
    public void validatesMapOfListOfInts() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: \n"
                        + "    x: 1\n"
                        + "    y: 2\n"
                        + "  key2: \n"
                        + "    - 3\n"
                        + "    - 4\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", map(list(integer())))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }

    @Test
    public void acceptssMapOfListOfObjects() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: \n"
                        + "    - x: 1\n"
                        + "      y: 2\n"
                        + "    - x: 3\n"
                        + "      y: 4\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", map(
                                list(
                                        object(
                                                field("x", integer()),
                                                field("y", integer()
                                                )
                                        )
                                )))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected list element type. Field 'parent\\[0\\]' should be OBJECT \\('x, y'\\), but it is STRING")
    public void validatesMapOfListOfObjects() throws Exception {
        JsonNode node2 = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  key1: \n"
                        + "    - ImString \n"
                        + "    - x: 3\n"
                        + "      y: 4\n"
        );

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("parent", map(
                                list(
                                        object(
                                                field("x", integer()),
                                                field("y", integer()
                                                )
                                        )
                                )))
                ))
                .build();

        boolean outcome = validator.validateObject(node2);
        assertThat(outcome, is(true));
    }


    /*
     * Validates related aspects of `atLeastOne` constraint:
     *
     *  1) One required field is present.
     *  2) Another required field is present.
     *  3) Both required fields are present
     *  4) Neither of the required fields are present.
     *
     */
    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Schema constraint failed. At least one of \\('http', 'https'\\) must be present.")
    public void validatesAtLeastOneConstraintCorrectly() throws Exception {
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

        DocumentFormat validator = newDocument()
                .rootSchema(schema(
                        field("connectors", object(
                                optional("x", integer()),
                                optional("http", integer()),
                                optional("https", integer()),
                                atLeastOne("http", "https")
                        ))))
                .build();

        assertThat(validator.validateObject(first), is(true));
        assertThat(validator.validateObject(second), is(true));
        assertThat(validator.validateObject(both), is(true));
        validator.validateObject(neither);
    }
}