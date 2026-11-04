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
package org.redlance.mocha.runtime;

/**
 * A per-evaluation counter of evaluation steps, shared by an
 * {@link ExpressionInterpreter} and the children it spawns for loops. A step is
 * one loop or for_each iteration, or one block execution; together these are
 * the only work a Molang script can repeat without bound, so counting them
 * bounds the whole evaluation. {@link #UNLIMITED} never counts.
 */
final class EvaluationBudget {
    static final EvaluationBudget UNLIMITED = new EvaluationBudget(0);

    private final long limit;
    private long remaining;

    EvaluationBudget(final long limit) {
        this.limit = limit;
        this.remaining = limit;
    }

    void step() {
        charge(1);
    }

    void charge(final long steps) {
        if (limit <= 0 || steps <= 0) {
            return;
        }
        remaining -= steps;
        if (remaining < 0) {
            throw new MolangEvaluationLimitException(limit);
        }
    }
}
