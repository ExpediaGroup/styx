package com.hotels.styx.config.validator;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class Schema {
    private final String name;
    private final List<Field> fields;
    private final Map<String, Field> fieldsByName;

    private Schema(Builder builder) {
        this.name = builder.name;
        this.fields = ImmutableList.copyOf(builder.fields);
        this.fieldsByName = this.fields.stream()
                .collect(Collectors.toMap(Field::name, a -> a));
    }

    public static Schema.Builder newSchema(String name) {
        return new Schema.Builder(name);
    }

    public Field field(String name) {
        return fieldsByName.get(name);
    }

    public List<Field> fields() {
        return fields;
    }

    public String name() {
        return this.name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schema that = (Schema) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(fields, that.fields) &&
                Objects.equals(fieldsByName, that.fieldsByName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, fields, fieldsByName);
    }

    public static class Field {
        private final String name;
        private boolean optional;

        public Field(String name, boolean optional) {
            this.name = name;
            this.optional = optional;
        }

        public String name() {
            return this.name;
        }

        public boolean isObject() {
            return this instanceof ObjectField || this instanceof ObjectFieldLazy;
        }

        public static Field integer(String name) {
            return new IntegerField(name, false);
        }

        public static Field string(String name) {
            return new StringField(name, false);
        }

        public static Field object(String name, Schema schema) {
            return new ObjectField(name, false, schema);
        }

        public static Field object(String name, String schemaName) {
            return new ObjectFieldLazy(name, false, schemaName);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Field field = (Field) o;
            return Objects.equals(name, field.name) && Objects.equals(optional, optional);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, optional);
        }

        public static Field union(String name, String discriminator) {
            return new DiscriminatedUnionObject(name, false, discriminator);
        }
    }

    public static class IntegerField extends Field {
        public IntegerField(String name, boolean optional) {
            super(name, optional);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
    }

    public static class StringField extends Field {
        public StringField(String name, boolean optional) {
            super(name, optional);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
    }

    public static class ObjectField extends Field {
        private final Schema schema;

        public ObjectField(String name, boolean optional, Schema schema) {
            super(name, optional);
            this.schema = requireNonNull(schema);
        }

        public Schema schema() {
            return schema;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ObjectField that = (ObjectField) o;
            return Objects.equals(schema, that.schema);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), schema);
        }
    }

    public static class ObjectFieldLazy extends Field {
        private final String schemaName;

        public ObjectFieldLazy(String name, boolean optional, String schemaName) {
            super(name, optional);
            this.schemaName = requireNonNull(schemaName);
        }

        public String schemaName() {
            return schemaName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ObjectFieldLazy that = (ObjectFieldLazy) o;
            return Objects.equals(schemaName, that.schemaName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), schemaName);
        }
    }

    public static class DiscriminatedUnionObject extends Field {

        private final String discriminatorField;

        public DiscriminatedUnionObject(String name, boolean optional, String discriminatorField) {
            super(name, optional);
            this.discriminatorField = requireNonNull(discriminatorField);
        }

        public String discriminatorFieldName() {
            return discriminatorField;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            DiscriminatedUnionObject that = (DiscriminatedUnionObject) o;
            return Objects.equals(discriminatorField, that.discriminatorField);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), discriminatorField);
        }
    }

    public static class Builder {
        private final String name;
        private final List<Field> fields;

        public Builder(String name) {
            this.name = name;
            this.fields = new ArrayList<>();
        }

        public Builder field(Field myInt) {
            this.fields.add(myInt);
            return this;
        }

        public Schema build() {
            Set<String> stringFieldNames = fields.stream()
                    .filter(field -> field instanceof StringField)
                    .map(Field::name)
                    .collect(toSet());

            Set<String> attributeNames = fields.stream()
                    .map(Field::name)
                    .collect(toSet());

            List<DiscriminatedUnionObject> unions = fields.stream()
                    .filter(field -> field instanceof DiscriminatedUnionObject)
                    .map(field -> (DiscriminatedUnionObject) field)
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
