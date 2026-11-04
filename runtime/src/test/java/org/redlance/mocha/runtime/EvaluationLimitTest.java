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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationLimitTest {
    @Test
    void a_loop_beyond_the_limit_is_aborted_and_reported() {
        final List<MolangEvaluationLimitException> reported = new ArrayList<>();
        final MolangInterpreter<?> molang = MolangInterpreter.standard()
                .evaluationLimit(10_000)
                .handleEvaluationLimit(reported::add);
        // without the limit this nested loop would run for about 2^62 iterations
        final float result = molang.eval("loop(2147483647, {loop(2147483647, {1})})");
        assertEquals(0F, result);
        assertEquals(1, reported.size());
        assertEquals(10_000L, reported.getFirst().limit());
    }

    @Test
    void a_loop_with_a_non_block_body_is_also_bounded() {
        final List<MolangEvaluationLimitException> reported = new ArrayList<>();
        final MolangInterpreter<?> molang = MolangInterpreter.standard()
                .evaluationLimit(10_000)
                .handleEvaluationLimit(reported::add);
        // the body is a bound function, not a block, so the iteration itself has to be counted
        final float result = molang.eval("loop(2147483647, math.sin)");
        assertEquals(0F, result);
        assertEquals(1, reported.size());
        assertEquals(10_000L, reported.getFirst().limit());
    }

    @Test
    void stored_blocks_cannot_expand_without_bound() {
        final List<MolangEvaluationLimitException> reported = new ArrayList<>();
        final MolangInterpreter<?> molang = MolangInterpreter.standard()
                .evaluationLimit(10_000)
                .handleEvaluationLimit(reported::add);
        // v.f40() would call v.f0 about 2^40 times without a limit
        final StringBuilder code = new StringBuilder("v.f0 = { 1 };");
        for (int i = 1; i <= 40; i++) {
            code.append(" v.f").append(i).append(" = { v.f").append(i - 1).append("(); v.f").append(i - 1).append("(); };");
        }
        code.append(" v.f40();");
        assertEquals(0F, molang.eval(code.toString()));
        assertEquals(1, reported.size());
    }

    @Test
    void a_short_loop_within_the_limit_still_runs() {
        final List<MolangEvaluationLimitException> reported = new ArrayList<>();
        final MolangInterpreter<?> molang = MolangInterpreter.standard()
                .evaluationLimit(10_000)
                .handleEvaluationLimit(reported::add);
        // the Fibonacci loop from the docs: 10 iterations, well within the limit
        final float result = molang.eval("v.x = 1; v.y = 1; loop(10, { t.x = v.x + v.y; v.x = v.y; v.y = t.x; }); return v.y;");
        assertEquals(144F, result);
        assertTrue(reported.isEmpty());
    }

    @Test
    void a_die_roll_with_a_huge_count_is_aborted() {
        final List<MolangEvaluationLimitException> reported = new ArrayList<>();
        final MolangInterpreter<?> molang = MolangInterpreter.standard()
                .evaluationLimit(10_000)
                .handleEvaluationLimit(reported::add);
        // math.die_roll loops over its first argument inside a native binding
        assertEquals(0F, molang.eval("math.die_roll(2147483647, 0, 1)"));
        assertEquals(1, reported.size());
        assertEquals(10_000L, reported.getFirst().limit());
    }

    @Test
    void a_small_die_roll_within_the_limit_still_runs() {
        final List<MolangEvaluationLimitException> reported = new ArrayList<>();
        final MolangInterpreter<?> molang = MolangInterpreter.standard()
                .evaluationLimit(10_000)
                .handleEvaluationLimit(reported::add);
        // each roll between 1 and 1 is exactly 1, so the sum is the roll count
        assertEquals(5F, molang.eval("math.die_roll(5, 1, 1)"));
        assertEquals(5F, molang.eval("math.die_roll_integer(5, 1, 1)"));
        assertTrue(reported.isEmpty());
    }

    @Test
    void a_stored_block_is_charged_to_the_calling_evaluation() {
        final MolangInterpreter<?> molang = MolangInterpreter.standard().evaluationLimit(5);
        molang.eval("v.n = 0; v.f = { v.n = v.n + 1; };"); // defines the block; it is not run here
        for (int i = 0; i < 20; i++) {
            molang.eval("v.f();"); // each call runs the block once, well within a fresh 5-step budget
        }
        // every call ran on its own budget, so the counter reached 20; a budget captured when the
        // block was defined would be exhausted after 5 calls and abort the rest
        assertEquals(20F, molang.eval("return v.n;"));
    }

    @Test
    void without_a_limit_a_long_loop_runs_to_completion() {
        // the default is no limit, so a legitimate long loop still finishes
        final MolangInterpreter<?> molang = MolangInterpreter.standard();
        final float result = molang.eval("v.n = 0; loop(100000, { v.n = v.n + 1; }); return v.n;");
        assertEquals(100_000F, result);
    }
}
