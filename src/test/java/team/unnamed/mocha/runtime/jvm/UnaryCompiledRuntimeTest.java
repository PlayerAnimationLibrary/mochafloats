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
package team.unnamed.mocha.runtime.jvm;

import org.junit.jupiter.api.Test;
import team.unnamed.mocha.MochaEngine;
import team.unnamed.mocha.runtime.binding.Binding;
import team.unnamed.mocha.runtime.compiled.MochaCompiledFunction;
import team.unnamed.mocha.runtime.compiled.Named;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static team.unnamed.mocha.MochaAssertions.assertEvaluatesAndCompiles;

/**
 * Unary operators applied to function calls, e.g. "-math.abs(x)",
 * must negate the call result, not the function itself.
 */
class UnaryCompiledRuntimeTest {
    @Test
    void test_constant_calls() {
        assertEvaluatesAndCompiles(-3, "-math.abs(3)");
        assertEvaluatesAndCompiles(-6, "-math.abs(3) * 2");
        assertEvaluatesAndCompiles(-2, "-math.abs(-3) + 1");
        assertEvaluatesAndCompiles(-1, "-math.abs(3) - -math.abs(2)");
        assertEvaluatesAndCompiles(3, "--math.abs(3)");
        assertEvaluatesAndCompiles(3, "math.abs(-math.abs(3))");
        assertEvaluatesAndCompiles(1, "!math.abs(0)");
        assertEvaluatesAndCompiles(0, "!math.abs(2)");
        assertEvaluatesAndCompiles(1, "!!math.abs(2)");
        assertEvaluatesAndCompiles(1, "!math.abs(0) && 1");
        assertEvaluatesAndCompiles(20, "!math.abs(2) ? 10 : 20");
    }

    @Test
    void test_bound_calls() {
        final MochaEngine<?> engine = MochaEngine.createStandard();
        engine.bindInstance(Query.class, new Query(), "query", "q");

        assertBoth(engine, -3, "-q.length('abc')");
        assertBoth(engine, -2, "-q.length('abc') + 1");
        assertBoth(engine, 1, "!q.length('')");
        assertBoth(engine, 0, "!q.length('abc')");
        assertBoth(engine, 10, "!q.length('') ? 10 : 20");
    }

    @Test
    void test_float_function() {
        final FloatFunction function = MochaEngine.createStandard().compile("-math.abs(value) * 2", FloatFunction.class);
        assertEquals(-14F, function.get(7), 1e-6F);
        assertEquals(-14F, function.get(-7), 1e-6F);
        assertEquals(0F, function.get(0), 1e-6F); // -0.0
    }

    @Test
    void test_int_function() {
        final IntFunction negate = MochaEngine.createStandard().compile("-math.abs(value)", IntFunction.class);
        assertEquals(-7, negate.get(7));
        assertEquals(-7, negate.get(-7));
        assertEquals(0, negate.get(0));

        final IntFunction not = MochaEngine.createStandard().compile("!math.abs(value)", IntFunction.class);
        assertEquals(0, not.get(7));
        assertEquals(1, not.get(0));
    }

    @Test
    void test_double_function() {
        final DoubleFunction function = MochaEngine.createStandard().compile("-math.abs(value) + 1", DoubleFunction.class);
        assertEquals(-6D, function.get(7));
        assertEquals(-6D, function.get(-7));
        assertEquals(1D, function.get(0));
    }

    @Test
    void test_boolean_function() {
        final BooleanFunction negate = MochaEngine.createStandard().compile("-math.abs(value)", BooleanFunction.class);
        assertTrue(negate.get(7));
        assertTrue(negate.get(-7));
        assertFalse(negate.get(0));

        final BooleanFunction not = MochaEngine.createStandard().compile("!math.abs(value)", BooleanFunction.class);
        assertFalse(not.get(7));
        assertTrue(not.get(0));
    }

    private static void assertBoth(final MochaEngine<?> engine, final float expected, final String code) {
        assertEquals(expected, engine.eval(code), 1e-6F, () -> "interpreted: " + code);
        assertEquals(expected, engine.compile(code).evaluate(), 1e-6F, () -> "compiled: " + code);
    }

    public interface FloatFunction extends MochaCompiledFunction {
        float get(@Named("value") float value);
    }

    public interface IntFunction extends MochaCompiledFunction {
        int get(@Named("value") int value);
    }

    public interface DoubleFunction extends MochaCompiledFunction {
        double get(@Named("value") double value);
    }

    public interface BooleanFunction extends MochaCompiledFunction {
        boolean get(@Named("value") float value);
    }

    public static final class Query {
        @Binding("length")
        public double length(final String value) {
            return value.length();
        }
    }
}
