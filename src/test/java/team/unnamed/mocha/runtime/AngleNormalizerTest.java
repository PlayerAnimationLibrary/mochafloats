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
package team.unnamed.mocha.runtime;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import team.unnamed.mocha.MochaEngine;
import team.unnamed.mocha.parser.ParseException;
import team.unnamed.mocha.parser.ast.Expression;
import team.unnamed.mocha.parser.ast.FloatExpression;
import team.unnamed.mocha.runtime.standard.MochaMath;

import java.util.Collections;

public class AngleNormalizerTest {
    @Test
    public void testD2R() {
        Expression expression = FloatExpression.of(10);
        Assertions.assertEquals(FloatExpression.of(MochaMath.d2r(10)), expression.visit(new AngleNormalizer()));
    }

    @Test
    public void testR2D() {
        Expression expression = FloatExpression.of(10);
        Assertions.assertEquals(FloatExpression.of(MochaMath.r2d(10)), expression.visit(new AngleNormalizer(false)));
    }

    @Test
    public void test() throws ParseException {
        MochaEngine<?> engine = MochaEngine.createStandard();
        Expression expression = engine.parse("180 - 90").getFirst().visit(new AngleNormalizer());
        Assertions.assertEquals(MochaMath.d2r(90), engine.eval(Collections.singletonList(expression)));
    }
}

