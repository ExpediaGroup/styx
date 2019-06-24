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
package com.hotels.styx;

import com.hotels.styx.config.schema.SchemaValidationException;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static ch.qos.logback.classic.Level.ERROR;
import static ch.qos.logback.classic.Level.INFO;
import static com.hotels.styx.common.testing.ExceptionExpectation.expect;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StyxConfigValidationKtTest {
    private LoggingTestSupport log;

    @BeforeMethod
    public void setUp() {
        log = new LoggingTestSupport("com.hotels.styx");
    }

    @AfterMethod
    public void tearDown() {
        log.stop();
    }

    @Test
    public void doesNoValidationWhenNotUsingYaml() {
        StyxConfig config = new StyxConfig();

        StyxConfigValidationKt.validate(config);

        assertThat(log.lastMessage(), is(loggingEvent(INFO, "Configuration could not be validated as it does not use YAML")));
    }

    @Test
    public void validConfigPassesValidation() {
        String valid = "" +
                "proxy:\n" +
                "  connectors:\n" +
                "    http:\n" +
                "      port: 8080\n" +
                "admin:\n" +
                "  connectors:\n" +
                "    http:\n" +
                "      port: 8081\n" +
                "services:\n" +
                "  factories:\n" +
                "    serv1:\n" +
                "      foo: bar\n";

        StyxConfig config = new StyxConfig(valid);

        StyxConfigValidationKt.validate(config);

        assertThat(log.lastMessage(), is(loggingEvent(INFO, "Configuration validated successfully.")));
    }

    @Test
    public void invalidConfigFailsValidation() {
        String valid = "" +
                "proxy:\n" +
                "  connectors:\n" +
                "    http:\n" +
                "      sport: 8080\n" +
                "admin:\n" +
                "  connectors:\n" +
                "    http:\n" +
                "      port: 8081\n" +
                "services:\n" +
                "  factories:\n" +
                "    serv1:\n" +
                "      foo: bar\n";

        StyxConfig config = new StyxConfig(valid);

        expect(SchemaValidationException.class, () ->
                StyxConfigValidationKt.validate(config));

        assertThat(log.lastMessage(), is(loggingEvent(ERROR, "Missing a mandatory field 'proxy.connectors.http.port'")));
    }
}
