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
import org.slf4j.Logger;

import static com.hotels.styx.ServerConfigSchema.validateServerConfiguration;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Used to perform validation on StyxConfig.
 * StyxConfig can only be validated if using YamlConfiguration underneath.
 */
// TODO it would be nice if we could implement this without violating encapsulation by knowing whether YAML is used internally
// TODO but that should be designed carefully so that our class model still makes sense.
public final class StyxConfigValidation {
    private static final Logger LOG = getLogger(StyxConfigValidation.class);

    private StyxConfigValidation() {
    }

    /**
     * Performs validation on StyxConfig.
     * This can only be done if the internal implementation uses YAML, otherwise it will just log a message.
     *
     * @param config configuration
     * @throws SchemaValidationException if config was invalid
     */
    public static void validate(StyxConfig config) {
        LOG.info(
                config.yamlConfiguration().map(yamlConfiguration -> {
                    validateServerConfiguration(yamlConfiguration)
                            .ifPresent(message -> {
                                LOG.info("Styx server failed to start due to configuration error.");
                                LOG.info("The configuration was sourced from " + config.startupConfig().configFileLocation());
                                LOG.info(message);
                                throw new SchemaValidationException(message);
                            });

                    return "Configuration validated successfully.";
                }).orElse("Configuration could not be validated as it does not use YAML")
        );
    }
}
