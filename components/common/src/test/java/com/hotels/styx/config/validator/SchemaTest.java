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

import com.hotels.styx.config.validator.Schema.Field;
import org.testng.annotations.Test;

import static com.hotels.styx.config.validator.ObjectValidator.schema;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

public class SchemaTest {

    @Test
    public void elementaryTypeFields() throws Exception {
        Schema co = ObjectValidator.schema("test")
                .field("myInt", Field.integer())
                .field("myString", Field.string())
                .field("myBool", Field.bool())
                .build();

        assertThat(co.fieldNames(), hasItems("myInt", "myString", "myBool"));
    }

    @Test
    public void subobjectFields() throws Exception {
        Schema co = ObjectValidator.schema("parent")
                .field("myInt", Field.integer())
                .field("myString", Field.string())
                .field("myObject", Field.object(
                        ObjectValidator.schema("subobject")
                                .field("x", Field.string())
                                .field("y", Field.integer())
                ))
                .build();

        assertThat(co.fieldNames(), hasItems("myInt", "myString", "myObject"));
    }

    @Test
    public void passSchema() {
        // This will skip the verification of the subobject
        Schema co = schema()
                .field("myIgnoredObject", Field.object(
                        schema()
                ))
                .build();
    }

    @Test
    public void listsOfElementaryTypes() throws Exception {
        Schema co = ObjectValidator.schema("parent")
                .field("myList", Field.list(Field.string()))
                .build();

        assertThat(co.fieldNames(), hasItems("myList"));
    }

    @Test
    public void listsOfObjectTypes() throws Exception {
        Schema co = ObjectValidator.schema("parent")
                .field("myList", Field.list(Field.object(
                        schema()
                                .field("x", Field.string())
                                .field("y", Field.integer())
                )))
                .build();

        assertThat(co.fieldNames(), hasItems("myList"));
    }

    @Test
    public void subobjectUnionFields() throws Exception {
        Schema co = ObjectValidator.schema("parent")
                .field("name", Field.string())
                .field("type", Field.string())
                .field("config", Field.union( "type"))
                .build();

        assertThat(co.fieldNames(), hasItems("name", "type", "config"));
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "Discriminator attribute 'type' not present.")
    public void checksThatSubobjectUnionDiscriminatorAttributeExists() throws Exception {
        ObjectValidator.schema("parent")
                .field("name", Field.string())
                .field("config", Field.union("type"))
                .build();
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "Discriminator attribute 'type' must be a string \\(but it is not\\)")
    public void checksThatSubobjectUnionDiscriminatorAttributeIsString() throws Exception {
        ObjectValidator.schema("parent")
                .field("name", Field.string())
                .field("type", Field.integer())
                .field("config", Field.union("type"))
                .build();
    }

    @Test
    public void fieldTypes() {
        assertThat(Field.string().type(), is(Schema.FieldType.STRING));
        assertThat(Field.integer().type(), is(Schema.FieldType.INTEGER));
        assertThat(Field.bool().type(), is(Schema.FieldType.BOOLEAN));
        assertThat(Field.object("Foo").type(), is(Schema.FieldType.OBJECT));
        assertThat(Field.object(schema()).type(), is(Schema.FieldType.OBJECT));
        assertThat(Field.list(Field.integer()).type(), is(Schema.FieldType.LIST));
    }
}