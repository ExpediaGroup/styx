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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.RoutingObjectYamlRecord;
import com.hotels.styx.routing.config.Builtins;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class RoutingObjectRecordDeserialiser extends StdDeserializer<RoutingObjectYamlRecord<RoutingObject>> {

    private final Map<String, Builtins.StyxObjectDescriptor<StyxObject<RoutingObject>>> descriptors;

    public RoutingObjectRecordDeserialiser(Map<String, Builtins.StyxObjectDescriptor<StyxObject<RoutingObject>>> descriptors) {
        super(StyxObject.class);
        this.descriptors = descriptors;
    }

    @Override
    public RoutingObjectYamlRecord<RoutingObject> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {

        JsonNode node = jp.getCodec().readTree(jp);

        String type = node.get("type").textValue();

        Set<String> tags = Collections.emptySet();
        if (node.get("tags") != null) {
            tags = node.get("tags").traverse(jp.getCodec()).readValueAs(new TypeReference<Set<String>>() {
            });
        }

        Builtins.StyxObjectDescriptor<StyxObject<RoutingObject>> descriptor = this.descriptors.get(type);

        StyxObject<RoutingObject> styxObject = (StyxObject<RoutingObject>) node.get("config").traverse(jp.getCodec()).readValueAs(descriptor.klass());

        return new RoutingObjectYamlRecord<>(type, tags, styxObject);
    }
}
