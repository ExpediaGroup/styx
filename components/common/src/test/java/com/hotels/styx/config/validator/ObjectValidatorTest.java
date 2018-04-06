package com.hotels.styx.config.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hotels.styx.config.validator.Schema.Field;
import org.testng.annotations.Test;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.hotels.styx.config.validator.ObjectValidator.newDocument;
import static com.hotels.styx.config.validator.Schema.newSchema;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ObjectValidatorTest {
    private final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);

    @Test
    public void validatesElementaryTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "root: \n"
                        + "  myInt: 5 \n"
                        + "  myString: styx\n");

        Schema schema = newSchema("")
                .field(Field.object(
                        "root", newSchema("root")
                                .field(Field.integer("myInt"))
                                .field(Field.string("myString"))
                                .build()))
                .build();

        boolean result = newDocument(schema).build().validate(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Missing a mandatory field 'root.surname'")
    public void ensuresAllMandatoryFieldsArePresent() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "root: \n"
                        + "  name: John \n"
                        + "  age: 5\n");

        Schema schema = newSchema("")
                .field(Field.object(
                        "root", newSchema("root")
                                .field(Field.string("name"))
                                .field(Field.string("surname"))
                                .field(Field.integer("age"))
                                .build()))
                .build();

        boolean result = newDocument(schema).build().validate(rootObject);
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

        Schema schema = newSchema("")
                .field(Field.object(
                        "root", newSchema("foo")
                                .field(Field.string("name"))
                                .field(Field.string("surname"))
                                .field(Field.integer("age"))
                                .build()))
                .build();

        boolean result = newDocument(schema).build().validate(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Expected 'root.myInt' to be an INTEGER, but it is STRING")
    public void checksIntegerFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "root: \n"
                        + "  myInt: 'y' \n");

        Schema schema = newSchema("")
                .field(Field.object(
                        "root", newSchema("root")
                                .field(Field.integer("myInt"))
                                .build()))
                .build();

        boolean result = newDocument(schema).build().validate(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Expected 'root.myString' to be a STRING, but it is NUMBER")
    public void checksStringFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "root: \n"
                        + "  myString: 5.0 \n");

        Schema schema = newSchema("")
                .field(Field.object(
                        "root", newSchema("foo")
                                .field(Field.string("myString"))
                                .build()))
                .build();

        boolean result = newDocument(schema).build().validate(rootObject);
        assertThat(result, is(true));
    }

    @Test(expectedExceptions = SchemaValidationException.class,
            expectedExceptionsMessageRegExp = "Unexpected field type. Expected 'parent.myChild' to be a OBJECT \\('child'\\), but it is NUMBER")
    public void checksSubObjectFieldTypes() throws Exception {
        JsonNode rootObject = YAML_MAPPER.readTree(
                "parent: \n"
                        + "  myChild: 5.0 \n");

        Schema schema = newSchema("")
                .field(Field.object(
                        "parent", newSchema("child")
                                .field(Field.object(
                                        "myChild", newSchema("child")
                                                .field(Field.integer("age"))
                                                .build()))
                                .build()))
                .build();

        boolean result = newDocument(schema).build().validate(rootObject);
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

        Schema schema = newSchema("person")
                .field(Field.string("id"))
                .field(Field.object(
                        "details", newSchema("personalDetails")
                                .field(Field.string("name"))
                                .field(Field.string("surname"))
                                .field(Field.integer("age"))
                                .build()))
                .build();

        boolean result = newDocument(schema).build().validate(rootObject);
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

        ObjectValidator validator = newDocument(
                newSchema("")
                        .field(Field.object("httpPipeline", "HttpPipeline"))
                        .field(Field.object("plugins", "PluginsList"))
                        .build())
                .subSchema(newSchema("HttpPipeline")
                        .field(Field.string("pipeline"))
                        .field(Field.string("handler"))
                        .build()
                )
                .subSchema(newSchema("PluginsList")
                        .field(Field.string("all"))
                        .build())
                .build();

        assertThat(validator.validate(rootObject), is(true));
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "No schema configured for lazy object reference 'notExists'")
    public void documentBuilderEnsuresNamedSchemaReferences() throws Exception {
        newDocument(
                newSchema("")
                        .field(Field.object("httpPipeline", "notExists"))
                        .field(Field.object("plugins", "PluginsList"))
                        .build())
                .subSchema(newSchema("PluginsList")
                        .field(Field.string("all"))
                        .build())
                .build();
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "No schema configured for lazy object reference 'notExists'")
    public void documentBuilderEnsuresNamedSchemaReferencesAreValidFromSubobjects() throws Exception {
        newDocument(
                newSchema("")
                        .field(Field.object(
                                "parent", newSchema("subobject")
                                        .field(Field.object("httpPipeline", "notExists"))
                                        .build()))
                        .field(Field.object("plugins", "PluginsList"))
                        .build())
                .subSchema(newSchema("PluginsList")
                        .field(Field.string("all"))
                        .build())
                .build();
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "No schema configured for lazy object reference 'non-existing-HandlerConfig'")
    public void documentBuilderEnsuresNamedSchemaReferencesAreValidFromSubSchemas() throws Exception {
        newDocument(
                newSchema("")
                        .field(Field.object("plugins", "PluginsList"))
                        .field(Field.object("httpPipeline", "HttpPipeline"))
                        .build())
                .subSchema(newSchema("HttpPipeline")
                        .field(Field.object("handlers", "non-existing-HandlerConfig"))
                        .build()
                )
                .subSchema(newSchema("PluginsList")
                        .field(Field.string("all"))
                        .build())
                .build();
    }

    @Test
    public void validatesDiscriminatedUnions() throws Exception {
        ObjectValidator validator = newDocument(
                newSchema("")
                        .field(Field.object("httpPipeline", "RoutingObject"))
                        .build())
                .subSchema(newSchema("RoutingObject")
                        .field(Field.string("name"))
                        .field(Field.string("type"))
                        .field(Field.union("config", "type"))
                        .build()
                )
                .subSchema(newSchema("ConnectionPool")
                        .field(Field.integer("maxConnectionsPerHost"))
                        .field(Field.integer("maxPendingConnectionsPerHost"))
                        .build()
                )
                .subSchema(newSchema("ProxyToBackend")
                        .field(Field.object(
                                "backend", newSchema("")
                                        .field(Field.string("id"))
                                        .field(Field.object("connectionPool", "ConnectionPool"))
                                        .build()))
                        .build()
                )
                .subSchema(newSchema("ConditionDestination")
                        .field(Field.string("condition"))
                        .field(Field.object("destination", "RoutingObject"))
                        .build()
                )
                .subSchema(newSchema("ConditionRouter")
                        .field(Field.object("route", "ConditionDestination"))
                        .field(Field.object("fallback", "RoutingObject"))
                        .build()
                )
                .build();


        JsonNode node2 = YAML_MAPPER.readTree(
                "httpPipeline: \n"
                        + "  name: 'myPipeline'\n"
                        + "  type: 'ConditionRouter'\n"
                        + "  config:\n"
                        + "    route:\n"
                        + "      condition: 'protocol() == https'\n"
                        + "      destination: \n"
                        + "        name: https-backend\n"
                        + "        type: ProxyToBackend\n"
                        + "        config:\n"
                        + "          backend:\n"
                        + "            id: '01'\n"
                        + "            connectionPool:\n"
                        + "              maxConnectionsPerHost:        1\n"
                        + "              maxPendingConnectionsPerHost: 4\n"
                        + "    fallback:\n"
                        + "      name: fallback-backend\n"
                        + "      type: ProxyToBackend\n"
                        + "      config:\n"
                        + "        backend:\n"
                        + "          id: 'app-01'\n"
                        + "          connectionPool:\n"
                        + "            maxConnectionsPerHost:        3\n"
                        + "            maxPendingConnectionsPerHost: 4\n"
        );

        boolean outcome2 = validator.validate(node2);
        assertThat(outcome2, is(true));


        JsonNode node = YAML_MAPPER.readTree(
                "httpPipeline: \n"
                        + "  name: 'myPipeline'\n"
                        + "  type: 'ProxyToBackend'\n"
                        + "  config:\n"
                        + "    backend:\n"
                        + "      id: 'app-01'\n"
                        + "      connectionPool:\n"
                        + "        maxConnectionsPerHost:        5\n"
                        + "        maxPendingConnectionsPerHost: 5\n"
        );

        boolean outcome = validator.validate(node);
        assertThat(outcome, is(true));


    }
}