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
package com.hotels.styx.config.schema;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * Field type.
     */
    public enum FieldType {
        STRING,
        INTEGER,
        BOOLEAN,
        OBJECT,
        LIST,
        MAP;
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

            if (this instanceof MapField) {
                return FieldType.MAP;
            }

            throw new IllegalStateException("Unknown field type: " + this.getClass() + " " + this);
        }
    }

    /**
     * Integer schema field type.
     */
    public static class IntegerField extends FieldValue {
    }

    /**
     * String schema field type.
     */
    public static class StringField extends FieldValue {
    }

    /**
     * Boolean schema field type.
     */
    public static class BoolField extends FieldValue {
    }

    /**
     * List schema field type.
     */
    public static class ListField extends FieldValue {
        private final FieldValue elementType;

        ListField(FieldValue elementType) {
            this.elementType = requireNonNull(elementType);
        }

        public FieldValue elementType() {
            return elementType;
        }

    }

    /**
     * Map schema field type.
     */
    public static class MapField extends FieldValue {
        private final FieldValue elementType;

        MapField(FieldValue elementType) {
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

        ObjectField(Schema schema) {
            this.schema = requireNonNull(schema);
        }

        public Schema schema() {
            return schema;
        }
    }

    /**
     * Object field type with a named reference to its schema object.
     */
    public static class ObjectFieldLazy extends FieldValue {
        private final String schemaName;

        ObjectFieldLazy(String schemaName) {
            this.schemaName = requireNonNull(schemaName);
        }

        public String schemaName() {
            return schemaName;
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
    public static class DiscriminatedUnionObject extends FieldValue {

        private final String discriminatorField;

        DiscriminatedUnionObject(String discriminatorField) {
            this.discriminatorField = requireNonNull(discriminatorField);
        }

        public String discriminatorFieldName() {
            return discriminatorField;
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
