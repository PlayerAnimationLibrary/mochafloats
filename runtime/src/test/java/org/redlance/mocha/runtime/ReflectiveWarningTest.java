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
import org.redlance.mocha.runtime.binding.Binding;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code warnOnReflectiveFunctionUsage} reports each reflectively called
 * method once, and never reports functions that don't use reflection.
 */
class ReflectiveWarningTest {
    @Test
    void reported_once_and_only_for_reflection() {
        final MolangInterpreter<?> molang = MolangInterpreter.standard();
        molang.warnOnReflectiveFunctionUsage(true);
        molang.bind(OnlyAnnotated.class);

        final String output = captureErr(() -> {
            for (int i = 0; i < 3; i++) {
                assertEquals(10F, molang.eval("only_annotated.twice(5)"));
                assertEquals(1F, molang.eval("math.sin(90) * math.cos(0)"));
            }
        });

        assertEquals(1, output.lines().count(), output);
        assertEquals(true, output.contains("OnlyAnnotated.twice"), output);
    }

    private static String captureErr(final Runnable runnable) {
        final PrintStream original = System.err;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            runnable.run();
        } finally {
            System.setErr(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Binding("only_annotated")
    public static final class OnlyAnnotated {
        @Binding("twice")
        public static float twice(final float value) {
            return value * 2;
        }
    }
}
