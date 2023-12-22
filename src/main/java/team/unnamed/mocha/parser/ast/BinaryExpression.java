/*
 * This file is part of mocha, licensed under the MIT license
 *
 * Copyright (c) 2021-2023 Unnamed Team
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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * Expression implementation for binary expressions
 * (expressions composed by <b>two</b> other expressions)
 *
 * <p>Example binary expressions: {@code 1 + 1}, {@code 5 * 9},
 * {@code a == b}, {@code a < b}, {@code true ?? false}</p>
 *
 * @since 3.0.0
 */
public final class BinaryExpression implements Expression {

    private final Op op;
    private final Expression left;
    private final Expression right;

    public BinaryExpression(
            final @NotNull Op op,
            final @NotNull Expression left,
            final @NotNull Expression right
    ) {
        this.op = requireNonNull(op, "op");
        this.left = requireNonNull(left, "left");
        this.right = requireNonNull(right, "right");
    }

    /**
     * Gets the binary expression type/operation.
     *
     * @return The expression operation.
     * @since 3.0.0
     */
    public @NotNull Op op() {
        return op;
    }

    /**
     * Gets the left-hand expression for this
     * binary expression.
     *
     * @return The left-hand expression
     * @since 3.0.0
     */
    public @NotNull Expression left() {
        return left;
    }

    /**
     * Gets the right-hand expression for this
     * binary expression.
     *
     * @return The right-hand expression
     * @since 3.0.0
     */
    public @NotNull Expression right() {
        return right;
    }

    @Override
    public <R> R visit(final @NotNull ExpressionVisitor<R> visitor) {
        return visitor.visitBinary(this);
    }

    @Override
    public String toString() {
        return op.name() + "(" + left + ", " + right + ")";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BinaryExpression that = (BinaryExpression) o;
        if (op != that.op) return false;
        if (!left.equals(that.left)) return false;
        return right.equals(that.right);
    }

    @Override
    public int hashCode() {
        int result = op.hashCode();
        result = 31 * result + left.hashCode();
        result = 31 * result + right.hashCode();
        return result;
    }

    public enum Op {
        AND(300),
        OR(200),
        LT(700),
        LTE(700),
        GT(700),
        GTE(700),
        ADD(900),
        SUB(900),
        MUL(1000),
        DIV(1000),
        ARROW(2000),
        NULL_COALESCE(2),
        ASSIGN(1),
        CONDITIONAL(1),
        EQ(500),
        NEQ(500);

        private final int precedence;

        Op(final int precedence) {
            this.precedence = precedence;
        }

        @ApiStatus.Internal
        public int precedence() {
            return precedence;
        }

    }

}