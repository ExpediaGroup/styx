package com.hotels.styx.config.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

public class ObjectValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectValidator.class);

    private final Schema rootSchema;
    private final Map<String, Schema> schemas;

    private ObjectValidator(Builder builder) {
        this.rootSchema = builder.schema;
        this.schemas = unmodifiableMap(builder.schemas);
    }

    private void validate(String prefix, Schema schema, JsonNode tree) {
        List<String> fieldNames = ImmutableList.copyOf(tree.fieldNames());

        LOGGER.info("validate('{}', schema='{}')", prefix, schema.name());

        assertMandatoryFieldsArePresent(schema, prefix, tree);

        fieldNames.forEach(name -> {
            Schema.Field field = schema.field(name);
            assertIsKnown(prefix, name, field);
            assertCorrectType(prefix, tree, name, field);

            if (field instanceof Schema.ObjectField) {
                Schema.ObjectField configField = (Schema.ObjectField) field;
                validate(prefix + name + ".", configField.schema(), tree.get(name));
            }

            // TODO: Add test: lazy object field:
            if (field instanceof Schema.ObjectFieldLazy) {
                Schema.ObjectFieldLazy configField = (Schema.ObjectFieldLazy) field;
                // TODO: Add test: incorrect rootSchema name!
                Schema subSchema = schemas.get(configField.schemaName());

                LOGGER.info("lazy object reference field='{}', subObjectSchema='{}'",
                        new Object[]{name, subSchema.name()});

                validate(prefix + name + ".", subSchema, tree.get(name));
            }

            if (field instanceof Schema.DiscriminatedUnionObject) {
                Schema.DiscriminatedUnionObject unionField = (Schema.DiscriminatedUnionObject) field;

                String discriminatorField = unionField.discriminatorFieldName();
                String subObjectType = tree.get(discriminatorField).asText();
                Schema subObjectSchema = this.schemas.get(subObjectType);

                LOGGER.info("discriminated union field='{}', discriminatorField='{}', subObjectType='{}', subObjectSchema='{}'",
                        new Object[]{name, discriminatorField, subObjectType, subObjectSchema.name()});

                validate(prefix + name + ".", subObjectSchema, tree.get(name));
            }
        });
    }

    public boolean validate(JsonNode tree) {
        validate("", rootSchema, tree);
        return true;
    }

    private void assertMandatoryFieldsArePresent(Schema schema, String prefix, JsonNode tree) {
        schema.fields().forEach(attribute -> {
            if (!tree.has(attribute.name())) {
                throw new SchemaValidationException(format("Missing a mandatory field '%s'", prefix + attribute.name()));
            }
        });
    }

    private void assertIsKnown(String prefix, String name, Schema.Field field) {
        if (field == null) {
            throw new SchemaValidationException(format("Unexpected field: '%s'", prefix + name));
        }
    }

    private void assertCorrectType(String prefix, JsonNode tree, String name, Schema.Field field) {
        JsonNode value = tree.get(name);
        if (field instanceof Schema.IntegerField && !value.isInt()) {
            throw new SchemaValidationException(
                    format("Unexpected field type. Expected '%s' to be an INTEGER, but it is %s",
                            prefix + name, value.getNodeType().name()));
        }
        if (field instanceof Schema.StringField && !value.isTextual()) {
            throw new SchemaValidationException(
                    format("Unexpected field type. Expected '%s' to be a STRING, but it is %s",
                            prefix + name, value.getNodeType().name())
            );
        }
        if (field instanceof Schema.ObjectField && !value.isObject()) {
            Schema.ObjectField subField = (Schema.ObjectField) field;
            throw new SchemaValidationException(
                    format("Unexpected field type. Expected '%s' to be a OBJECT ('%s'), but it is %s",
                            prefix + name, subField.schema().name(), value.getNodeType().name())
            );
        }
        if (field instanceof Schema.ObjectFieldLazy && !value.isObject()) {
            Schema.ObjectFieldLazy subField = (Schema.ObjectFieldLazy) field;
            throw new SchemaValidationException(
                    format("Unexpected field type. Expected '%s' to be a OBJECT ('%s'), but it is %s",
                            prefix + name, subField.schemaName(), value.getNodeType().name())
            );
        }
    }

    public static Builder newDocument(Schema schema) {
        return new Builder(schema);
    }

    public static class Builder {
        private final Schema schema;
        private final Map<String, Schema> schemas = new HashMap<>();

        public Builder(Schema schema) {
            this.schema = requireNonNull(schema);
        }

        public Builder subSchema(Schema schema) {
            this.schemas.put(schema.name(), requireNonNull(schema));
            return this;
        }

        public ObjectValidator build() {
            assertSchemaReferences(schema);
            schemas.values().forEach(this::assertSchemaReferences);
            return new ObjectValidator(this);
        }

        private void assertSchemaReferences(Schema schema) {
            schema.fields().forEach(field -> {
                if (field instanceof Schema.ObjectFieldLazy) {
                    Schema.ObjectFieldLazy objectField = (Schema.ObjectFieldLazy) field;
                    if (!schemas.containsKey(objectField.schemaName())) {
                        throw new InvalidSchemaException(format("No schema configured for lazy object reference '%s'", objectField.schemaName()));
                    }
                }
                if (field instanceof Schema.ObjectField) {
                    Schema.ObjectField objectField = (Schema.ObjectField) field;
                    assertSchemaReferences(objectField.schema());
                }
            });
        }

    }


}
