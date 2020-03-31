/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.hotels.styx.common.Collections.listOf;
import static com.hotels.styx.common.Collections.setOf;
import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.list;
import static com.hotels.styx.config.schema.SchemaDsl.object;
import static com.hotels.styx.config.schema.SchemaDsl.optional;
import static com.hotels.styx.config.schema.SchemaDsl.string;
import static com.hotels.styx.config.schema.SchemaDsl.union;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Declares a layout and constraints for configuration objects.
 * <p>
 * A Schema represents a container for configuration attributes (fields). The
 * configuration layout is similar to that of JSON (https://www.json.org).
 * <p>
 * The fields are name/type pairs. A name uniquely identifies a field within
 * its enclosing container object. The field type declaration specifies what
 * kind of values are allowed for the field.
 * <p>
 * The field types are:
 * <p>
 * - Elementary types: Integer, Boolean, String
 * - Container types: Object, List (Json array)
 * <p>
 * Lists can be either lists of objects, or lists of elementary types. It is a
 * known limitation at the moment that nested lists (or multi-dimensional) lists
 * are not supported.
 * <p>
 * An 'object' fields open up a new attribute containment hierarchy (a subobject)
 * whose layout is specified by a separate Schema instance. This subobject schema
 * can either be a named schema reference, or it can be inlined within the object
 * attribute type.
 */
public class Schema {
    private final List<Constraint> constraints;
    private final String name;
    private final List<String> fieldNames;
    private final List<Field> fields;
    private final Set<String> optionalFields;
    private final boolean ignore;

    private Schema(Builder builder) {
        this.fieldNames = builder.fields.stream().map(Field::name).collect(toList());
        this.fields = listOf(builder.fields);
        this.optionalFields = setOf(builder.optionalFields);
        this.constraints = listOf(builder.constraints);
        this.ignore = builder.pass;
        this.name = builder.name.length() > 0 ? builder.name : String.join(", ", this.fieldNames);
    }

    private static String sanitise(String string) {
        return string
                .replaceFirst("^\\.", "")
                .replaceAll("\\.\\[", "[");
    }

    public Set<String> optionalFields() {
        return optionalFields;
    }

    public List<String> fieldNames() {
        return fieldNames;
    }

    public List<Field> fields() {
        return fields;
    }

    public List<Constraint> constraints() {
        return constraints;
    }

    public String name() {
        return name;
    }

    public boolean ignore() {
        return ignore;
    }

    private static List<String> push(List<String> currentList, String element) {
        List<String> newList = new ArrayList<>(currentList);
        newList.add(element);
        return newList;
    }

    private static List<String> pop(List<String> currentList) {
        if (currentList.size() > 0) {
            List<String> newList = new ArrayList<>(currentList);
            newList.remove(newList.size() - 1);
            return newList;
        } else {
            return currentList;
        }
    }

    private static String top(List<String> currentList) {
        if (currentList.size() > 0) {
            return currentList.get(currentList.size() - 1);
        } else {
            return "";
        }
    }

    private static String message(List<String> parents, String self, JsonNode value) {
        String fullName = sanitise(String.join(".", parents));
        return format("Unexpected field type. Field '%s' should be %s, but it is %s", fullName, self, value.getNodeType());
    }

    private static void assertNoUnknownFields(String prefix, Schema schema, List<String> fieldsPresent) {
        Set<String> knownFields = setOf(schema.fieldNames());

        fieldsPresent.forEach(name -> {
            if (!knownFields.contains(name)) {
                throw new SchemaValidationException(format("Unexpected field: '%s'", sanitise(prefix + "." + name)));
            }
        });
    }


    /**
     * Represents a schema field type.
     */
    public interface FieldType {
        /**
         * Validates a JsonNode value against this type.  Typically checks that a value
         * can be used as an instance of this type.
         *
         *
         * @param parents a parentage attribute stack
         * @param parent value's parent node
         * @param value value to be inspected
         * @param typeExtensions provides type extensions
         * @throws SchemaValidationException if validation fails
         */
        void validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) throws SchemaValidationException;

        /**
         * Returns an user friendly description of the type that will be
         * used in validation error messages. Therefore the message ought to
         * be user friendly.
         *
         * @return type description
         */
        String describe();
    }

    /**
     * Represents a named schema field type.
     */
    public static class Field implements SchemaDirective {
        private final String name;
        private final boolean optional;
        private final FieldType value;

        Field(String name, boolean optional, FieldType value) {
            this.name = requireNonNull(name);
            this.optional = optional;
            this.value = requireNonNull(value);
        }

        public String name() {
            return this.name;
        }

        public boolean optional() {
            return optional;
        }

        public FieldType value() {
            return value;
        }
    }

    /**
     * Represents Styx routing object specification, which is either
     * a named reference to an already declared object, or a new
     * object declaration.
     */
    public static class RoutingObjectSpec implements FieldType {

        @Override
        public void validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {

            FieldType routingObjectDefinition = object(
                    field("type", string()),
                    optional("name", string()),
                    optional("tags", list(string())),
                    field("config", union("type"))
            );

            try {
                new StringField().validate(parents, parent, value, typeExtensions);
                return;
            } catch (SchemaValidationException e) {
                // Is not reference type - PASS
            }

            routingObjectDefinition.validate(parents, parent, value, typeExtensions);
        }

        @Override
        public String describe() {
            return "ROUTING-OBJECT-SPEC";
        }
    }

    /**
     * Integer schema field type.
     */
    public static class IntegerField implements FieldType {
        @Override
        public void validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            if (!value.isInt() && !canParseAsInteger(value)) {
                throw new SchemaValidationException(message(parents, describe(), value));
            }
        }

        @Override
        public String describe() {
            return "INTEGER";
        }

        private static boolean canParseAsInteger(JsonNode value) {
            try {
                parseInt(value.textValue());
                return true;
            } catch (NumberFormatException cause) {
                return false;
            }
        }
    }

    /**
     * String schema field type.
     */
    public static class StringField implements FieldType {
        @Override
        public void validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            if (!value.isTextual()) {
                throw new SchemaValidationException(message(parents, describe(), value));
            }
        }

        @Override
        public String describe() {
            return "STRING";
        }

    }

    /**
     * Boolean schema field type.
     */
    public static class BoolField implements FieldType {
        private static final Pattern YAML_BOOLEAN_VALUES = Pattern.compile("(?i)true|false");

        @Override
        public void validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            if (!value.isBoolean() && !canParseAsBoolean(value)) {
                throw new SchemaValidationException(message(parents, describe(), value));
            }
        }

        @Override
        public String describe() {
            return "BOOLEAN";
        }

        private static boolean canParseAsBoolean(JsonNode value) {
            return YAML_BOOLEAN_VALUES.matcher(value.asText()).matches();
        }
    }

    /**
     * List schema field type.
     */
    public static class ListField implements FieldType {
        private final FieldType elementType;

        ListField(FieldType elementType) {
            this.elementType = requireNonNull(elementType);
        }

        @Override
        public void validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            if (!value.isArray()) {
                throw new SchemaValidationException(message(parents, describe(), value));
            }

            for (int i = 0; i < value.size(); i++) {
                elementType.validate(push(pop(parents), format("%s[%d]", top(parents), i)), value, value.get(i), typeExtensions);
            }
        }

        @Override
        public String describe() {
            return format("LIST(%s)", elementType.describe());
        }

    }

    /**
     * Or field type is an alternative of two possible types.
     */
    public static class OrField implements FieldType {
        private final FieldType alt1;
        private final FieldType alt2;

        public OrField(FieldType alt1, FieldType alt2) {
            this.alt1 = alt1;
            this.alt2 = alt2;
        }

        @Override
        public void validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            try {
                alt1.validate(parents, parent, value, typeExtensions);
            } catch (SchemaValidationException e) {
                try {
                    alt2.validate(parents, parent, value, typeExtensions);
                } catch (SchemaValidationException e2) {
                    throw new SchemaValidationException(message(parents, describe(), value));
                }
            }
        }

        @Override
        public String describe() {
            return format("OR(%s, %s)", alt1.describe(), alt2.describe());
        }
    }

    public Builder newBuilder() {
        return new Builder(this);
    }

    /**
     * Map schema field type.
     */
    public static class MapField implements FieldType {
        private final FieldType elementType;

        MapField(FieldType elementType) {
            this.elementType = requireNonNull(elementType);
        }

        @Override
        public void validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            if (!value.isObject()) {
                throw new SchemaValidationException(message(parents, describe(), value));
            }

            value.fieldNames().forEachRemaining(key -> elementType.validate(push(parents, format("[%s]", key)), value, value.get(key), typeExtensions));
        }

        @Override
        public String describe() {
            return format("MAP(%s)", elementType.describe());
        }

    }

    /**
     * Object field type with inlined sub-schema.
     */
    public static class ObjectField implements FieldType {
        private final Schema schema;

        ObjectField(Schema schema) {
            this.schema = requireNonNull(schema);
        }

        public Schema schema() {
            return schema;
        }

        @Override
        public void validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            if (!value.isObject()) {
                throw new SchemaValidationException(message(parents, describe(), value));
            }

            if (schema.ignore()) {
                return;
            }

            for (Field field : schema.fields()) {
                if (isMandatory(schema, field) && value.get(field.name()) == null) {
                    throw new SchemaValidationException(format(
                            "Missing a mandatory field '%s'",
                            sanitise(String.join(".", push(parents, field.name())))));
                }

                if (value.get(field.name()) != null) {
                    String name = field.name();
                    field.value().validate(push(parents, name), value, value.get(name), typeExtensions);
                }
            }

            schema.constraints().forEach(constraint -> {
                if (!constraint.evaluate(schema, value)) {
                    throw new SchemaValidationException("Schema constraint failed. " + constraint.describe());
                }
            });

            assertNoUnknownFields(String.join(".", parents), schema, listOf(value.fieldNames()));
        }

        @Override
        public String describe() {
            return format("OBJECT(%s)", schema.name());
        }

        private static boolean isMandatory(Schema schema, Field field) {
            return !schema.optionalFields().contains(field.name());
        }

    }

    /**
     * A union of alternative object layouts chosen by a named discriminator field.
     * <p>
     * The "discriminatorField" contains a name of the object attribute that is
     * used as an object discriminator. This attribute must 1) reside within the
     * same object as the union field itself, and 2) must have a STRING type.
     * <p>
     * The value of the discriminator attribute field is used as a named schema
     * reference when the subobject layout is determined.
     */
    public static class DiscriminatedUnionObject implements FieldType {

        private final String discriminatorField;

        DiscriminatedUnionObject(String discriminatorField) {
            this.discriminatorField = requireNonNull(discriminatorField);
        }

        String discriminatorFieldName() {
            return discriminatorField;
        }

        @Override
        public void validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            JsonNode type = parent.get(discriminatorField);

            try {
                string().validate(push(parents, discriminatorField), parent, type, typeExtensions);
            } catch (SchemaValidationException e) {
                String fullName = String.join(".", push(parents, discriminatorField));
                throw new SchemaValidationException(format("Union discriminator '%s': %s", fullName, e.getMessage()));
            }

            String typeValue = type.textValue();
            FieldType schema = typeExtensions.apply(typeValue);
            if (schema == null) {
                String fullName = String.join(".", parents);
                throw new SchemaValidationException(format("Unknown union discriminator type '%s' for union '%s'. Union type is %s", typeValue, fullName, describe()));
            }

            schema.validate(parents, parent, value, typeExtensions);
        }

        @Override
        public String describe() {
            return format("UNION(%s)", discriminatorField);
        }
    }

    /**
     * Schema builder object.
     */
    public static class Builder {
        private final List<Field> fields = new ArrayList<>();
        private final Set<String> optionalFields = new HashSet<>();
        private final List<Constraint> constraints = new ArrayList<>();

        private String name = "";
        private boolean pass;

        public Builder() {
        }

        public Builder(Schema schema) {
            this.name = schema.name;
            this.pass = schema.ignore;
            this.fields.addAll(schema.fields);
            this.optionalFields.addAll(schema.optionalFields);
            this.constraints.addAll(schema.constraints);
        }

        public Builder name(String name) {
            this.name = requireNonNull(name);
            return this;
        }

        private void addField(Field field) {
            ensureNotDuplicate(field.name());
            this.fields.add(field);
            if (field.optional()) {
                this.optionalFields.add(field.name());
            }
        }

        public Builder add(SchemaDirective element) {
            if (element instanceof Field) {
                addField((Field) element);
            } else if (element instanceof OpaqueSchema) {
                this.pass = true;
            } else if (element instanceof Constraint) {
                this.constraints.add((Constraint) element);
            }
            return this;
        }

        private void ensureNotDuplicate(String name) {
            fields.stream()
                    .filter(field -> field.name().equals(name))
                    .map(Field::name)
                    .findFirst()
                    .ifPresent(x -> {
                        throw new InvalidSchemaException(format("Duplicate field name '%s' in schema '%s'", name, this.name));
                    });
        }

        public Schema build() {
            Set<String> stringFieldNames = fields.stream()
                    .filter(field -> field.value() instanceof StringField)
                    .map(Field::name)
                    .collect(toSet());

            Set<String> attributeNames = fields.stream()
                    .map(Field::name)
                    .collect(toSet());

            List<DiscriminatedUnionObject> unions = fields.stream()
                    .filter(field -> field.value() instanceof DiscriminatedUnionObject)
                    .map(field -> (DiscriminatedUnionObject) field.value())
                    .collect(toList());

            unions.forEach(field -> {
                String discriminator = field.discriminatorFieldName();

                if (!attributeNames.contains(discriminator)) {
                    throw new InvalidSchemaException(format("Discriminator attribute '%s' not present.", discriminator));
                }

                if (!stringFieldNames.contains(discriminator)) {
                    throw new InvalidSchemaException(format("Discriminator attribute '%s' must be a string (but it is not)", discriminator));
                }

            });

            return new Schema(this);
        }
    }

}
