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
package com.hotels.styx.server.routing.antlr;

import com.hotels.styx.api.HttpRequest;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Objects.requireNonNull;

class FunctionResolver {
    private final Map<String, Function0> zeroArgumentFunctions;
    private final Map<String, Function1> oneArgumentFunctions;

    public FunctionResolver(Map<String, Function0> zeroArgumentFunctions, Map<String, Function1> oneArgumentFunctions) {
        this.zeroArgumentFunctions = requireNonNull(zeroArgumentFunctions);
        this.oneArgumentFunctions = requireNonNull(oneArgumentFunctions);
    }

    PartialFunction resolveFunction(String name, List<String> arguments) {
        int argumentSize = arguments.size();
        String argumentsRepresentation = join(", ", arguments);

        switch (argumentSize) {
            case 0:
                Function0 function0 = ensureNotNull(
                        zeroArgumentFunctions.get(name),
                        "No such function=[%s], with n=[%d] arguments=[%s]", name, argumentSize, argumentsRepresentation);
                return function0::call;
            case 1:
                Function1 function1 = ensureNotNull(
                        oneArgumentFunctions.get(name),
                        "No such function=[%s], with n=[%d] arguments=[%s]", name, argumentSize, argumentsRepresentation);
                return request -> function1.call(request, arguments.get(0));

            default:
                throw new IllegalArgumentException(format("No such function=[%s], with n=[%d] arguments=[%s]", name, argumentSize, argumentsRepresentation));
        }
    }

    private <T> T ensureNotNull(T functionRef, String message, String name, int argumentSize, String argumentsRepresentation) {
        if (functionRef == null) {
            throw new DslFunctionResolutionError(String.format(message, name, argumentSize, argumentsRepresentation));
        }
        return functionRef;
    }

    interface PartialFunction {
        String call(HttpRequest request);
    }
}
