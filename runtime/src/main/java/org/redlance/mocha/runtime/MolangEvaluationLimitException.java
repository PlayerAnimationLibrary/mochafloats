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
 * Thrown while interpreting when an evaluation runs more steps (loop and
 * for_each iterations, and block executions) than the limit set with
 * {@link MolangInterpreter#evaluationLimit(long)}.
 *
 * <p>It aborts the whole evaluation, so a script cannot repeat loop, for_each
 * or block work without bound. {@link MolangInterpreter#eval} catches it,
 * reports it through the handler set with
 * {@link MolangInterpreter#handleEvaluationLimit}, and returns {@code 0}.</p>
 *
 * @since 6.2.0
 */
public final class MolangEvaluationLimitException extends RuntimeException {
    private final long limit;

    public MolangEvaluationLimitException(final long limit) {
        super("Molang evaluation exceeded its limit of " + limit + " steps");
        this.limit = limit;
    }

    /**
     * The limit that was exceeded, in evaluation steps.
     *
     * @since 6.2.0
     */
    public long limit() {
        return limit;
    }
}
