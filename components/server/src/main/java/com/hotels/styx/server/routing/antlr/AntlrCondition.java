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
import com.hotels.styx.server.routing.Condition;

import static java.util.Objects.requireNonNull;


/**
 * ANTLR based condition.
 */
class AntlrCondition implements Condition {
    private final Expression<Boolean> expression;

    AntlrCondition(Expression<Boolean> expression) {
        this.expression = requireNonNull(expression);
    }

    @Override
    public boolean evaluate(HttpRequest request) {
        return expression.evaluate(request);
    }

}
