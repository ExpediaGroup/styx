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
package com.hotels.styx.routing.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.configuration.RouteDatabase;
import com.hotels.styx.routing.config.RoutingObjectDefinition;
import com.hotels.styx.routing.config.RoutingObjectFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.hotels.styx.admin.support.Json.PRETTY_PRINTER;
import static com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins;

/**
 * Styx Route Database.
 */
public class StyxRouteDatabase implements RouteDatabase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ObjectMapper YAML_MAPPER = addStyxMixins(new ObjectMapper(new YAMLFactory()))
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);


    private final ConcurrentHashMap<String, ConfigRecord> handlers;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final RoutingObjectFactory routingObjectFactory;

    public StyxRouteDatabase(RoutingObjectFactory routingObjectFactory) {
        this.routingObjectFactory = routingObjectFactory;
        this.handlers = new ConcurrentHashMap<>();
    }

    @Override
    public void insert(String routingObjectDefAsJson) {
        try {
            RoutingObjectDefinition value = YAML_MAPPER.readValue(routingObjectDefAsJson, RoutingObjectDefinition.class);
            insert(value.name(), value);
        } catch (IOException e) {
            throw new RuntimeException(String.format("StyxRouteDatabase insert error. Object: %s", routingObjectDefAsJson), e);
        }
    }

    @Override
    public void delete(String key) {
        handlers.remove(key);
        notifyListeners();
    }

    public void insert(String key, RoutingObjectDefinition routingObjectDef) {
        handlers.put(key, new ConfigRecord(routingObjectDef));
        notifyListeners();
    }

    @Override
    public void remove(String key) {
        try {
            handlers.remove(key);
            notifyListeners();
        } catch (NullPointerException npe) {
            // pass
        }
    }

    //
    // Needs to run concurrently
    //
    @Override
    public Optional<Record> lookup(String key) {
        return Optional.ofNullable(handlers.get(key))
                .map(record -> {
                    if (record instanceof HandlerRecord) {
                        return (HandlerRecord) record;
                    } else {
                        HttpHandler handler = routingObjectFactory.build(ImmutableList.of(key), this, record.configuration);

                        HandlerRecord newRecord = record.addHandler(handler);
                        handlers.put(key, newRecord);
                        return newRecord;
                    }
                })
                .map(this::toRecord);
    }

    private Record toRecord(HandlerRecord record) {
        return new Record() {
            @Override
            public String name() {
                return record.name();
            }

            @Override
            public String type() {
                return record.type();
            }

            @Override
            public Set<String> tags() {
                return ImmutableSet.copyOf(record.tags());
            }

            @Override
            public HttpHandler handler() {
                return record.handler();
            }

            @Override
            public String configuration() {
                try {
                    return OBJECT_MAPPER
                            .writer(PRETTY_PRINTER)
                            .writeValueAsString(record.configuration());
                } catch (JsonProcessingException e) {
                    // TODO: Fix this:
                    return "Serialisation error";
                }
            }

            @Override
            public String toString() {
                return "{\n"
                        + " name: " + name()
                        + ",\n type: " + type()
                        + ",\n tags: " + tags()
                        + ",\n configuration: " + configuration()
                        + "\n}";
            }
        };
    }

    @Override
    public Set<Record> tagLookup(String... tags) {
        return handlers.values()
                .stream()
                .filter(record -> asSet(record.tags()).containsAll(asSet(ImmutableList.copyOf(tags))))
                .map(record -> {
                    if (record instanceof HandlerRecord) {
                        return (HandlerRecord) record;
                    } else {
                        String key = record.name();
                        HttpHandler handler = routingObjectFactory.build(ImmutableList.of(key), this, record.configuration);

                        HandlerRecord newRecord = record.addHandler(handler);
                        handlers.put(key, newRecord);
                        return newRecord;
                    }
                })
                .map(this::toRecord)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Record> lookupAll() {
        return handlers.values()
                .stream()
                .map(record -> {
                    if (record instanceof HandlerRecord) {
                        return (HandlerRecord) record;
                    } else {
                        String key = record.name();
                        HttpHandler handler = routingObjectFactory.build(ImmutableList.of(key), this, record.configuration);

                        HandlerRecord newRecord = record.addHandler(handler);
                        handlers.put(key, newRecord);
                        return newRecord;
                    }
                })
                .map(this::toRecord)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<HttpHandler> handler(String key) {
        return lookup(key)
                .map(Record::handler);
    }

    @Override
    public Set<HttpHandler> handlers(String... tags) {
        return tagLookup(tags).stream()
                .map(Record::handler)
                .collect(Collectors.toSet());
    }


    @Override
    public void replaceTag(String key, String oldTag, String newTag) {
        handlers.computeIfPresent(key, (x, record) -> record.replaceTag(oldTag, newTag));
        notifyListeners();
    }

    @Override
    public void addListener(Listener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    private void notifyListeners() {
        listeners.forEach(listener -> listener.updated(this));
    }

    private Set<String> asSet(List<String> inObject) {
        return ImmutableSet.copyOf(inObject);
    }

    private static class ConfigRecord {
        final RoutingObjectDefinition configuration;

        ConfigRecord(RoutingObjectDefinition configuration) {
            this.configuration = configuration;
        }

        public String name() {
            return this.configuration.name();
        }

        public String type() {
            return this.configuration.type();
        }

        public List<String> tags() {
            return this.configuration.tags();
        }

        JsonNode configuration() {
            return configuration.config();
        }

        RoutingObjectDefinition replaceTag(RoutingObjectDefinition input, String oldTag, String newTag) {
            return new RoutingObjectDefinition(
                    input.name(),
                    input.type(),
                    ImmutableList.copyOf(
                            input.tags().stream()
                                    .map(tag -> tag.equals(oldTag) ? newTag : tag)
                                    .collect(Collectors.toList())),
                    input.config());
        }

        public ConfigRecord replaceTag(String oldTag, String newTag) {
            return new ConfigRecord(replaceTag(this.configuration, oldTag, newTag));
        }

        public HandlerRecord addHandler(HttpHandler handler) {
            return new HandlerRecord(this.configuration, handler);
        }
    }

    private static class HandlerRecord extends ConfigRecord {
        private final HttpHandler handler;

        HandlerRecord(RoutingObjectDefinition configuration, HttpHandler handler) {
            super(configuration);
            this.handler = handler;
        }

        HttpHandler handler() {
            return handler;
        }

        public ConfigRecord replaceTag(String oldTag, String newTag) {
            return new HandlerRecord(replaceTag(configuration, oldTag, newTag), handler);
        }
    }
}
