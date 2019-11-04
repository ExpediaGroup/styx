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
package com.hotels.styx.config.validator;

import com.hotels.styx.config.schema.InvalidSchemaException;
import org.junit.jupiter.api.Test;

import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.integer;
import static com.hotels.styx.config.schema.SchemaDsl.object;
import static com.hotels.styx.config.schema.SchemaDsl.union;
import static com.hotels.styx.config.validator.DocumentFormat.newDocument;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DocumentFormatTest {
    @Test
    public void discriminatedUnionSelectorMustBeString() {
        Exception e = assertThrows(InvalidSchemaException.class,
                () -> newDocument()
                    .rootSchema(object(
                            field("httpPipeline", object(
                                    field("type", integer()),
                                    field("config", union("type"))
                            ))
                    )
                ).build());
        assertThat(e.getMessage(), matchesPattern("Discriminator attribute 'type' must be a string \\(but it is not\\)"));
    }
}