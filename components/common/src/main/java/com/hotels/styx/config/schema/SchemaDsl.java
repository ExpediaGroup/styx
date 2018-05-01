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
    public static Schema.Field field(String name, Schema.FieldValue value) {
        return new Schema.Field(name, false, value);
    }

    /**
     * Declares an optional field.
     * @param name   The field name.
     * @param value  The vield value type.
     * @return       The Field class instance
     */
    public static Schema.Field optional(String name, Schema.FieldValue value) {
        return new Schema.Field(name, true, value);
    }

    /**
     * An integer field value type.
     *
     * @return A FieldValue instance.
     */
    public static Schema.FieldValue integer() {
        return new Schema.IntegerField();
    }

    /**
     * A string field value type.
     *
     * @return A FieldValue instance.
     */
    public static Schema.FieldValue string() {
        return new Schema.StringField();
    }

    /**
     * A boolean field value type.
     *
     * @return A FieldValue instance.
     */
    public static Schema.FieldValue bool() {
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
     * @return A FieldValue instance.
     */
    public static Schema.FieldValue object(SchemaDirective... schemaDirectives) {
        Schema.Builder builder = new Schema.Builder();
        Stream.of(schemaDirectives).forEach(builder::add);
        return new Schema.ObjectField(builder.build());
    }

    /**
     * An object field value type.
     *
     * The object layout is declared elsewhere and it is determined via a named reference.
     * This feature is used by a DocumentFormat class which validates fasterxml JsonNode
     * objects against its document schema specification.
     *
     * @param schemaName  Schema name
     * @return An object field value type
     */
    public static Schema.FieldValue object(String schemaName) {
        return new Schema.ObjectFieldLazy(schemaName);
    }

    /**
     * A choice of alternative object layouts determined by a named discriminator field.
     *
     * The `discriminator` parameter is a name of the field that determines the
     * union layout type. This field must 1) reside within the same object as
     * the union field itself, and 2) must have a STRING type.
     *
     * The actual usage depends on the consumer of the Schema objects. The `DocumentFormat`
     * class for example, specifies the unions as follows:
     *
     * <pre>
     * DocumentFormat validator = newDocument()
     *     .subSchema("ProxyTo", schema(
     *         field("id", string()),
     *         field("destination", string())
     *     ))
     *     .subSchema("Redirection", schema(
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
     * In this example the `httpPipeline.config` attribute can refer to, depending on the
     * `type` attribute, either `ProxyTo` or `Redirection` object types.
     *
     * @param discriminator - the discriminator field name
     * @return an union field value
     */
    public static Schema.FieldValue union(String discriminator) {
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
    public static Schema.FieldValue list(Schema.FieldValue elementType) {
        return new Schema.ListField(elementType);
    }

    /**
     * Map schema field type.
     *
     * A map declares a JSON object type whose field names are treated as
     * arbitrary (string) keys associated with some elementary or object type.
     */
    public static Schema.FieldValue map(Schema.FieldValue elementType) {
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
