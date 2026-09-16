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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.unnamed.mocha.MochaEngine;
import team.unnamed.mocha.runtime.binding.Binding;
import team.unnamed.mocha.runtime.compiled.MochaCompiledFunction;
import team.unnamed.mocha.runtime.compiled.Named;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compiling a Java function call must not leak the type of its
 * last parameter into the rest of the expression.
 *
 * @see <a href="https://github.com/PlayerAnimationLibrary/mochafloats/issues/54">#54</a>
 */
class JavaFunctionCallCompiledRuntimeTest {
    private MochaEngine<?> engine;

    @BeforeEach
    void setUp() {
        engine = MochaEngine.createStandard();
        engine.bindInstance(Query.class, new Query(), "query", "q");
        engine.bind(StaticQuery.class);
    }

    @Test
    void issue54() {
        assertEvaluates(100, "query.body_float('chest', 'stored_power') - 100");
        assertEvaluates(300, "query.body_float('chest', 'stored_power') + 100");
        assertEvaluates(400, "query.body_float('chest', 'stored_power') * 2");
        assertEvaluates(50, "query.body_float('chest', 'stored_power') / 4");
    }

    @Test
    void stringArguments() {
        assertEvaluates(6, "q.length('abcd') + q.length('xy')");
        assertEvaluates(0, "q.body_float('chest', 'stored_power') - q.body_float('head', 'power')");
        assertEvaluates(4, "(q.length('abc') - 1) * 2");
        assertEvaluates(-2, "-(q.length('abc')) + 1");
        assertEvaluates(2, "math.abs(q.length('abc') - 5)");
        assertEvaluates(4.5F, "q.mixed('abc', 2, 0.5) - 1");
    }

    @Test
    void primitiveArguments() {
        assertEvaluates(6.5F, "q.twice_int(3) + 0.5");
        assertEvaluates(3, "q.twice_double(1.25) + 0.5");
        assertEvaluates(9, "q.twice_long(4) + 1");
        assertEvaluates(2, "q.flag(1) + 1");
        assertEvaluates(5, "q.twice_int(q.length('abc')) - 1");
    }

    @Test
    void staticFunction() {
        assertEvaluates(8, "static_query.length('abc') + 5");
        assertEvaluates(1, "static_query.length('abc') == 3");
    }

    @Test
    void comparisonsAndLogic() {
        assertEvaluates(1, "q.body_float('chest', 'stored_power') > 100");
        assertEvaluates(0, "q.body_float('chest', 'stored_power') < 100");
        assertEvaluates(1, "q.body_float('chest', 'stored_power') == 200");
        assertEvaluates(1, "q.length('ab') > 1 && q.length('') < 1");
        assertEvaluates(1, "q.length('') > 1 || q.length('ab') == 2");
        assertEvaluates(10, "q.length('abc') > 2 ? 10 : 20");
        assertEvaluates(20, "q.length('abc') > 5 ? 10 : 20");
    }

    @Test
    void voidFunction() {
        assertEvaluates(1, "q.log('hello') + 1");
    }

    @Test
    void statements() {
        assertEvaluates(6, "t.x = q.length('abc'); return t.x * 2;");
        assertEvaluates(7, "q.log('hello'); return q.length('abc') + 4;");
    }

    @Test
    void typedFunction() {
        final ScriptType script = engine.compile("q.length('abcd') * a + b", ScriptType.class);
        assertEquals(9, script.eval(2, 1));
        assertEquals(-4, script.eval(-1, 0));
    }

    private void assertEvaluates(final float expected, final String code) {
        assertEquals(expected, engine.eval(code), 1e-6F, () -> "interpreted: " + code);
        assertEquals(expected, engine.compile(code).evaluate(), 1e-6F, () -> "compiled: " + code);
    }

    public interface ScriptType extends MochaCompiledFunction {
        int eval(@Named("a") float a, @Named("b") float b);
    }

    public static final class Query {
        @Binding("body_float")
        public double bodyFloat(final String part, final String key) {
            return 200.0;
        }

        @Binding("length")
        public double length(final String value) {
            return value.length();
        }

        @Binding("mixed")
        public double mixed(final String value, final int i, final double d) {
            return value.length() + i + d;
        }

        @Binding("twice_int")
        public int twiceInt(final int value) {
            return value * 2;
        }

        @Binding("twice_double")
        public double twiceDouble(final double value) {
            return value * 2;
        }

        @Binding("twice_long")
        public long twiceLong(final long value) {
            return value * 2;
        }

        @Binding("flag")
        public double flag(final boolean value) {
            return value ? 1 : 0;
        }

        @Binding("log")
        public void log(final String message) {
        }
    }

    @Binding("static_query")
    public static final class StaticQuery {
        @Binding("length")
        public static double length(final String value) {
            return value.length();
        }
    }
}
