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
package org.redlance.mocha.runtime.jvm;

import org.junit.jupiter.api.Test;
import org.redlance.mocha.runtime.ExecutionContext;
import org.redlance.mocha.runtime.MochaEngine;
import org.redlance.mocha.runtime.binding.Binding;
import org.redlance.mocha.runtime.compiled.MochaCompiledFunction;
import org.redlance.mocha.runtime.compiled.Named;
import org.redlance.mocha.runtime.value.Function;
import org.redlance.mocha.runtime.value.MutableObjectBinding;
import org.redlance.mocha.runtime.value.NumberValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.redlance.mocha.MochaAssertions.assertEvaluatesAndCompiles;

/**
 * Compiled functions must give the interpreter's results, and constructs
 * the compiler doesn't support must be rejected while compiling instead of
 * producing invalid bytecode or a wrong result.
 */
class CompilerSemanticsTest {
    @Test
    void temp_variables_with_any_return_type() {
        final MochaEngine<?> engine = MochaEngine.createStandard();
        assertEquals(6F, engine.compiler().compile("t.x = 2; return t.x * a;", FloatFn.class).get(3));
        assertEquals(6D, engine.compiler().compile("t.x = 2; return t.x * a;", DoubleFn.class).get(3));
        assertEquals(6, engine.compiler().compile("t.x = 2; return t.x * a;", IntFn.class).get(3));
        assertTrue(engine.compiler().compile("t.x = a; return t.x > 1;", BoolFn.class).get(3));
        assertFalse(engine.compiler().compile("t.x = a; return t.x > 1;", BoolFn.class).get(1));
    }

    @Test
    void assignment_as_last_statement() {
        assertEvaluatesAndCompiles(2, "t.x = 2");
        assertEvaluatesAndCompiles(5, "t.x = 2; t.y = t.x + 3");
    }

    @Test
    void fractions_are_true() {
        final MochaEngine<?> engine = MochaEngine.createStandard();
        assertTrue(engine.compiler().compile("a", BoolFn.class).get(0.5F));
        assertTrue(engine.compiler().compile("a", DoubleBoolFn.class).get(0.5));
        assertFalse(engine.compiler().compile("a", BoolFn.class).get(0));
        assertEquals(1F, engine.compiler().compile("a && 1", FloatFn.class).get(0.5F));
        assertEquals(10F, engine.compiler().compile("a ? 10 : 20", FloatFn.class).get(0.5F));
        assertEquals(0F, engine.compiler().compile("!a", FloatFn.class).get(0.5F));
        assertEquals(1F, engine.compiler().compile("math.sin(a) && 1", FloatFn.class).get(30));
        assertEquals(1F, engine.compiler().compile("(a + 0.25) || 0", FloatFn.class).get(0.25F));
    }

    @Test
    void division_by_zero_and_non_finite_results() {
        final MochaEngine<?> engine = MochaEngine.createStandard();
        assertEquals(0F, engine.compiler().compile("1 / a", FloatFn.class).get(0));
        assertEquals(0.5F, engine.compiler().compile("1 / a", FloatFn.class).get(2));
        assertEquals(0F, engine.compiler().compile("math.ln(a)", FloatFn.class).get(0));
        assertEquals(0F, engine.compiler().compile("math.sqrt(a)", FloatFn.class).get(-1));
        assertEquals(5F, engine.compiler().compile("math.ln(a) + 5", FloatFn.class).get(0));
        assertEquals(0F, engine.compiler().compile("a * 3e38", FloatFn.class).get(10));
        assertEvaluatesAndCompiles(0, "1 / 0");
    }

    @Test
    void conditional_without_else() {
        final MochaEngine<?> engine = MochaEngine.createStandard();
        assertEquals(5F, engine.compiler().compile("a ? 5", FloatFn.class).get(1));
        assertEquals(0F, engine.compiler().compile("a ? 5", FloatFn.class).get(0));
    }

    @Test
    void same_results_as_the_interpreter() {
        assertEvaluatesAndCompiles(-30, "-math.sin(90) * 30");
        assertEvaluatesAndCompiles(1, "math.pi > 3 && math.pi < 4");
        assertEvaluatesAndCompiles(6, "t.a = 1; t.b = t.a + 1; return t.a + t.b * 2 + 1;");
        assertEvaluatesAndCompiles(20, "t.missing ? 10 : 20");
        assertEvaluatesAndCompiles(0, "unknown.thing + nothing()");
    }

    @Test
    void unsupported_constructs_are_rejected() {
        final MochaEngine<?> engine = MochaEngine.createStandard();
        engine.interpreter().bind(StaticBindings.class);
        engine.interpreter().bindInstance(InstanceBindings.class, new InstanceBindings(), "instance");
        final MutableObjectBinding query = new MutableObjectBinding();
        query.set("speed", (Function<?>) (ctx, args) -> NumberValue.of(2));
        engine.scope().set("q", query);

        for (final String code : new String[]{
                "v.x = 2; return v.x;",
                "v.preset",
                "q.speed()",
                "q.speed * 2",
                "t.a ?? 3",
                "v.a -> v.b",
                "t.x[0]",
                "loop(3, { t.x = t.x + 1; }); return t.x;",
                "for_each(t.x, v.list, { t.y = t.x; })",
                "break",
                "instance.speed",
                "bindings.with_context(5)"
        }) {
            final UnsupportedOperationException exception = assertThrows(
                    UnsupportedOperationException.class,
                    () -> engine.compiler().compile(code),
                    code
            );
            assertTrue(exception.getMessage().startsWith("The compiler doesn't support"), exception::getMessage);
        }
    }

    public interface FloatFn extends MochaCompiledFunction {
        float get(@Named("a") float a);
    }

    public interface DoubleFn extends MochaCompiledFunction {
        double get(@Named("a") double a);
    }

    public interface IntFn extends MochaCompiledFunction {
        int get(@Named("a") int a);
    }

    public interface BoolFn extends MochaCompiledFunction {
        boolean get(@Named("a") float a);
    }

    public interface DoubleBoolFn extends MochaCompiledFunction {
        boolean get(@Named("a") double a);
    }

    @Binding("bindings")
    public static final class StaticBindings {
        @Binding("with_context")
        public static float withContext(final ExecutionContext<?> context, final float value) {
            return value;
        }
    }

    public static final class InstanceBindings {
        @Binding("speed")
        public float speed = 3;
    }
}
