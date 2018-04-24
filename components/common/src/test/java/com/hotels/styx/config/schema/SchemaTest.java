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

import org.testng.annotations.Test;

import static com.hotels.styx.config.schema.SchemaDsl.bool;
import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.integer;
import static com.hotels.styx.config.schema.SchemaDsl.list;
import static com.hotels.styx.config.schema.SchemaDsl.object;
import static com.hotels.styx.config.schema.SchemaDsl.opaque;
import static com.hotels.styx.config.schema.SchemaDsl.string;
import static com.hotels.styx.config.schema.SchemaDsl.union;
import static com.hotels.styx.config.schema.SchemaDsl.schema;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

public class SchemaTest {

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "Discriminator attribute 'type' not present.")
    public void checksThatSubobjectUnionDiscriminatorAttributeExists() throws Exception {
        schema(
                field("name", string()),
                field("config", union("type"))
        );
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "Discriminator attribute 'type' must be a string \\(but it is not\\)")
    public void checksThatSubobjectUnionDiscriminatorAttributeIsString() throws Exception {
        schema(
                field("name", string()),
                field("type", integer()),
                field("config", union("type"))
        );
    }

    @Test
    public void fieldTypes() {
        assertThat(string().type(), is(Schema.FieldType.STRING));
        assertThat(integer().type(), is(Schema.FieldType.INTEGER));
        assertThat(bool().type(), is(Schema.FieldType.BOOLEAN));
        assertThat(object("Foo").type(), is(Schema.FieldType.OBJECT));
        assertThat(object(opaque()).type(), is(Schema.FieldType.OBJECT));
        assertThat(list(integer()).type(), is(Schema.FieldType.LIST));
    }
}