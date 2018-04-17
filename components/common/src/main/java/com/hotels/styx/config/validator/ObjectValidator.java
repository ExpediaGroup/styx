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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Object validator provides an end-user interface for the schema based object validation.
 *
 * An ObjectValidator instance is created with a call to `newDocument`. This returns an
 * a builder object that is used to customise the validator. Specifically to:
 *
 * - add named sub-schemas that can be referred to from other schema objects.
 *
 * - to specify a `root schema` that declares the layout of the top level configuration object.
 *
 *
 */
public class ObjectValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectValidator.class);

    private final Schema rootSchema;
    private final Map<String, Schema> schemas;

    private ObjectValidator(Builder builder) {
        this.rootSchema = builder.schema;
        this.schemas = ImmutableMap.copyOf(builder.schemas);
    }

    public static Schema.Builder schema(String name) {
        return new Schema.Builder(name);
    }

    public static Schema.Builder schema() {
        return schema("");
    }

    public static Schema.Builder pass() {
        return new Schema.Builder("").pass(true);
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
        }

        if (fieldValue instanceof Schema.ObjectFieldLazy) {
            Schema.ObjectFieldLazy configField = (Schema.ObjectFieldLazy) fieldValue;
            Schema subSchema = schemas.get(configField.schemaName());

            LOGGER.info("lazy object reference field='{}', subObjectSchema='{}'",
                    new Object[]{name, subSchema.name()});

            validateObject(prefix + name + ".", subSchema, tree.get(name));
        }

        if (fieldValue instanceof Schema.DiscriminatedUnionObject) {
            Schema.DiscriminatedUnionObject unionField = (Schema.DiscriminatedUnionObject) fieldValue;

            String discriminatorField = unionField.discriminatorFieldName();
            String subObjectType = tree.get(discriminatorField).asText();

            Schema subObjectSchema = this.schemas.get(subObjectType);

            LOGGER.info("discriminated union field='{}', discriminatorField='{}', subObjectType='{}', subObjectSchema='{}'",
                    new Object[]{name, discriminatorField, subObjectType, subObjectSchema.name()});

            validateObject(prefix + name + ".", subObjectSchema, tree.get(name));
        }

        if (fieldValue instanceof Schema.ListField) {
            Schema.ListField listField = (Schema.ListField) fieldValue;

            // ALT1: Check that all elements are of desirede (elementary) type
            if (isBasicType(listField.elementType())) {
                JsonNode list = tree.get(name);
                for (int i = 0; i < list.size(); i++) {
                    JsonNode entry = list.get(i);
                    assertCorrectType("Unexpected list element type.", prefix + format("%s[%d]", name, i), entry, listField.elementType());
                }
            }

            // ALT2: Check that all elements follow the same object
            if (isObject(listField.elementType())) {
                JsonNode list = tree.get(name);
                for (int i = 0; i < list.size(); i++) {
                    JsonNode entry = list.get(i);
                    Schema subSchema = getSchema(listField.elementType());
                    assertCorrectType("Unexpected list element type.", prefix + format("%s[%d]", name, i), entry, listField.elementType());
                    validateObject(prefix + format("%s[%d].", name, i), subSchema, entry);
                }
            }

            // ALT3: Lists of lists
        }
    }

    private void validateObject(String prefix, Schema schema, JsonNode tree) {

        LOGGER.info("validate object('{}', schema='{}')", prefix, schema.name());

        if (schema.isPass()) {
            return;
        }

        List<String> fieldNames = ImmutableList.copyOf(tree.fieldNames());

        assertNoUnknownFields(prefix, schema, fieldNames);

        schema.constraints().forEach(constraint -> {
            if (!constraint.evaluate(schema, tree)) {
                throw new SchemaValidationException("Schema constraint failed. " + constraint.message());
            }
        });

        schema.fields().forEach(field -> {
            if (isMandatory(schema, field) && tree.get(field.name()) == null) {
                throw new SchemaValidationException(format("Missing a mandatory field '%s'", prefix + field.name()));
            }

            if (tree.get(field.name()) != null) {
                validateField(prefix, field, tree);
            }
        });
    }

    private boolean isMandatory(Schema schema, Schema.Field field) {
        return !schema.optionals().contains(field.name());
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

    private boolean isObject(Schema.FieldValue fieldValue) {
        return (fieldValue instanceof Schema.ObjectField)
                || (fieldValue instanceof Schema.ObjectFieldLazy);
    }

    private boolean isBasicType(Schema.FieldValue fieldValue) {
        return !(fieldValue instanceof Schema.ObjectField)
                && !(fieldValue instanceof Schema.ObjectFieldLazy)
                && !(fieldValue instanceof Schema.ListField);
    }

    private Schema.FieldType toFieldType(JsonNode value) {
        // JsonNodeType.BINARY, Schema.FieldType.
        // JsonNodeType.MISSING,
        // JsonNodeType.NULL,
        // JsonNodeType.POJO,

        if (value.isInt()) {
            return Schema.FieldType.INTEGER;
        }

        return ImmutableMap.of(
                JsonNodeType.ARRAY, Schema.FieldType.LIST,
                JsonNodeType.BOOLEAN, Schema.FieldType.BOOLEAN,
                JsonNodeType.NUMBER, Schema.FieldType.INTEGER,
                JsonNodeType.OBJECT, Schema.FieldType.OBJECT,
                JsonNodeType.STRING, Schema.FieldType.STRING)
                .get(value.getNodeType());
    }

    private static String displayExpectedType(Schema.FieldType expectedType, Schema.FieldValue field) {
        if (field instanceof Schema.ObjectField) {
            Schema.ObjectField subField = (Schema.ObjectField) field;
            return format("%s ('%s')", expectedType, subField.schema().name());
        }

        if (field instanceof Schema.ObjectFieldLazy) {
            Schema.ObjectFieldLazy subField = (Schema.ObjectFieldLazy) field;
            return format("%s ('%s')", expectedType, subField.schemaName());
        }

        return expectedType.toString();
    }

    private void assertCorrectType(String message, String fieldName, JsonNode value, Schema.FieldValue field) {
        Schema.FieldType expectedType = field.type();
        Schema.FieldType actualType = toFieldType(value);

        if (expectedType != actualType) {
            throw new SchemaValidationException(format("%s Field '%s' should be %s, but it is %s",
                    message, fieldName, displayExpectedType(expectedType, field), actualType));
        }
    }

    /**
     * An object validator builder.
     */
    public static class Builder {
        private Schema schema;
        private final Map<String, Schema> schemas = new HashMap<>();

        public Builder() {
        }

        public Builder subSchema(String name, Schema.Builder schema) {
            this.schemas.put(requireNonNull(name), requireNonNull(schema.name(name).build()));
            return this;
        }

        public Builder rootSchema(Schema.Builder schema) {
            this.schema = requireNonNull(schema.build());
            return this;
        }

        public ObjectValidator build() {
            assertRootSchemaExists(schema);
            assertSchemaReferences(schema);
            schemas.values().forEach(this::assertSchemaReferences);
            return new ObjectValidator(this);
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
