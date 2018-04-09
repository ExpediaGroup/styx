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

import com.hotels.styx.server.routing.ConditionBaseVisitor;
import com.hotels.styx.server.routing.ConditionParser;
import com.hotels.styx.server.routing.ConditionParser.StringIsPresentContext;

import java.util.Map;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.nullToEmpty;
import static com.hotels.styx.server.routing.antlr.Strings.stripFirstAndLastCharacter;

class ExpressionVisitor extends ConditionBaseVisitor<Expression<Boolean>> {
    private final StringCompareVisitor stringVisitor;

    public ExpressionVisitor(Map<String, Function0> zeroArgumentFunctions, Map<String, Function1> oneArgumentFunctions) {
        this.stringVisitor = new StringCompareVisitor(
                new FunctionResolver(zeroArgumentFunctions, oneArgumentFunctions));
    }

    @Override
    public Expression<Boolean> visitAndExpression(ConditionParser.AndExpressionContext ctx) {
        Expression<Boolean> left = visit(ctx.expression(0));
        Expression<Boolean> right = visit(ctx.expression(1));
        return request -> left.evaluate(request) && right.evaluate(request);
    }

    @Override
    public Expression<Boolean> visitOrExpression(ConditionParser.OrExpressionContext ctx) {
        Expression<Boolean> left = visit(ctx.expression(0));
        Expression<Boolean> right = visit(ctx.expression(1));
        return request -> left.evaluate(request) || right.evaluate(request);
    }

    @Override
    public Expression<Boolean> visitNotExpression(ConditionParser.NotExpressionContext ctx) {
        Expression<Boolean> expression = visit(ctx.expression());
        return request -> !expression.evaluate(request);
    }

    @Override
    public Expression<Boolean> visitSubExpression(ConditionParser.SubExpressionContext ctx) {
        Expression<Boolean> expression = visit(ctx.expression());
        return expression::evaluate;
    }

    @Override
    public Expression<Boolean> visitStringIsPresent(StringIsPresentContext ctx) {
        Expression<String> stringExpression = stringVisitor.visitStringExpression(ctx.stringExpression());
        return request -> nullToEmpty(stringExpression.evaluate(request)).length() > 0;
    }

    @Override
    public Expression<Boolean> visitStringEqualsString(ConditionParser.StringEqualsStringContext ctx) {
        Expression<String> left = stringVisitor.visitStringExpression(ctx.stringExpression(0));
        Expression<String> right = stringVisitor.visitStringExpression(ctx.stringExpression(1));
        return request -> nullToEmpty(left.evaluate(request)).equals(right.evaluate(request));
    }

    @Override
    public Expression<Boolean> visitStringMatchesRegexp(ConditionParser.StringMatchesRegexpContext ctx) {
        Expression<String> stringExpression = stringVisitor.visitStringExpression(ctx.stringExpression());
        Pattern pattern = Pattern.compile(stripFirstAndLastCharacter(ctx.string().getText()));
        return request -> {
            String evaluate = stringExpression.evaluate(request);
            return pattern.matcher(evaluate).matches();
        };
    }
}
