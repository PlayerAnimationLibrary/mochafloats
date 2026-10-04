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
package org.redlance.mocha.runtime.standard;

import org.junit.jupiter.api.Test;
import org.redlance.mocha.runtime.MolangInterpreter;
import org.redlance.mocha.runtime.binding.BindExternalFunction;
import org.redlance.mocha.runtime.binding.Binding;
import org.redlance.mocha.runtime.binding.JavaFunction;
import org.redlance.mocha.runtime.value.Function;
import org.redlance.mocha.runtime.value.NumberValue;
import org.redlance.mocha.runtime.value.ObjectProperty;
import org.redlance.mocha.runtime.value.ObjectValue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interpreter must call the math functions through the functions
 * registered in {@link MochaMath}'s constructor, not through reflection.
 */
class MochaMathBindingTest {
    private static ObjectValue math() {
        return (ObjectValue) MolangInterpreter.standard().scope().get("math");
    }

    private static List<String> functionNames() {
        final List<String> names = new ArrayList<>();
        for (final Method method : MochaMath.class.getDeclaredMethods()) {
            final Binding binding = method.getDeclaredAnnotation(Binding.class);
            if (binding != null && Modifier.isStatic(method.getModifiers())) {
                names.addAll(Arrays.asList(binding.value()));
            }
        }
        for (final BindExternalFunction function : MochaMath.class.getAnnotationsByType(BindExternalFunction.class)) {
            names.add(function.as().isEmpty() ? function.name() : function.as());
        }
        return names;
    }

    @Test
    void functions_are_not_reflective() {
        final ObjectValue math = math();
        for (final String name : functionNames()) {
            final JavaFunction<?> function = assertInstanceOf(JavaFunction.class, math.get(name), name);
            assertFalse(function.reflective(), () -> "math." + name + " is called through reflection");
        }
    }

    @Test
    void purity_is_kept() {
        final ObjectValue math = math();
        assertTrue(((Function<?>) math.get("sin")).pure());
        assertTrue(((Function<?>) math.get("abs")).pure());
        assertFalse(((Function<?>) math.get("random")).pure());
        assertFalse(((Function<?>) math.get("die_roll")).pure());
    }

    @Test
    void constants_stay_inlineable() {
        final ObjectValue math = math();
        final ObjectProperty pi = math.getProperty("pi");
        assertNotNull(pi);
        assertTrue(pi.constant());
        assertEquals(NumberValue.of(MochaMath.PI), pi.value());
        assertTrue(math.getProperty("e").constant());
    }

    @Test
    void results() {
        final MolangInterpreter<?> molang = MolangInterpreter.standard();
        assertEquals(1F, molang.eval("math.sin(90)"));
        assertEquals(-30F, molang.eval("-math.sin(90) * 30"));
        assertEquals(3F, molang.eval("math.abs(-3)"));
        assertEquals(2F, molang.eval("math.max(1, 2)"));
        assertEquals(3F, molang.eval("math.round(2.5)"));
        assertEquals(1024F, molang.eval("math.pow(2, 10)"));
        assertEquals(0.25F, molang.eval("math.inverse_lerp(0, 10, 2.5)"));
        assertEquals(MochaMath.PI, molang.eval("math.pi"));
    }

    @Test
    void standalone_object() {
        final MochaMath math = new MochaMath();
        assertEquals(NumberValue.of(MochaMath.PI), math.get("PI"));
        assertInstanceOf(Function.class, math.get("sin"));
        assertFalse(math.set("sin", NumberValue.zero()), "math can't be changed after construction");
    }
}
