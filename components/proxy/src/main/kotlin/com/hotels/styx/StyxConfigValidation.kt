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
package com.hotels.styx

import com.hotels.styx.ServerConfigSchema.validateServerConfiguration
import com.hotels.styx.config.schema.SchemaValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger

val LOG: Logger = getLogger("com.hotels.styx")

/**
 * Used to perform validation on StyxConfig.
 * StyxConfig can only be validated if using YamlConfiguration underneath.
 */
// TODO it would be nice if we could implement this without violating encapsulation by knowing whether YAML is used internally
// TODO but that should be designed carefully so that our class model still makes sense.
fun validate(config: StyxConfig) {
    val outcome: Outcome = config.yamlConfiguration().map {
        validateServerConfiguration(it)
                .map { errorMessage -> ValidationFailure(errorMessage) as Outcome }
                .orElse(ValidationSuccess)
    }.orElse(NoValidation)

    when (outcome) {
        ValidationSuccess -> LOG.info("Configuration validated successfully.")
        NoValidation -> LOG.info("Configuration could not be validated as it does not use YAML")
        is ValidationFailure -> {
            val message: String = outcome.error
            LOG.error("Styx server failed to start due to configuration error.")
            LOG.error("The configuration was sourced from " + config.startupConfig().configFileLocation())
            LOG.error(message)
            throw SchemaValidationException(message);
        }
    }
}

sealed class Outcome
object ValidationSuccess : Outcome()
data class ValidationFailure(val error: String): Outcome()
object NoValidation: Outcome()
