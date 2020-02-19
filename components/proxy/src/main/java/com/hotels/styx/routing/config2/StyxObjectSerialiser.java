/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.routing.config2;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class StyxObjectSerialiser extends StdSerializer<StyxObject> {

    private final JsonSerializer<Object> defaultSerializer;

    public StyxObjectSerialiser(JsonSerializer<Object> defaultSerializer) {
        super(StyxObject.class);
        this.defaultSerializer = defaultSerializer;
    }

    @Override
    public void serialize(
            StyxObject value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonProcessingException {

        jgen.writeStartObject();
        jgen.writeStringField("type", value.type());

        jgen.writeFieldName("config");

        defaultSerializer.serialize(value, jgen, provider);

        jgen.writeEndObject();
    }
}
