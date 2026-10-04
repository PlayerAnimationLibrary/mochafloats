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
import org.redlance.mocha.runtime.value.MutableObjectBinding;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour decided by what Bedrock and the majority of other MoLang
 * implementations (MolangJS, bridge's molang, bedrockk/MoLang, Moonflower's
 * molang-compiler, molang-py) do.
 */
class SemanticsTest {
    private static final int SAMPLES = 5_000;

    @Test
    void strings_compare_by_text() {
        final MolangInterpreter<?> molang = MolangInterpreter.standard();
        assertEquals(0F, molang.eval("'a' == 'b'"));
        assertEquals(1F, molang.eval("'a' == 'a'"));
        assertEquals(1F, molang.eval("'a' != 'b'"));
        assertEquals(0F, molang.eval("'a' != 'a'"));
        assertEquals(1F, molang.eval("v.name = 'steve'; return v.name == 'steve';"));
        assertEquals(0F, molang.eval("v.name = 'steve'; return v.name == 'alex';"));
        // a string and a number still compare as numbers, a string being 0
        assertEquals(1F, molang.eval("'a' == 0"));
    }

    @Test
    void halves_round_away_from_zero() {
        final MolangInterpreter<?> molang = MolangInterpreter.standard();
        assertEquals(-3F, molang.eval("math.round(-2.5)"));
        assertEquals(3F, molang.eval("math.round(2.5)"));
        assertEquals(-2F, molang.eval("math.round(-2.4)"));
        assertEquals(11F, molang.eval("math.round(10.5)"));
        assertEquals(0F, molang.eval("math.round(0.49999997)"));
    }

    @Test
    void blocks_in_conditionals_run() {
        final MolangInterpreter<?> molang = MolangInterpreter.standard();
        assertEquals(4F, molang.eval("v.w = 0; 1 ? { v.w = 4; } : { v.w = 5; }; return v.w;"));
        assertEquals(5F, molang.eval("v.w = 0; 0 ? { v.w = 4; } : { v.w = 5; }; return v.w;"));
        assertEquals(4F, molang.eval("v.q = 0; 1 ? { v.q = 4; }; return v.q;"));
        assertEquals(2F, molang.eval("t.i = 0; t.x = 0; loop(5, { t.i = t.i + 1; (t.i > 2) ? { break; } : { t.x = t.i; }; }); return t.x;"));
    }

    @Test
    void only_missing_values_fall_back() {
        final MolangInterpreter<?> molang = MolangInterpreter.standard();
        molang.scope().set("q", new MutableObjectBinding());
        assertEquals(0F, molang.eval("v.zero = 0; return v.zero ?? 7;"));
        assertEquals(2F, molang.eval("v.two = 2; return v.two ?? 7;"));
        assertEquals(7F, molang.eval("v.never ?? 7"));
        assertEquals(3F, molang.eval("t.x ?? 3"));
        assertEquals(4F, molang.eval("q.missing ?? 4"));
        assertEquals(5F, molang.eval("unknown ?? 5"));
        assertEquals(6F, molang.eval("unknown.thing ?? 6"));
        assertEquals((float) Math.PI, molang.eval("math.pi ?? 1"), 1e-6F);
    }

    @Test
    void empty_and_reversed_ranges() {
        final MolangInterpreter<?> molang = MolangInterpreter.standard();
        assertEquals(1F, molang.eval("math.random(1, 1)"));
        assertEquals(2F, molang.eval("math.random_integer(2, 2)"));
        assertEquals(3F, molang.eval("math.die_roll(3, 1, 1)"));
        assertEquals(2F, molang.eval("math.die_roll_integer(2, 1, 1)"));
        for (int i = 0; i < SAMPLES; i++) {
            final float value = molang.eval("math.random(5, 1)");
            assertTrue(value >= 1 && value <= 5, () -> "math.random(5, 1) = " + value);
        }
        assertEquals(Set.of(1F, 2F, 3F), sample(molang, "math.random_integer(3, 1)"));
    }

    @Test
    void integer_ranges_include_both_bounds() {
        final MolangInterpreter<?> molang = MolangInterpreter.standard();
        assertEquals(Set.of(1F, 2F, 3F), sample(molang, "math.random_integer(1, 3)"));
        assertEquals(Set.of(1F, 2F, 3F), sample(molang, "math.die_roll_integer(1, 1, 3)"));
        assertEquals(Set.of(2F, 3F, 4F), sample(molang, "math.die_roll_integer(2, 1, 2)"));
    }

    @Test
    void die_roll_sums_rolls_between_the_bounds() {
        final MolangInterpreter<?> molang = MolangInterpreter.standard();
        boolean fractional = false;
        for (int i = 0; i < SAMPLES; i++) {
            final float value = molang.eval("math.die_roll(1, 5, 6)");
            assertTrue(value >= 5 && value <= 6, () -> "math.die_roll(1, 5, 6) = " + value);
            fractional |= value != (int) value;
        }
        assertTrue(fractional, "math.die_roll returns fractional rolls");
        for (int i = 0; i < SAMPLES; i++) {
            final float value = molang.eval("math.die_roll(2, 1, 2)");
            assertTrue(value >= 2 && value <= 4, () -> "math.die_roll(2, 1, 2) = " + value);
        }
    }

    private static Set<Float> sample(final MolangInterpreter<?> molang, final String code) {
        final Set<Float> values = new HashSet<>();
        for (int i = 0; i < SAMPLES; i++) {
            values.add(molang.eval(code));
        }
        return values;
    }
}
