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
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;

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
    private final ImmutableList<Constraint> constraints;
    private final String name;
    private final List<String> fieldNames;
    private final List<Field> fields;
    private final Set<String> optionalFields;
    private final boolean ignore;

    private Schema(Builder builder) {
        this.fieldNames = builder.fields.stream().map(Field::name).collect(toList());
        this.fields = ImmutableList.copyOf(builder.fields);
        this.optionalFields = ImmutableSet.copyOf(builder.optionalFields);
        this.constraints = ImmutableList.copyOf(builder.constraints);
        this.ignore = builder.pass;
        this.name = builder.name.length() > 0 ? builder.name : Joiner.on(", ").join(this.fieldNames);
    }

    public Schema.Builder newBuilder() {
        return new Schema.Builder(this);
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

    private static String message(List<String> parents, String self, JsonNode value) {
        String fullName = Joiner.on(".").join(parents);
        return format("Unexpected field type. Field '%s' should be %s, but it is %s", fullName, self, value.getNodeType());
    }


    /**
     * Represents a schema field type.
     */
    public interface FieldType {
        /**
         * Validates a JsonNode value against this type.  Typically checks that a value
         * can be used as an instance of this type.
         *
         * @param parents A parentage attribute stack.
         * @param parent Value's parent node.
         * @param value Value to be inspected.
         * @param typeExtensions Provides type extensions.
         * @return
         */
        Optional<String> validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions);

        /**
         * Returns an user friendly description of the type that will be
         * used in validation error messages. Therefore the message ought to
         * be user friendly.
         *
         * @return type description.
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
        public Optional<String> validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {

            FieldType routingObjectDefinition = object(
                    field("type", string()),
                    optional("name", string()),
                    optional("tags", list(string())),
                    field("config", union("type"))
            );

            Optional<String> isReference = new StringField().validate(parents, parent, value, typeExtensions);
            if (!isReference.isPresent()) {
                // It passed as a reference:
                return Optional.empty();
            }

            Optional<String> isInlinedObject = routingObjectDefinition.validate(parents, parent, value, typeExtensions);
            if (!isInlinedObject.isPresent()) {
                // It passed as a routing object spec:
                return Optional.empty();
            }

            // Failed
            return Optional.of(message(parents, describe(), value));
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
        public Optional<String> validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            return (value.isInt() || canParseAsInteger(value)) ? Optional.empty() : Optional.of(message(parents, describe(), value));
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
        public Optional<String> validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            return value.isTextual() ? Optional.empty() : Optional.of(message(parents, describe(), value));
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
        public Optional<String> validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            return (value.isBoolean() || canParseAsBoolean(value)) ? Optional.empty() : Optional.of(message(parents, describe(), value));
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

        public FieldType elementType() {
            return elementType;
        }

        @Override
        public Optional<String> validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            return (value.isArray()) ? validateList(parents, parent, value, typeExtensions) : Optional.of(message(parents, describe(), value));
        }

        @Override
        public String describe() {
            return format("LIST (%s)", elementType.describe());
        }

        private Optional<String> validateList(List<String> parents, JsonNode parent, JsonNode list, Function<String, FieldType> lookup) {
            for (int i = 0; i < list.size(); i++) {
                JsonNode entry = list.get(i);

                Optional<String> correctType = elementType.validate(push(parents, format("[%d]", i)), list, entry, lookup);
                if (correctType.isPresent()) {
                    return correctType;
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Map schema field type.
     */
    public static class MapField implements FieldType {
        private final FieldType elementType;

        MapField(FieldType elementType) {
            this.elementType = requireNonNull(elementType);
        }

        public FieldType elementType() {
            return elementType;
        }

        @Override
        public Optional<String> validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            return value.isObject() ? validateMap(parents, parent, value, typeExtensions) : Optional.of(message(parents, describe(), value));
        }

        @Override
        public String describe() {
            return format("MAP (%s)", elementType.describe());
        }

        private Optional<String> validateMap(List<String> parents, JsonNode parent, JsonNode mapNode, Function<String, FieldType> schemas) {
            AtomicReference<Optional<String>> validationError = new AtomicReference<>(Optional.empty());

            mapNode.fieldNames().forEachRemaining(key -> {
                JsonNode entry = mapNode.get(key);
                Optional<String> error = elementType.validate(push(parents, format("%s", key)), mapNode, entry, schemas);

                if (error.isPresent()) {
                    validationError.set(error);
                }
            });
            return validationError.get();
        }

    }

    private static void assertNoUnknownFields(String prefix, Schema schema, List<String> fieldsPresent) {
        Set<String> knownFields = ImmutableSet.copyOf(schema.fieldNames());

        fieldsPresent.forEach(name -> {
            if (!knownFields.contains(name)) {
                throw new SchemaValidationException(format("Unexpected field: '%s.%s'", prefix, name));
            }
        });
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
        public Optional<String> validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            return value.isObject() ? validateObject(parents, parent, value, typeExtensions) : Optional.of(message(parents, describe(), value));
        }

        @Override
        public String describe() {
            return format("OBJECT (%s)", schema.name());
        }

        private Optional<String> validateObject(List<String> parents, JsonNode parent, JsonNode tree, Function<String, FieldType> lookup) {

            if (schema.ignore()) {
                return Optional.empty();
            }

            for (Schema.Field field : schema.fields()) {
                if (isMandatory(schema, field) && tree.get(field.name()) == null) {
                    throw new SchemaValidationException(format("Missing a mandatory field '%s.%s'", Joiner.on(".").join(parents), field.name()));
                }

                if (tree.get(field.name()) != null) {
                    String name = field.name();
                    Optional<String> correctType = field.value().validate(push(parents, name), tree, tree.get(name), lookup);
                    if (correctType.isPresent()) {
                        throw new SchemaValidationException(correctType.get());
                    }
                }
            }

            schema.constraints().forEach(constraint -> {
                if (!constraint.evaluate(schema, tree)) {
                    throw new SchemaValidationException("Schema constraint failed. " + constraint.describe());
                }
            });

            assertNoUnknownFields(Joiner.on(".").join(parents), schema, ImmutableList.copyOf(tree.fieldNames()));

            return Optional.empty();
        }

        private static boolean isMandatory(Schema schema, Schema.Field field) {
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

        public String discriminatorFieldName() {
            return discriminatorField;
        }

        @Override
        public Optional<String> validate(List<String> parents, JsonNode parent, JsonNode value, Function<String, FieldType> typeExtensions) {
            JsonNode type = parent.get(discriminatorField);

            Optional<String> discriminatorType = string().validate(push(parents, discriminatorField), parent, type, typeExtensions);
            if (discriminatorType.isPresent()) {
                String fullName = Joiner.on(".").join(push(parents, discriminatorField));
                return Optional.of(format("Union discriminator '%s': %s", fullName, discriminatorType.get()));
            }

            String typeValue = type.textValue();
            FieldType schema = typeExtensions.apply(typeValue);
            if (schema == null) {
                String fullName = Joiner.on(".").join(parents);
                return Optional.of(format("Unknown schema '%s' in union '%s'. Union type is %s", typeValue, fullName, describe()));
            }

            return schema.validate(parents, parent, value, typeExtensions);
        }

        @Override
        public String describe() {
            return format("UNION (%s)", discriminatorField);
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
