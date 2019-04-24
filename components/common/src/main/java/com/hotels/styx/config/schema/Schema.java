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

    /**
     * Represents a named schema field type.
     */
    public static class Field implements SchemaDirective {
        private final String name;
        private final boolean optional;
        private final FieldValue value;

        Field(String name, boolean optional, FieldValue value) {
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

        public FieldValue value() {
            return value;
        }
    }


    /**
     * Represents a type of a schema field.
     */
    public interface FieldValue {
        Optional<String> isCorrectType(List<String> parents, JsonNode parent, JsonNode element, Function<String, FieldValue> schemas);

        String describe();
    }

    /**
     * Represents Styx routing object specification, which is either
     * a named reference to an already declared object, or a new
     * object declaration.
     */
    public static class RoutingObjectSpec implements FieldValue {

        @Override
        public Optional<String> isCorrectType(List<String> parents, JsonNode parent, JsonNode element, Function<String, FieldValue> schemas) {
            StringField ref = new StringField();

            FieldValue routingObjectDefinition = object(
                    field("type", string()),
                    optional("name", string()),
                    optional("tags", list(string())),
                    field("config", union("type"))
            );

            Optional<String> correctType = ref.isCorrectType(parents, parent, element, schemas);

            if (!correctType.isPresent()) {
                // It passed as a reference:
                return Optional.empty();
            }

            Optional<String> correctType1 = routingObjectDefinition.isCorrectType(parents, parent, element, schemas);
            if (!correctType1.isPresent()) {
                // It passed as a routing object spec:
                return Optional.empty();
            }

            // Failed
            return message(parents, element);
        }

        private Optional<String> message(List<String> parents, JsonNode tree) {
            String fullName = Joiner.on(".").join(parents);
            return Optional.of(format("Unexpected field type. Field '%s' should be %s, but it is %s", fullName, describe(), tree.getNodeType()));
        }

        @Override
        public String describe() {
            return "ROUTING-OBJECT-SPEC";
        }
    }

    /**
     * Integer schema field type.
     */
    public static class IntegerField implements FieldValue {
        @Override
        public Optional<String> isCorrectType(List<String> parents, JsonNode parent, JsonNode element, Function<String, FieldValue> schemas) {
            return (element.isInt() || canParseAsInteger(element)) ? Optional.empty() : message(parents, element);
        }

        @Override
        public String describe() {
            return "INTEGER";
        }

        private Optional<String> message(List<String> parents, JsonNode tree) {
            String fullName = Joiner.on(".").join(parents);

            return Optional.of(format("Unexpected field type. Field '%s' should be INTEGER, but it is %s", fullName, tree.getNodeType()));
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
    public static class StringField implements FieldValue {
        @Override
        public Optional<String> isCorrectType(List<String> parents, JsonNode parent, JsonNode element, Function<String, FieldValue> schemas) {
            return element.isTextual() ? Optional.empty() : message(parents, element);
        }

        @Override
        public String describe() {
            return "STRING";
        }

        private Optional<String> message(List<String> parents, JsonNode tree) {
            String fullName = Joiner.on(".").join(parents);

            return Optional.of(format("Unexpected field type. Field '%s' should be STRING, but it is %s", fullName, tree.getNodeType()));
        }
    }

    /**
     * Boolean schema field type.
     */
    public static class BoolField implements FieldValue {
        private static final Pattern YAML_BOOLEAN_VALUES = Pattern.compile("(?i)true|false");

        @Override
        public Optional<String> isCorrectType(List<String> parents, JsonNode parent, JsonNode element, Function<String, FieldValue> schemas) {
            return (element.isBoolean() || canParseAsBoolean(element)) ? Optional.empty() : message(parents, element);
        }

        @Override
        public String describe() {
            return "BOOLEAN";
        }

        private Optional<String> message(List<String> parents, JsonNode tree) {
            String fullName = Joiner.on(".").join(parents);
            return Optional.of(format("Unexpected field type. Field '%s' should be BOOLEAN, but it is %s", fullName, tree.getNodeType()));
        }

        private static boolean canParseAsBoolean(JsonNode value) {
            return YAML_BOOLEAN_VALUES.matcher(value.asText()).matches();
        }
    }

    /**
     * List schema field type.
     */
    public static class ListField implements FieldValue {
        private final FieldValue elementType;

        ListField(FieldValue elementType) {
            this.elementType = requireNonNull(elementType);
        }

        public FieldValue elementType() {
            return elementType;
        }

        @Override
        public Optional<String> isCorrectType(List<String> parents, JsonNode parent, JsonNode element, Function<String, FieldValue> schemas) {
            return (element.isArray()) ? validateList(parents, parent, element, schemas) : message(parents, element);
        }

        @Override
        public String describe() {
            return format("LIST (%s)", elementType.describe());
        }

        private Optional<String> message(List<String> parents, JsonNode tree) {
            String fullName = Joiner.on(".").join(parents);
            return Optional.of(format("Unexpected field type. Field '%s' should be LIST (%s), but it is %s", fullName, elementType.describe(), tree.getNodeType()));
        }

        private Optional<String> validateList(List<String> parents, JsonNode parent, JsonNode list, Function<String, FieldValue> lookup) {
            for (int i = 0; i < list.size(); i++) {
                JsonNode entry = list.get(i);

                Optional<String> correctType = elementType.isCorrectType(push(parents, format("[%d]", i)), list, entry, lookup);
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
    public static class MapField implements FieldValue {
        private final FieldValue elementType;

        MapField(FieldValue elementType) {
            this.elementType = requireNonNull(elementType);
        }

        public FieldValue elementType() {
            return elementType;
        }

        @Override
        public Optional<String> isCorrectType(List<String> parents, JsonNode parent, JsonNode mapNode, Function<String, FieldValue> schemas) {
            AtomicReference<Optional<String>> success = new AtomicReference<>(Optional.empty());

            mapNode.fieldNames().forEachRemaining(key -> {
                JsonNode entry = mapNode.get(key);
                Optional<String> correctType = elementType.isCorrectType(push(parents, format("%s", key)), mapNode, entry, schemas);

                if (correctType.isPresent()) {
                    success.set(correctType);
                }
            });
            return success.get();
        }

        @Override
        public String describe() {
            return format("MAP (%s)", elementType.describe());
        }

    }

    /**
     * Object field type with inlined sub-schema.
     */
    public static class ObjectField implements FieldValue {
        private final Schema schema;

        ObjectField(Schema schema) {
            this.schema = requireNonNull(schema);
        }

        public Schema schema() {
            return schema;
        }

        @Override
        public Optional<String> isCorrectType(List<String> parents, JsonNode parent, JsonNode element, Function<String, FieldValue> schemas) {
            return element.isObject() ? validateObject(parents, parent, element, schemas) : message(parents, element);
        }

        @Override
        public String describe() {
            return format("OBJECT (%s)", schema.name());
        }

        private Optional<String> message(List<String> parents, JsonNode tree) {
            String fullName = Joiner.on(".").join(parents);
            return Optional.of(format("Unexpected field type. Field '%s' should be OBJECT ('%s'), but it is %s", fullName, this.schema.name(), tree.getNodeType()));
        }

        private Optional<String> validateObject(List<String> parents, JsonNode parent, JsonNode tree, Function<String, FieldValue> lookup) {

            if (schema.ignore()) {
                return Optional.empty();
            }

            for (Schema.Field field : schema.fields()) {
                if (isMandatory(schema, field) && tree.get(field.name()) == null) {
                    throw new SchemaValidationException(format("Missing a mandatory field '%s.%s'", Joiner.on(".").join(parents), field.name()));
                }

                if (tree.get(field.name()) != null) {
                    String name = field.name();
                    Optional<String> correctType = field.value().isCorrectType(push(parents, name), tree, tree.get(name), lookup);
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

    private static void assertNoUnknownFields(String prefix, Schema schema, List<String> fieldsPresent) {
        Set<String> knownFields = ImmutableSet.copyOf(schema.fieldNames());

        fieldsPresent.forEach(name -> {
            if (!knownFields.contains(name)) {
                throw new SchemaValidationException(format("Unexpected field: '%s.%s'", prefix, name));
            }
        });
    }

    static List<String> push(List<String> currentList, String element) {
        ArrayList<String> newList = new ArrayList<>(currentList);
        newList.add(element);
        return newList;
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
    public static class DiscriminatedUnionObject implements FieldValue {

        private final String discriminatorField;

        DiscriminatedUnionObject(String discriminatorField) {
            this.discriminatorField = requireNonNull(discriminatorField);
        }

        public String discriminatorFieldName() {
            return discriminatorField;
        }

        @Override
        public Optional<String> isCorrectType(List<String> parents, JsonNode parent, JsonNode element, Function<String, FieldValue> schemas) {
            JsonNode type = parent.get(discriminatorField);

            Optional<String> discriminatorType = string().isCorrectType(push(parents, discriminatorField), parent, type, schemas);
            if (discriminatorType.isPresent()) {
                String fullName = Joiner.on(".").join(push(parents, discriminatorField));
                return Optional.of(format("Union discriminator '%s': %s", fullName, discriminatorType.get()));
            }

            String typeValue = type.textValue();
            FieldValue schema = schemas.apply(typeValue);
            if (schema == null) {
                String fullName = Joiner.on(".").join(parents);
                return Optional.of(format("Unknown schema '%s' in union '%s'. Union type is %s", typeValue, fullName, describe()));
            }

            return schema.isCorrectType(parents, parent, element, schemas);
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
