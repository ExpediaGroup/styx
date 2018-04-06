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
package com.hotels.styx.proxy.encoders;

import com.hotels.styx.StyxConfig;
import org.slf4j.Logger;
import org.testng.annotations.Test;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class ConfigurableUnwiseCharsEncoderTest {

    @Test
    public void loadsUnwiseCharsToEncodeFromConfigurations() {
        ConfigurableUnwiseCharsEncoder encoder = newUnwiseCharsEncoder("|,},{");
        assertThat(encoder.encode("|}{"), is("%7C%7D%7B"));
    }

    @Test
    public void shouldHandleEmptyUnwiseChars() {
        ConfigurableUnwiseCharsEncoder encoder = newUnwiseCharsEncoder("");
        assertThat(encoder.encode("|}{"), is("|}{"));
    }

    @Test
    public void shouldEncodeUnwiseCharsMixedWithOtherChars() {
        ConfigurableUnwiseCharsEncoder encoder = newUnwiseCharsEncoder("|,{,}");
        String urlWithUnwiseChars = "/search.do?foo={&srsReport=Landing|AutoS|HOTEL|Hotel%20Il%20Duca%20D%27Este" +
                "|0|0|0|2|1|2|284128&srsr=Landing|AutoS|HOTEL|Hotel%20Il%20Duca%20D%27Este|0|0|0|2|1|2|284128";
        assertThat(encoder.encode(urlWithUnwiseChars), is("/search.do?foo=%7B&srsReport=Landing%7CAutoS%7CHOTEL%7CHotel" +
                "%20Il%20Duca%20D%27Este%7C0%7C0%7C0%7C2%7C1%7C2%7C284128&srsr=Landing%7CAutoS%7CHOTEL%7CHotel%20Il%20" +
                "Duca%20D%27Este%7C0%7C0%7C0%7C2%7C1%7C2%7C284128"));
    }

    @Test
    public void shouldLogErrorMessageInCaseOfEncodingUnwiseChars() throws Exception {
        Logger logger = mock(Logger.class);
        ConfigurableUnwiseCharsEncoder encoder = new ConfigurableUnwiseCharsEncoder(newStyxConfig("|"), logger);
        assertThat(encoder.encode("|}{"), is("%7C}{"));
        verify(logger).warn("Value contains unwise chars. you should fix this. raw={}, escaped={}: ",
                "|}{",
                "%7C}{");
    }

    @Test
    public void shouldNotLogIfNotEncoding() throws Exception {
        Logger logger = mock(Logger.class);
        ConfigurableUnwiseCharsEncoder encoder = new ConfigurableUnwiseCharsEncoder(newStyxConfig(""), logger);
        assertThat(encoder.encode("|}{"), is("|}{"));
        verifyZeroInteractions(logger);
    }


    private static ConfigurableUnwiseCharsEncoder newUnwiseCharsEncoder(String unwiseCharSet) {
        StyxConfig config = newStyxConfig(unwiseCharSet);
        return new ConfigurableUnwiseCharsEncoder(config);
    }

    private static StyxConfig newStyxConfig(String unwiseCharSet) {
        String yaml = format("" +
                "url:\n" +
                "  encoding:\n" +
                "    unwiseCharactersToEncode: \"%s\"\n", unwiseCharSet);

        return StyxConfig.fromYaml(yaml);
    }
}