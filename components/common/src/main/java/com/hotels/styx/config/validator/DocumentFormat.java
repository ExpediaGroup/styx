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
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.hotels.styx.config.schema.InvalidSchemaException;
import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.config.schema.Schema.FieldType;
import com.hotels.styx.config.schema.SchemaValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Provides an end-user interface for the schema based object validation.
 * <p>
 * An DocumentFormat instance is created with a call to `newDocument`. This returns an
 * a builder object that is used to customise the validator. Specifically to:
 * <p>
 * - add named sub-schemas that can be referred to from other schema objects.
 * <p>
 * - to specify a `root schema` that declares the layout of the top level configuration object.
 */
public class DocumentFormat {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentFormat.class);
    private static final Pattern YAML_BOOLEAN_VALUES = Pattern.compile("(?i)true|false");

    private final Schema rootSchema;
    private final Map<String, Schema> schemas;

    private DocumentFormat(Builder builder) {
        this.rootSchema = builder.schema;
        this.schemas = ImmutableMap.copyOf(builder.schemas);
    }

    public boolean validateObject(JsonNode tree) {
        validateObject("", rootSchema, tree);
        return true;
    }

    public static Builder newDocument() {
        return new Builder();
    }

    private void validateField(String prefix, Schema.Field field, JsonNode tree) {
        requireNonNull(prefix);
        requireNonNull(field);
        requireNonNull(tree);

        String name = field.name();
        assertCorrectType("Unexpected field type.", prefix + name, tree.get(name), field.value());

        Schema.FieldValue fieldValue = field.value();

        if (fieldValue instanceof Schema.ObjectField) {
            Schema.ObjectField configField = (Schema.ObjectField) fieldValue;
            validateObject(prefix + name + ".", configField.schema(), tree.get(name));

        } else if (fieldValue instanceof Schema.ListField) {
            Schema.ListField listField = (Schema.ListField) fieldValue;
            validateList(prefix + name, listField, tree.get(name));

        } else if (fieldValue instanceof Schema.ObjectFieldLazy) {
            Schema.ObjectFieldLazy configField = (Schema.ObjectFieldLazy) fieldValue;
            Schema subSchema = schemas.get(configField.schemaName());

            LOGGER.debug("lazy object reference field='{}', subObjectSchema='{}'", name, subSchema.name());
            validateObject(prefix + name + ".", subSchema, tree.get(name));

        } else if (fieldValue instanceof Schema.DiscriminatedUnionObject) {
            Schema.DiscriminatedUnionObject unionField = (Schema.DiscriminatedUnionObject) fieldValue;
            String discriminatorFieldName = unionField.discriminatorFieldName();

            validateDiscriminatedUnion(prefix + name, discriminatorFieldName, tree.get(unionField.discriminatorFieldName()), tree.get(name));

        } else if (fieldValue instanceof Schema.MapField) {
            Schema.MapField mapField = (Schema.MapField) fieldValue;
            validateMap(prefix + name, mapField, tree.get(name));
        }
    }

    private void validateDiscriminatedUnion(String prefix, String discriminatorFieldName, JsonNode discriminatorNode, JsonNode unionNode) {
        String subObjectType = discriminatorNode.asText();
        Schema subObjectSchema = this.schemas.get(subObjectType);

        LOGGER.debug("discriminated union field='{}', discriminatorField='{}', subObjectType='{}', subObjectSchema='{}'",
                new Object[]{prefix, discriminatorFieldName, subObjectType, subObjectSchema.name()});

        validateObject(prefix + ".", subObjectSchema, unionNode);
    }

    private void validateList(String prefix, Schema.ListField listField, JsonNode list) {
        if (isBasicType(listField.elementType())) {
            // ALT1: Check that all elements are of desirede (elementary) type
            for (int i = 0; i < list.size(); i++) {
                JsonNode entry = list.get(i);
                assertCorrectType("Unexpected list element type.", format("%s[%d]", prefix, i), entry, listField.elementType());
            }
        } else if (isObject(listField.elementType())) {
            // ALT2: Check that all elements follow the same object
            for (int i = 0; i < list.size(); i++) {
                JsonNode entry = list.get(i);
                Schema subSchema = getSchema(listField.elementType());
                assertCorrectType("Unexpected list element type.", format("%s[%d]", prefix, i), entry, listField.elementType());
                validateObject(prefix + format("[%d].", i), subSchema, entry);
            }
        }

        // ALT3: Lists of lists
        // --- not implemented
        //
    }

    private void validateMap(String prefix, Schema.MapField mapField, JsonNode mapNode) {
        if (isBasicType(mapField.elementType())) {
            mapNode.fieldNames().forEachRemaining(key -> {
                JsonNode entry = mapNode.get(key);
                assertCorrectType("Unexpected map element type.", format("%s.%s", prefix, key), entry, mapField.elementType());
            });
        } else if (isObject(mapField.elementType())) {
            mapNode.fieldNames().forEachRemaining(
                    key -> {
                        assertCorrectType("Unexpected map element type.", format("%s.%s", prefix, key), mapNode.get(key), mapField.elementType());
                        validateObject(format("%s.%s.", prefix, key), getSchema(mapField.elementType()), mapNode.get(key));
                    }
            );
        } else if (mapField.elementType().type() == FieldType.LIST) {
            mapNode.fieldNames().forEachRemaining(
                    key -> {
                        assertCorrectType("Unexpected field type.", format("%s.%s", prefix, key), mapNode.get(key), mapField.elementType());
                        validateList(prefix, (Schema.ListField) mapField.elementType(), mapNode.get(key));
                    }
            );
        }
    }

    private void validateObject(String prefix, Schema schema, JsonNode tree) {
        LOGGER.info("validate object('{}', schema='{}')", prefix, schema.name());

        if (schema.ignore()) {
            return;
        }

        for (Schema.Field field : schema.fields()) {
            if (isMandatory(schema, field) && tree.get(field.name()) == null) {
                throw new SchemaValidationException(format("Missing a mandatory field '%s%s'", prefix, field.name()));
            }

            if (tree.get(field.name()) != null) {
                validateField(prefix, field, tree);
            }
        }

        schema.constraints().forEach(constraint -> {
            if (!constraint.evaluate(schema, tree)) {
                throw new SchemaValidationException("Schema constraint failed. " + constraint.describe());
            }
        });

        assertNoUnknownFields(prefix, schema, ImmutableList.copyOf(tree.fieldNames()));
    }

    private static boolean isMandatory(Schema schema, Schema.Field field) {
        return !schema.optionalFields().contains(field.name());
    }

    private static void assertNoUnknownFields(String prefix, Schema schema, List<String> fieldsPresent) {
        Set<String> knownFields = ImmutableSet.copyOf(schema.fieldNames());

        fieldsPresent.forEach((name) -> {
            if (!knownFields.contains(name)) {
                throw new SchemaValidationException(format("Unexpected field: '%s'", prefix + name));
            }
        });
    }

    private Schema getSchema(Schema.FieldValue field) {
        if (field instanceof Schema.ObjectField) {
            return ((Schema.ObjectField) field).schema();
        }
        if (field instanceof Schema.ObjectFieldLazy) {
            return schemas.get(((Schema.ObjectFieldLazy) field).schemaName());
        }
        throw new InvalidSchemaException("Not an object");
    }

    private static boolean isObject(Schema.FieldValue fieldValue) {
        return (fieldValue instanceof Schema.ObjectField)
                || (fieldValue instanceof Schema.ObjectFieldLazy);
    }

    private static boolean isBasicType(Schema.FieldValue fieldValue) {
        return !(fieldValue instanceof Schema.ObjectField)
                && !(fieldValue instanceof Schema.ObjectFieldLazy)
                && !(fieldValue instanceof Schema.ListField);
    }

    private static FieldType toFieldType(JsonNode value) {
        // JsonNodeType.BINARY, Schema.FieldType.
        // JsonNodeType.MISSING,
        // JsonNodeType.NULL,
        // JsonNodeType.POJO,

        if (value.isInt()) {
            return FieldType.INTEGER;
        }

        return ImmutableMap.of(
                JsonNodeType.ARRAY, FieldType.LIST,
                JsonNodeType.BOOLEAN, FieldType.BOOLEAN,
                JsonNodeType.NUMBER, FieldType.INTEGER,
                JsonNodeType.OBJECT, FieldType.OBJECT,
                JsonNodeType.STRING, FieldType.STRING)
                .get(value.getNodeType());
    }

    private static String displayExpectedType(FieldType expectedType, Schema.FieldValue field) {
        if (field instanceof Schema.ObjectField) {
            Schema.ObjectField subField = (Schema.ObjectField) field;
            return format("%s ('%s')", expectedType, subField.schema().name());
        }

        if (field instanceof Schema.ObjectFieldLazy) {
            Schema.ObjectFieldLazy subField = (Schema.ObjectFieldLazy) field;
            return format("%s ('%s')", expectedType, subField.schemaName());
        }

        if (field instanceof Schema.ListField) {
            Schema.ListField listField = (Schema.ListField) field;
            return format("%s (%s)", expectedType, listField.elementType().type());
        }

        return expectedType.toString();
    }

    private static void assertCorrectType(String message, String fieldName, JsonNode value, Schema.FieldValue field) {
        FieldType expectedType = field.type();
        FieldType actualType = toFieldType(value);

        if (FieldType.STRING.equals(actualType) && canParseAsInteger(value)) {
            return;
        }

        if (FieldType.STRING.equals(actualType) && canParseAsBoolean(value)) {
            return;
        }

        if (FieldType.OBJECT.equals(actualType) && FieldType.MAP.equals(expectedType)) {
            return;
        }

        if (expectedType != actualType) {
            throw new SchemaValidationException(format("%s Field '%s' should be %s, but it is %s",
                    message, fieldName, displayExpectedType(expectedType, field), actualType));
        }
    }

    private static boolean canParseAsInteger(JsonNode value) {
        try {
            parseInt(value.textValue());
            return true;
        } catch (NumberFormatException cause) {
            return false;
        }
    }

    private static boolean canParseAsBoolean(JsonNode value) {
        return YAML_BOOLEAN_VALUES.matcher(value.textValue()).matches();
    }

    /**
     * An object validator builder.
     */
    public static class Builder {
        private Schema schema;
        private final Map<String, Schema> schemas = new HashMap<>();

        public Builder subSchema(String name, Schema schema) {
            this.schemas.put(requireNonNull(name), requireNonNull(schema.newBuilder().name(name).build()));
            return this;
        }

        public Builder rootSchema(Schema schema) {
            this.schema = requireNonNull(schema);
            return this;
        }

        public DocumentFormat build() {
            assertRootSchemaExists(schema);
            assertSchemaReferences(schema);
            schemas.values().forEach(this::assertSchemaReferences);
            return new DocumentFormat(this);
        }

        private static void assertRootSchemaExists(Schema schema) {
            if (schema == null) {
                throw new InvalidSchemaException("Root schema is not specified.");
            }
        }

        private void assertSchemaReferences(Schema schema) {
            schema.fields().forEach(field -> {
                if (field.value() instanceof Schema.ObjectFieldLazy) {
                    Schema.ObjectFieldLazy objectField = (Schema.ObjectFieldLazy) field.value();
                    if (!schemas.containsKey(objectField.schemaName())) {
                        throw new InvalidSchemaException(format("No schema configured for lazy object reference '%s'", objectField.schemaName()));
                    }
                }
                if (field.value() instanceof Schema.ObjectField) {
                    Schema.ObjectField objectField = (Schema.ObjectField) field.value();
                    assertSchemaReferences(objectField.schema());
                }
            });
        }
    }

}
