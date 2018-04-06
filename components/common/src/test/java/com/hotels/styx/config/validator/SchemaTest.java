package com.hotels.styx.config.validator;

import com.hotels.styx.config.validator.Schema.Field;
import org.testng.annotations.Test;

import static com.hotels.styx.config.validator.Schema.newSchema;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

public class SchemaTest {

    @Test
    public void elementaryTypeFields() throws Exception {
        Schema co = newSchema("test")
                .field(Field.integer("myInt"))
                .field(Field.string("myString"))
                .build();

        assertThat(co.field("myInt"), is(Field.integer("myInt")));
        assertThat(co.field("myString"), is(Field.string("myString")));
        assertThat(co.fields(), hasItems(
                Field.integer("myInt"),
                Field.string("myString")));
    }

    @Test
    public void subobjectFields() throws Exception {
        Schema co = newSchema("parent")
                .field(Field.integer("myInt"))
                .field(Field.string("myString"))
                .field(Field.object(
                        "myObject", newSchema("subobject")
                                .field(Field.string("x"))
                                .field(Field.integer("y"))
                                .build()
                ))
                .build();

        assertThat(co.field("myInt"), is(Field.integer("myInt")));
        assertThat(co.field("myString"), is(Field.string("myString")));
        assertThat(co.fields(), hasItems(
                Field.integer("myInt"),
                Field.string("myString"),
                Field.object("myObject", newSchema("subobject")
                        .field(Field.string("x"))
                        .field(Field.integer("y"))
                        .build())));
    }

    @Test
    public void subobjectUnionFields() throws Exception {
        Schema co = newSchema("parent")
                .field(Field.string("name"))
                .field(Field.string("type"))
                .field(Field.union("config", "type"))
                .build();

        assertThat(co.field("name"), is(Field.string("name")));
        assertThat(co.field("type"), is(Field.string("type")));
        assertThat(co.field("config"), is(Field.union("config", "type")));
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "Discriminator attribute 'type' not present.")
    public void checksThatSubobjectUnionDiscriminatorAttributeExists() throws Exception {
        newSchema("parent")
                .field(Field.string("name"))
                .field(Field.union("config", "type"))
                .build();
    }

    @Test(expectedExceptions = InvalidSchemaException.class,
            expectedExceptionsMessageRegExp = "Discriminator attribute 'type' must be a string \\(but it is not\\)")
    public void checksThatSubobjectUnionDiscriminatorAttributeIsString() throws Exception {
        newSchema("parent")
                .field(Field.string("name"))
                .field(Field.integer("type"))
                .field(Field.union("config", "type"))
                .build();
    }


}