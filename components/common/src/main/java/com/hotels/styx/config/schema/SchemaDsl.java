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

import java.util.stream.Stream;

/**
 * SchemaDsl class provides a domain specific language for constructing Schema declarations.
 *
 * The DSL consist of static methods for building constituent components for the Schema
 * declaration.
 */
public final class SchemaDsl {
    private SchemaDsl() {
    }

    /**
     * Declares a schema.
     *
     * @param schemaDirectives Such as fields, constraints, and other directives.
     * @return a schema object
     */
    public static Schema schema(SchemaDirective... schemaDirectives) {
        Schema.Builder builder = new Schema.Builder();
        Stream.of(schemaDirectives).forEach(builder::add);
        return builder.build();
    }

    /**
     * Declares a mandatory field.
     * @param name   The field name.
     * @param value  The vield value type.
     * @return       The Field class instance
     */
    public static Schema.Field field(String name, Schema.FieldType value) {
        return new Schema.Field(name, false, value);
    }

    /**
     * Declares an optional field.
     * @param name   The field name.
     * @param value  The vield value type.
     * @return       The Field class instance
     */
    public static Schema.Field optional(String name, Schema.FieldType value) {
        return new Schema.Field(name, true, value);
    }

    /**
     * An integer field value type.
     *
     * @return A FieldType instance.
     */
    public static Schema.FieldType integer() {
        return new Schema.IntegerField();
    }

    /**
     * A string field value type.
     *
     * @return A FieldType instance.
     */
    public static Schema.FieldType string() {
        return new Schema.StringField();
    }

    /**
     * A boolean field value type.
     *
     * @return A FieldType instance.
     */
    public static Schema.FieldType bool() {
        return new Schema.BoolField();
    }

    /**
     * An object field value type.
     *
     * An object opens up a nested scope for additional attribute fields.
     * The attributes are passed in as a vararg list argument. In addition to
     * arguments, any schema constraints are passed in with the list.
     *
     * For example, to declare an object with a mix of mandatory and optional
     * fields:
     *
     * <pre>
     * object(
     *     field("x", integer()),
     *     field("y", integer()),
     *     optional("name", string())
     * )
     * </pre>
     *
     * For example, to declare an object with `atLeastOne` constraint:
     *
     * <pre>
     * object(
     *     optional("a", integer()),
     *     optional("b", integer()),
     *     atLeastOne("a", "b")
     * )
     * </pre>
     *
     * For example, use `pass()` directive to mark the object as opaque.
     * In this case the object's layout is unknown and no attempt is made
     * to validate such object.
     *
     * <pre>
     * object(
     *     pass()
     * )
     * </pre>
     *
     *
     * @return A FieldType instance.
     */
    public static Schema.FieldType object(SchemaDirective... schemaDirectives) {
        Schema.Builder builder = new Schema.Builder();
        Stream.of(schemaDirectives).forEach(builder::add);
        return new Schema.ObjectField(builder.build());
    }

    /**
     * A Styx routing object.
     *
     * This can be a named reference to another routing object, or an inline
     * declaration.
     *
     * @return A validator for Styx routing object.
     */
    public static Schema.FieldType routingObject() {
        return new Schema.RoutingObjectSpec();
    }



    /**
     * A discriminated union type (https://en.wikipedia.org/wiki/Tagged_u…) that allows the attribute
     * value to take the form of different object types.
     *
     * The discriminated union consist of three parts:
     *
     *   1) a discriminator (or tag) field
     *   2) an union attribute
     *   3) an enclosing object that contains both the tag and union attribute
     *
     * A discriminator is a `string` field that assumes the value of the union type.
     *
     * The `union` takes the name of the discriminator field as an argument. This field must reside within
     * the same enclosing object as the union field itself, and must have a STRING type.
     *
     * If the discriminator field doesn't exist, or does not assume a valid type name,
     * the validation will end up in a runtime exception. Use a `DocumentFormat` class
     * to ensure discriminator field refers to a valid field name.
     *
     * In the following example, `httpPipeline` is the enclosing object that contains
     * both the discriminator attribute ("type") and the union attribute ("config").
     * The discriminator attribute has a type of `string`, and valid values for it are
     * "ProxyTo" or "Redirection". The union field ("config") takes the discriminator
     * attribute name as an argument ("type").
     *
     * <pre>
     * DocumentFormat validator = newDocument()
     *     .typeExtension("ProxyTo", schema(
     *         field("id", string()),
     *         field("destination", string())
     *     ))
     *     .typeExtension("Redirection", schema(
     *         field("status", integer()),
     *         field("location", string())
     *     ))
     *     .rootSchema(schema(
     *         field("httpPipeline", object(
     *             field("type", string()),
     *             field("config", union("type"))
     *         ))
     *     ))
     *     .build();
     * </pre>
     *
     * This schema will now correctly accept both inputs:
     *
     * <pre>
     *     httpPipeline:
     *       type: ProxyTo
     *       config:
     *         id: foo
     *         destination: bar
     * </pre>
     *
     * and
     *
     * <pre>
     *     httpPipeline:
     *       type: Redirection
     *       config:
     *         status: 301
     *         location: http://localhost:8080/bar
     * </pre>
     *
     * @param discriminator - the discriminator field name
     * @return an union field value
     */
    public static Schema.FieldType union(String discriminator) {
        return new Schema.DiscriminatedUnionObject(discriminator);
    }

    /**
     * A list of values.
     *
     * Lists can be either lists of objects, or lists of elementary types. It is a
     * known limitation at the moment that nested lists (or multi-dimensional) lists
     * are not supported.
     *
     * Another constraint is that the list elements must all have the same type.
     * The `Schema` implementation doesn't currently support lists of mixed element
     * types.
     *
     * @param elementType A type of list entries.
     * @return a list field value type
     */
    public static Schema.FieldType list(Schema.FieldType elementType) {
        return new Schema.ListField(elementType);
    }

    /**
     * Map schema field type.
     *
     * A map declares a JSON object type whose field names are treated as
     * arbitrary (string) keys associated with some elementary or object type.
     */
    public static Schema.FieldType map(Schema.FieldType elementType) {
        return new Schema.MapField(elementType);
    }

    /**
     * A directive to mark the object layout as `opaque`.
     *
     * @return an OpaqueSchema directive
     */
    public static OpaqueSchema opaque() {
        return new OpaqueSchema();
    }

    /**
     * A constraint to tell that at least one of optional fields should be present.
     * For example:
     * <pre>
     * object(
     *     optional("a", integer()),
     *     optional("b", integer()),
     *     atLeastOne("a", "b")
     * )
     * </pre>
     *
     * Note that it is important to mark the alternative fields as optional. The
     * `atLeastOne` constraint is not particularly useful for mandatory fields.  This
     * is because the mandatory fields are supposed to always be present.
     *
     * @param fieldNames List of field names.
     * @return An "at least one" constraint.
     */
    public static Constraint atLeastOne(String... fieldNames) {
        return new AtLeastOneFieldPresenceConstraint(fieldNames);
    }

}
