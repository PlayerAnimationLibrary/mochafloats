/*
 * This file is part of mocha, licensed under the MIT license
 *
 * Copyright (c) 2021-2025 Unnamed Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package team.unnamed.mocha.runtime;

import org.jetbrains.annotations.NotNull;
import team.unnamed.mocha.parser.ast.*;
import team.unnamed.mocha.runtime.standard.MochaMath;

@SuppressWarnings("unused") // api
public final class AngleNormalizer implements ExpressionVisitor<@NotNull Expression> {
    private final boolean d2r;

    public AngleNormalizer() {
        this(true);
    }

    public AngleNormalizer(boolean d2r) {
        this.d2r = d2r;
    }

    @Override
    public @NotNull Expression visitFloat(@NotNull FloatExpression expression) {
        float value = expression.value();
        if (this.d2r) {
            value = MochaMath.d2r(value);
        } else {
            value = MochaMath.r2d(value);
        }
        return FloatExpression.of(value);
    }

    @Override
    public @NotNull Expression visitTernaryConditional(@NotNull TernaryConditionalExpression expression) {
        return new TernaryConditionalExpression(expression.condition(),
                expression.trueExpression().visit(this),
                expression.falseExpression().visit(this)
        );
    }

    @Override
    public @NotNull Expression visitUnary(@NotNull UnaryExpression expression) {
        return new UnaryExpression(expression.op(), expression.expression().visit(this));
    }

    @Override
    public @NotNull Expression visitBinary(@NotNull BinaryExpression expression) {
        return new BinaryExpression(expression.op(),
                expression.left().visit(this),
                expression.right().visit(this)
        );
    }

    /*@Override
    public @NotNull Expression visitCall(@NotNull CallExpression expression) {
        return ExpressionVisitor.super.visitCall(expression);
    }*/

    @Override
    public @NotNull Expression visit(@NotNull Expression expression) {
        return expression;
    }
}
