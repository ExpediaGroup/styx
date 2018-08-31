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
package com.hotels.styx.admin.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Supplier;

import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.admin.support.Json.PRETTY_PRINTER;
import static java.util.Objects.requireNonNull;

/**
 * A supplier that serialises the output of another supplier into JSON. It will call the source supplier each time it
 * is called.
 */
public class JsonSupplier implements Supplier<String> {
    private final Supplier<?> objectSupplier;
    private final ObjectMapper mapper;
    private final boolean pretty;

    /**
     * Constructs an instance.
     *
     * @param objectSupplier a supplier that will provide an object each time it is called, that can be transformed into JSON
     * @param modules        modules for the object mapper
     */
    public static JsonSupplier create(Supplier<?> objectSupplier, Module... modules) {
        return create(objectSupplier, false, modules);
    }

    /**
     * Constructs an instance.
     *
     * @param objectSupplier A supplier that will provide an object each time it is called, that can be transformed into JSON.
     * @param modules        Modules for the object mapper.
     * @oaram pretty         Enable or disable pretty printing. Defaults to false.
     */
    public static JsonSupplier create(Supplier<?> objectSupplier, boolean pretty, Module... modules) {
        return new JsonSupplier(objectSupplier, pretty, modules);
    }

    private JsonSupplier(Supplier<?> objectSupplier, boolean pretty, Module... modules) {
        this.objectSupplier = requireNonNull(objectSupplier);
        this.pretty = pretty;

        this.mapper = new ObjectMapper();

        for (Module module : modules) {
            mapper.registerModule(module);
        }
    }

    @Override
    public String get() {
        return toJson(objectSupplier.get());
    }

    private String toJson(Object object) {
        try {
            if (pretty) {
                return mapper.writer(PRETTY_PRINTER).writeValueAsString(object);
            } else {
                return mapper.writer().writeValueAsString(object);
            }
        } catch (JsonProcessingException e) {
            throw propagate(e);
        }
    }

}
