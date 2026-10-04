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
package org.redlance.mocha.runtime.binding;

import org.junit.jupiter.api.Test;
import org.redlance.mocha.runtime.MolangInterpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BindExternalFunction} must work when it's used once, not only
 * when it's repeated.
 */
class ExternalFunctionTest {
    @Test
    void single_annotation() {
        final MolangInterpreter<?> molang = MolangInterpreter.standard();
        molang.bind(Single.class);
        assertEquals(5F, molang.eval("single.hypot(3, 4)"));
    }

    @Test
    void repeated_annotations() {
        final MolangInterpreter<?> molang = MolangInterpreter.standard();
        molang.bind(Repeated.class);
        assertEquals(5F, molang.eval("repeated.hypot(3, 4)"));
        assertEquals(3F, molang.eval("repeated.cube_root(27)"));
    }

    @Binding("single")
    @BindExternalFunction(at = Math.class, name = "hypot", args = {double.class, double.class}, pure = true)
    public static final class Single {
    }

    @Binding("repeated")
    @BindExternalFunction(at = Math.class, name = "hypot", args = {double.class, double.class}, pure = true)
    @BindExternalFunction(at = Math.class, name = "cbrt", args = {double.class}, as = "cube_root", pure = true)
    public static final class Repeated {
    }
}
