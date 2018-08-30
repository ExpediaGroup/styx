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

import com.google.common.base.Splitter;
import com.google.common.escape.CharEscaperBuilder;
import com.google.common.escape.Escaper;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.server.netty.codec.UnwiseCharsEncoder;
import org.slf4j.Logger;

import java.util.Objects;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.escape.Escapers.nullEscaper;
import static java.lang.Integer.toHexString;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Unwise chars encoder that gets what to code from configuration.
 */
public class ConfigurableUnwiseCharsEncoder implements UnwiseCharsEncoder {
    public static final String ENCODE_UNWISECHARS = "url.encoding.unwiseCharactersToEncode";

    private final Escaper escaper;
    private final Logger logger;

    public ConfigurableUnwiseCharsEncoder(String unwiseChars) {
        this(unwiseChars, getLogger(ConfigurableUnwiseCharsEncoder.class));
    }

    public ConfigurableUnwiseCharsEncoder(StyxConfig config) {
        this(config, getLogger(ConfigurableUnwiseCharsEncoder.class));
    }

    public ConfigurableUnwiseCharsEncoder(StyxConfig config, Logger logger) {
        this(config.get(ENCODE_UNWISECHARS).orElse(""), logger);
    }

    public ConfigurableUnwiseCharsEncoder(String unwiseChars, Logger logger) {
        this.escaper = newEscaper(unwiseChars);
        this.logger = requireNonNull(logger);
    }

    @Override
    public String encode(String value) {
        String escaped = escaper.escape(value);
        if (!Objects.equals(escaped, value)) {
            logger.warn("Value contains unwise chars. you should fix this. raw={}, escaped={}: ", value, escaped);
        }
        return escaped;
    }

    private static Escaper newEscaper(String unwiseChars) {
        if (isNullOrEmpty(unwiseChars)) {
            return nullEscaper();
        }
        Iterable<String> tokens = Splitter.on(",").omitEmptyStrings().split(unwiseChars);
        CharEscaperBuilder builder = new CharEscaperBuilder();
        for (String token : tokens) {
            char c = token.charAt(0);
            builder.addEscape(c, "%" + toHexString(c).toUpperCase());
        }
        return builder.toEscaper();
    }
}
