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
import org.redlance.mocha.runtime.ExecutionContext;
import org.redlance.mocha.runtime.MolangInterpreter;
import org.redlance.mocha.runtime.value.Value;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parameters of interpreted Java bindings that don't take a plain
 * MoLang value: the execution context, the entity, lazy arguments
 * and varargs.
 */
class JavaBindingParametersTest {
    private static MolangInterpreter<String> interpreter() {
        final MolangInterpreter<String> molang = MolangInterpreter.standard("steve");
        molang.bind(Parameters.class);
        return molang;
    }

    @Test
    void execution_context() {
        assertEquals(1F, interpreter().eval("params.has_context()"));
        assertEquals(15F, interpreter().eval("params.context_plus(5)"));
    }

    @Test
    void entity() {
        assertEquals(5F, interpreter().eval("params.entity_length()"));
        assertEquals(0F, interpreter().eval("params.wrong_entity_type()"));
    }

    @Test
    void lazy() {
        assertEquals(7F, interpreter().eval("params.lazy(7)"));
        assertEquals(1F, interpreter().eval("params.lazy_context()"));
    }

    @Test
    void varargs() {
        assertEquals(6F, interpreter().eval("params.sum(1, 2, 3)"));
        assertEquals(0F, interpreter().eval("params.sum()"));
        assertEquals(3F, interpreter().eval("params.count(1, 'two', 3)"));
        assertEquals(12F, interpreter().eval("params.scaled_sum(2, 1, 2, 3)"));
        assertEquals(5F, interpreter().eval("params.joined_length('ab', 'cde')"));
    }

    @Binding("params")
    public static final class Parameters {
        @Binding("has_context")
        public static boolean hasContext(final ExecutionContext<?> context) {
            return context != null;
        }

        @Binding("context_plus")
        public static float contextPlus(final ExecutionContext<?> context, final float value) {
            return context == null ? value : value + 10;
        }

        @Binding("entity_length")
        public static int entityLength(final @Entity String entity) {
            return entity == null ? -1 : entity.length();
        }

        @Binding("wrong_entity_type")
        public static float wrongEntityType(final @Entity Integer entity) {
            return entity == null ? 0 : 1;
        }

        @Binding("lazy")
        public static float lazy(final Lazy<Value> value) {
            return value.get().getAsNumber();
        }

        @Binding("lazy_context")
        public static boolean lazyContext(final Lazy<ExecutionContext<?>> context) {
            return context.get() != null;
        }

        @Binding("sum")
        public static float sum(final float... values) {
            float sum = 0;
            for (final float value : values) {
                sum += value;
            }
            return sum;
        }

        @Binding("count")
        public static int count(final Value... values) {
            return values.length;
        }

        @Binding("scaled_sum")
        public static double scaledSum(final double scale, final double... values) {
            double sum = 0;
            for (final double value : values) {
                sum += value;
            }
            return sum * scale;
        }

        @Binding("joined_length")
        public static int joinedLength(final String... values) {
            return String.join("", values).length();
        }
    }
}
