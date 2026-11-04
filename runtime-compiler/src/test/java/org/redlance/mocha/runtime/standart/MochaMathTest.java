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
package org.redlance.mocha.runtime.standart;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redlance.mocha.MochaAssertions;
import org.redlance.mocha.runtime.MochaEngine;

public class MochaMathTest {
    @Test
    void test() {
        final MochaEngine<?> engine = MochaEngine.createStandard();
        Assertions.assertEquals(Math.abs(-10F), engine.interpreter().eval("math.abs(-10);"));
    }

    @Test
    void die_roll_still_compiles() {
        // the budget charge was added to die_roll's interpreter binding only; the static
        // method it compiles to is unchanged, so it must still compile. Each roll between
        // 1 and 1 is exactly 1, so the result is the roll count on both paths.
        MochaAssertions.assertEvaluatesAndCompiles(5, "math.die_roll(5, 1, 1)");
        MochaAssertions.assertEvaluatesAndCompiles(5, "math.die_roll_integer(5, 1, 1)");
    }
}
