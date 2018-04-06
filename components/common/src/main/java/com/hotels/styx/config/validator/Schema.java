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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.hotels.styx.config.validator.Constraints.AtLeastOneFieldPresenceConstraint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Declares a layout and constraints for configuration objects.
 *
 * A Schema represents a container for configuration attributes (fields). The
 * configuration layout is similar to that of JSON (https://www.json.org).
 *
 * The fields are name/type pairs. A name uniquely identifies a field within
 * its enclosing container object. The field type declaration specifies what
 * kind of values are allowed for the field.
 *
 * The field types are:
 *
 *  - Elementary types: Integer, Boolean, String
 *  - Container types: Object, List (Json array)
 *
 * Lists can be either lists of objects, or lists of elementary types. It is a
 * known limitation at the moment that nested lists (or multi-dimensional) lists
 * are not supported.
 *
 * An 'object' fields open up a new attribute containment hierarchy (a subobject)
 * whose layout is specified by a separate Schema instance. This subobject schema
 * can either be a named schema reference, or it can be inlined within the object
 * attribute type.
 *
 */
public class Schema {
    private final ImmutableList<Constraint> constraints;
    private final String name;
    private final List<String> fieldNames;
    private final List<Field> fields;
    private final Set<String> optionalFields;
    private final boolean pass;

    /**
     * Field type.
     *
     */
    public enum FieldType {
        STRING,
        INTEGER,
        BOOLEAN,
        OBJECT,
        LIST
    }

    private Schema(Builder builder) {
        this.name = builder.name;
        this.fieldNames = builder.fields.stream().map(Field::name).collect(toList());
        this.fields = new ArrayList<>(builder.fields);
        this.optionalFields = ImmutableSet.copyOf(builder.optionalFields);
        this.constraints = ImmutableList.copyOf(builder.constraints);
        this.pass = builder.pass;
    }

    public Optional<Field> field(String name) {
        return fields.stream()
                .filter(field -> field.name().equals(name))
                .findFirst();
    }

    public Set<String> optionals() {
        return optionalFields;
    }

    public List<String> fieldNames() {
        return ImmutableList.copyOf(fieldNames);
    }

    public List<Field> fields() {
        return ImmutableList.copyOf(fields);
    }

    public List<Constraint> constraints() {
        return ImmutableList.copyOf(constraints);
    }

    public String name() {
        return this.name;
    }

    public boolean isPass() {
        return pass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Schema schema = (Schema) o;
        return Objects.equals(name, schema.name)
                && Objects.equals(fieldNames, schema.fieldNames)
                && Objects.equals(fields, schema.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, fieldNames, fields);
    }

    /**
     * Represents a named schema field type.
     */
    public static class Field {
        private final String name;
        private boolean optional;
        private final FieldValue value;

        public Field(String name, boolean optional, FieldValue value) {
            this.name = requireNonNull(name);
            this.optional = optional;
            this.value = requireNonNull(value);
        }

        public String name() {
            return this.name;
        }

        public FieldValue value() {
            return value;
        }

        public static Field field(String name, FieldValue value) {
            return new Field(name, false, value);
        }

        public static FieldValue integer() {
            return new IntegerField();
        }

        public static FieldValue string() {
            return new StringField();
        }

        public static FieldValue bool() {
            return new BoolField();
        }

        public static FieldValue object(Schema.Builder schema) {
            return new ObjectField(schema.build());
        }

        public static FieldValue object(String schemaName) {
            return new ObjectFieldLazy(schemaName);
        }

        public static FieldValue union(String discriminator) {
            return new DiscriminatedUnionObject(discriminator);
        }

        public static FieldValue list(FieldValue elementType) {
            return new ListField(elementType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Field field = (Field) o;
            return Objects.equals(name, field.name) && Objects.equals(optional, optional);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, optional);
        }
    }

    /**
     * Represents a type of a schema field.
     */
    public static class FieldValue {
        public FieldType type() {
            if (this instanceof IntegerField) {
                return FieldType.INTEGER;
            }

            if (this instanceof StringField) {
                return FieldType.STRING;
            }

            if (this instanceof BoolField) {
                return FieldType.BOOLEAN;
            }

            if (this instanceof ObjectField || this instanceof ObjectFieldLazy || this instanceof DiscriminatedUnionObject) {
                return FieldType.OBJECT;
            }

            if (this instanceof ListField) {
                return FieldType.LIST;
            }

            throw new IllegalStateException("Unknown field type: " + this.getClass() + " " + this);
        }
    }

    /**
     * Integer schema field type.
     */
    public static class IntegerField extends FieldValue {
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
    }

    /**
     * String schema field type.
     */
    public static class StringField extends FieldValue {
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
    }

    /**
     * Boolean schema field type.
     */
    public static class BoolField extends FieldValue {
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
    }

    /**
     * List schema field type.
     */
    public static class ListField extends FieldValue {
        private FieldValue elementType;

        public ListField(FieldValue elementType) {
            this.elementType = requireNonNull(elementType);
        }

        public FieldValue elementType() {
            return elementType;
        }

    }

    /**
     * Object field type with inlined sub-schema.
     */
    public static class ObjectField extends FieldValue {
        private final Schema schema;

        public ObjectField(Schema schema) {
            this.schema = requireNonNull(schema);
        }

        public Schema schema() {
            return schema;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ObjectField that = (ObjectField) o;
            return Objects.equals(schema, that.schema);
        }

        @Override
        public int hashCode() {
            return Objects.hash(schema);
        }
    }

    /**
     * Object field type with a named reference to its schema object.
     */
    public static class ObjectFieldLazy extends FieldValue {
        private final String schemaName;

        public ObjectFieldLazy(String schemaName) {
            this.schemaName = requireNonNull(schemaName);
        }

        public String schemaName() {
            return schemaName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ObjectFieldLazy that = (ObjectFieldLazy) o;
            return Objects.equals(schemaName, that.schemaName);
        }

        @Override
        public int hashCode() {

            return Objects.hash(schemaName);
        }
    }

    /**
     * A union of alternative object layouts chosen by a named discriminator field.
     *
     * The "discriminatorField" contains a name of the object attribute that is
     * used as an object discriminator. This attribute must 1) reside within the
     * same object as the union field itself, and 2) must have a STRING type.
     *
     * The value of the discriminator attribute field is used as a named schema
     * reference when the subobject layout is determined.
     *
     */
    public static class DiscriminatedUnionObject extends FieldValue {

        private final String discriminatorField;

        public DiscriminatedUnionObject(String discriminatorField) {
            this.discriminatorField = requireNonNull(discriminatorField);
        }

        public String discriminatorFieldName() {
            return discriminatorField;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DiscriminatedUnionObject that = (DiscriminatedUnionObject) o;
            return Objects.equals(discriminatorField, that.discriminatorField);
        }

        @Override
        public int hashCode() {
            return Objects.hash(discriminatorField);
        }
    }

    /**
     * Schema builder object.
     */
    public static class Builder {
        private final List<Field> fields;
        private final Set<String> optionalFields = new HashSet<>();
        private final List<Constraint> constraints = new ArrayList<>();

        private String name;
        private boolean pass;

        public Builder(String name) {
            this.name = name;
            this.fields = new ArrayList<>();
        }

        public Builder() {
            this.fields = new ArrayList<>();
            this.name = "";
        }

        public Builder name(String name) {
            this.name = requireNonNull(name);
            return this;
        }

        public Builder pass(boolean pass) {
            this.pass = true;
            return this;
        }

        public Builder field(String name, FieldValue value) {
            ensureNotDuplicate(name);
            this.fields.add(new Field(name, false, value));
            return this;
        }

        public Builder optional(String name, FieldValue value) {
            ensureNotDuplicate(name);
            this.fields.add(new Field(name, true, value));
            this.optionalFields.add(name);
            return this;
        }

        public Builder atLeastOne(Field ...fields) {
            asList(fields).forEach(field -> {
                ensureNotDuplicate(field.name());
                this.fields.add(field);
                this.optionalFields.add(field.name());
            });
            this.constraints.add(new AtLeastOneFieldPresenceConstraint(fields));
            return this;
        }

        private void ensureNotDuplicate(String name) {
            this.fields.stream()
                    .filter(field -> field.name().equals(name))
                    .map(Field::name)
                    .findFirst()
                    .ifPresent((x) -> {
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
