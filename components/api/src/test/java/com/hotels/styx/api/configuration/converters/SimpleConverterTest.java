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
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

public class SimpleConverterTest {
    final SimpleConverter converter = new SimpleConverter();

    @Test
    public void convertsBooleanValue() {
        assertThat(converter.convert("true", Boolean.TYPE), is(true));
        assertThat(converter.convert("false", Boolean.TYPE), is(false));
    }

    @Test
    public void convertsIntegerValues() {
        assertThat(converter.convert("9000", Integer.TYPE), is(9000));
    }

    @Test
    public void convertsStringValue() {
        assertThat(converter.convert("9000", String.class), is("9000"));
    }

    @Test
    public void convertsEnumValues() {
        assertThat(converter.convert("CLOSE", Status.class), is(Status.CLOSE));
        assertThat(converter.convert("OPEN", Status.class), is(Status.OPEN));
    }

    @Test
    public void convertsArrayOfPrimitives() {
        String[] locales = converter.convert("UK,US,IT", String[].class);
        assertThat(asList(locales), contains("UK", "US", "IT"));
    }

    @Test(expectedExceptions = ConversionException.class)
    public void failsForUndefinedEnumValue() {
        assertThat(converter.convert("UDEFINED", Status.class), is(Status.OPEN));
    }

    @Test(expectedExceptions = ConversionException.class)
    public void failsForUndefinedObject() {
        assertThat(converter.convert("UDEFINED", ConfigurationValue.class), is(new ConfigurationValue()));
    }

    static class ConfigurationValue {
    }

    //
    enum Status {
        OPEN,
        CLOSE
    }
}
