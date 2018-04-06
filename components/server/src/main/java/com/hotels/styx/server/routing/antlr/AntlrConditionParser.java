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

import com.hotels.styx.server.routing.Condition;
import com.hotels.styx.server.routing.ConditionLexer;
import com.hotels.styx.server.routing.ConditionParser;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Condition} parser based on ANLR.
 */
public class AntlrConditionParser implements Condition.Parser {
    private final ExpressionVisitor expressionVisitor;

    public AntlrConditionParser(Builder builder) {
        this.expressionVisitor = new ExpressionVisitor(builder.zeroArgumentFunctions, builder.oneArgumentFunctions);
    }

    @Override
    public Condition parse(String condition) {
        ConditionParser parser = new ConditionParser(
                new CommonTokenStream(new ConditionLexer(new ANTLRInputStream(condition))));
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new DslSyntaxError(line, charPositionInLine, msg, e);
            }
        });

        return new AntlrCondition(expressionVisitor.visit(parser.expression()));
    }

    /**
     * Builder for {@link AntlrConditionParser}.
     */
    public static class Builder {
        private final Map<String, Function0> zeroArgumentFunctions = new HashMap<>();
        private final Map<String, Function1> oneArgumentFunctions = new HashMap<>();

        public Builder registerFunction(String name, Function0 function0) {
            zeroArgumentFunctions.put(name, function0);
            return this;
        }

        public Builder registerFunction(String name, Function1 function1) {
            oneArgumentFunctions.put(name, function1);
            return this;
        }

        public AntlrConditionParser build() {
            return new AntlrConditionParser(this);
        }
    }
}
