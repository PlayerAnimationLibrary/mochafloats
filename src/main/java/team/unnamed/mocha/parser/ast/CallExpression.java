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
package team.unnamed.mocha.parser.ast;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Call expression implementation, executes functions
 * with certain arguments.
 *
 * <p>Example call expressions: {@code print('hello')},
 * {@code math.sqrt(9)}, {@code math.pow(3, 2)}</p>
 *
 * @since 3.0.0
 */
public final class CallExpression implements Expression {
    private final List<Expression> arguments;
    private Expression function;

    public CallExpression(final @NotNull Expression function, final @NotNull List<Expression> arguments) {
        this.function = requireNonNull(function, "function");
        this.arguments = requireNonNull(arguments, "arguments");
    }

    /**
     * Gets the function expression.
     *
     * @since 3.0.0
     */
    public @NotNull Expression function() {
        return function;
    }

    /**
     * Sets the function expression.
     *
     * @since 3.0.0
     */
    public void function(final @NotNull Expression function) {
        this.function = requireNonNull(function, "function");
    }

    /**
     * Gets the list of arguments to pass to
     * the function.
     *
     * @since 3.0.0
     */
    public @NotNull List<Expression> arguments() {
        return arguments;
    }

    @Override
    public <R> R visit(final @NotNull ExpressionVisitor<R> visitor) {
        return visitor.visitCall(this);
    }

    @Override
    public String toString() {
        return "Call(" + function + ", " + arguments + ")";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallExpression that = (CallExpression) o;
        if (!function.equals(that.function)) return false;
        return arguments.equals(that.arguments);
    }

    @Override
    public int hashCode() {
        int result = function.hashCode();
        result = 31 * result + arguments.hashCode();
        return result;
    }

}