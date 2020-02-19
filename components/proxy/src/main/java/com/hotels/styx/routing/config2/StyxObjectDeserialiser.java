package com.hotels.styx.routing.config2;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.hotels.styx.routing.config.Builtins;

import java.io.IOException;
import java.util.Map;

public class StyxObjectDeserialiser extends StdDeserializer<StyxObject> {

    private final Map<String, Builtins.StyxObjectDescriptor> descriptors;

    public StyxObjectDeserialiser(Map<String, Builtins.StyxObjectDescriptor> descriptors) {
        super(StyxObject.class);
        this.descriptors = descriptors;
    }

    @Override
    public StyxObject deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {

        JsonNode node = jp.getCodec().readTree(jp);

        String type = node.get("type").textValue();

        Builtins.StyxObjectDescriptor descriptor = this.descriptors.get(type);

        JsonParser parser = node.get("config").traverse(jp.getCodec());

        return (StyxObject) parser.readValueAs(descriptor.klass());
    }
}
