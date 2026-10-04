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
package org.redlance.mocha.parser;

import org.junit.jupiter.api.Test;
import org.redlance.mocha.parser.ast.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.redlance.mocha.parser.ParserAssertions.assertCreateSameTree;
import static org.redlance.mocha.parser.ParserAssertions.assertCreateTree;

/**
 * Grouping of assignments and conditionals, and incomplete scripts, decided
 * by what the majority of other MoLang implementations do.
 */
class GroupingTest {
    private static Expression access(final String object, final String property) {
        return new AccessExpression(new IdentifierExpression(object), property);
    }

    @Test
    void assignment_takes_the_whole_conditional() throws ParseException {
        assertCreateTree("v.x = 1 ? 5 : 6", new BinaryExpression(
                BinaryExpression.Op.ASSIGN,
                access("v", "x"),
                new TernaryConditionalExpression(FloatExpression.ONE, FloatExpression.of(5), FloatExpression.of(6))
        ));
        assertCreateSameTree("v.a = (v.b = 1)", "v.a = v.b = 1");
        assertCreateSameTree("t.a = (t.b ?? 3)", "t.a = t.b ?? 3");
        assertCreateSameTree("v.x = (q.a ? 1)", "v.x = q.a ? 1");
    }

    @Test
    void conditionals_group_from_the_right() throws ParseException {
        assertCreateTree("1 ? 2 : 3 ? 4 : 5", new TernaryConditionalExpression(
                FloatExpression.ONE,
                FloatExpression.of(2),
                new TernaryConditionalExpression(FloatExpression.of(3), FloatExpression.of(4), FloatExpression.of(5))
        ));
        assertCreateTree("(1 ? 2 : 3) ? 4 : 5", new TernaryConditionalExpression(
                new TernaryConditionalExpression(FloatExpression.ONE, FloatExpression.of(2), FloatExpression.of(3)),
                FloatExpression.of(4),
                FloatExpression.of(5)
        ));
        assertCreateSameTree("q.a ? 1 : (q.b + 1)", "q.a ? 1 : q.b + 1");
        assertCreateSameTree("(q.a == 1) ? 2 : 3", "q.a == 1 ? 2 : 3");
    }

    @Test
    void incomplete_scripts_are_errors() {
        for (final String code : new String[]{
                "v.a = 1;; v.b = 2; return v.b;",
                "v.a = 1; ; return 5;",
                "1 +",
                "math.abs(1,)",
                "q.a ? : 1",
                "return;",
                "{ v.a = 1;; }"
        }) {
            assertThrows(ParseException.class, () -> MolangParser.parseAll(code), code);
        }
    }

    @Test
    void complete_scripts_still_parse() throws IOException {
        assertEquals(1, MolangParser.parseAll("math.pi;").size());
        assertEquals(2, MolangParser.parseAll("v.a = 1; return v.a;").size());
        assertEquals(1, MolangParser.parseAll("loop(2, {})").size());
        assertEquals(1, MolangParser.parseAll("loop(2, { v.a = v.a + 1; })").size());
        assertEquals(1, MolangParser.parseAll("loop(2, { v.a = v.a + 1; v.b = 1 })").size());
        assertEquals(0, MolangParser.parseAll("").size());
    }
}
