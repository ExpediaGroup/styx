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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.config.schema.InvalidSchemaException;
import com.hotels.styx.config.schema.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Provides an end-user interface for the schema based object validation.
 * <p>
 * An DocumentFormat instance is created with a call to `newDocument`. This returns an
 * a builder object for customising the validator. Specifically to:
 * <p>
 * - Add custom extension types that can be referred from unions.
 * <p>
 * - Specify a "root" type that declares the top level configuration object layout.
 */
public class DocumentFormat {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentFormat.class);

    private final Schema.FieldValue root;
    private final ImmutableMap<String, Schema.FieldValue> additionalSchemas;

    private DocumentFormat(Builder builder) {
        this.root = requireNonNull(builder.root);
        this.additionalSchemas = ImmutableMap.copyOf(builder.schemas);
    }

    public boolean validateObject(JsonNode tree) {
        return !root.isCorrectType(new ArrayList<>(), tree, tree, this.additionalSchemas::get).isPresent();
    }

    public static Builder newDocument() {
        return new Builder();
    }

    /**
     * An object validator builder.
     */
    public static class Builder {
        private final Map<String, Schema.FieldValue> schemas = new HashMap<>();
        private Schema.FieldValue root;

        public Builder typeExtension(String typeName, Schema.FieldValue schema) {
            this.schemas.put(requireNonNull(typeName), requireNonNull(schema));
            return this;
        }

        public Builder rootSchema(Schema.FieldValue root) {
            this.root = root;
            return this;
        }

        public DocumentFormat build() {
            assertRootSchemaExists(root);
            return new DocumentFormat(this);
        }

        private static void assertRootSchemaExists(Schema.FieldValue schema) {
            if (schema == null) {
                throw new InvalidSchemaException("Root schema is not specified.");
            }
        }

    }

}
