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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redlance.mocha.parser.ast.Expression;
import org.redlance.mocha.parser.ast.IdentifierExpression;
import org.redlance.mocha.runtime.value.NumberValue;

import java.util.Collections;
import java.util.List;

public class IndividualScopeTest {
    private static final List<Expression> THIS = Collections.singletonList(new IdentifierExpression("this"));

    @Test
    public void testThis() {
        final MochaEngine<?> engine = MochaEngine.createStandard();
        Assertions.assertEquals(0F, engine.interpreter().eval(THIS));
        Assertions.assertEquals(10F, engine.interpreter().eval(THIS, scope ->
                scope.set("this", NumberValue.of(10F))
        ));
        Assertions.assertEquals(0F, engine.interpreter().eval(THIS));
    }
}
