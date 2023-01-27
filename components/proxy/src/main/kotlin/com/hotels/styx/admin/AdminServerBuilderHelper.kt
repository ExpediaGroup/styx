/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.admin

import com.hotels.styx.admin.handlers.ReadinessHandler
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.WebServiceHandler
import com.hotels.styx.server.AdminHttpRouter
import java.util.function.BooleanSupplier

/**
 * This class exists temporarily in order to migrate part of [AdminServerBuilder] to Kotlin without needing to migrate the whole thing.
 */
class AdminServerBuilderHelper(private val adminHttpRouter: AdminHttpRouter) {

    fun addHandlers() {

    }

    infix fun List<String>.areHandledBy(handler : WebServiceHandler) = forEach { it.isHandledBy(handler) }
    infix fun List<String>.areHandledBy(handler : HttpHandler) = forEach { it.isHandledBy(handler) }

    infix fun String.isHandledBy(handler : WebServiceHandler) {
        adminHttpRouter.aggregate(this, handler)
    }

    infix fun String.isHandledBy(handler : HttpHandler) {
        adminHttpRouter.stream(this, handler)
    }
}

fun newReadinessHandler(booleanSupplier: BooleanSupplier) = ReadinessHandler { booleanSupplier.asBoolean }
