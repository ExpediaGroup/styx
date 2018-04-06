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
package com.hotels.styx.api.configuration.converters;

import com.hotels.styx.api.configuration.ConversionException;
import com.hotels.styx.api.configuration.Converter;

import java.lang.reflect.Array;

/**
 * Converter of object to {@link Boolean}, {@link Double}, {@link Integer}, {@link String}, enum or array types.
 */
public class SimpleConverter implements Converter {
    @Override
    public <T> T convert(Object source, Class<T> targetType) {
        if (source == null) {
            return null;
        }

        if (targetType == Boolean.class || targetType == Boolean.TYPE) {
            @SuppressWarnings("unchecked")
            T t = (T) Boolean.valueOf(source.toString());
            return t;
        }

        @SuppressWarnings("unchecked")
        T t = (T) parse(source.toString(), targetType);
        return t;
    }

    private Object parse(String source, Class<?> targetType) throws AssertionError {
        if (targetType.isEnum()) {
            String vlow = source.toLowerCase();
            for (Enum<?> e : targetType.asSubclass(Enum.class).getEnumConstants()) {
                if (e.name().toLowerCase().equals(vlow)) {
                    return e;
                }
            }
            throw new ConversionException("Enum constant not found: " + source);
        } else if (targetType == Double.class || targetType == Double.TYPE) {
            return Double.valueOf(source);
        } else if (targetType == Integer.class || targetType == Integer.TYPE) {
            return Integer.valueOf(source);
        } else if (targetType == String.class) {
            return source;
        } else if (targetType.isArray()) {
            String[] strVals = source.split(",", -1);
            Class<?> arrayType = targetType.getComponentType();
            Object a = Array.newInstance(arrayType, strVals.length);
            for (int i = 0; i < strVals.length; i++) {
                Array.set(a, i, parse(strVals[i], arrayType));
            }
            return a;
        }
        throw new ConversionException("Unknown return type " + targetType + " (val: " + source + ")");
    }
}
