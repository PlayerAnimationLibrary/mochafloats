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

import java.util.Collections;

import static org.redlance.mocha.parser.ParserAssertions.assertCreateSameTree;
import static org.redlance.mocha.parser.ParserAssertions.assertCreateTree;
import static org.redlance.mocha.parser.ParserAssertions.assertParseError;

class UnaryParseTest {
    @Test
    void test_literals() {
        // negated literals are folded
        assertCreateTree("-5", FloatExpression.of(-5));
        assertCreateTree("--5", FloatExpression.of(5));

        assertCreateTree("!0", new UnaryExpression(
                UnaryExpression.Op.LOGICAL_NEGATION,
                FloatExpression.ZERO
        ));

        assertCreateTree("-t.x", new UnaryExpression(
                UnaryExpression.Op.ARITHMETICAL_NEGATION,
                new AccessExpression(new IdentifierExpression("t"), "x")
        ));
    }

    @Test
    void test_call_operand() {
        assertCreateTree("-math.abs(3)", new UnaryExpression(
                UnaryExpression.Op.ARITHMETICAL_NEGATION,
                new CallExpression(
                        new AccessExpression(new IdentifierExpression("math"), "abs"),
                        Collections.singletonList(FloatExpression.of(3))
                )
        ));

        assertCreateTree("!query.is_baby()", new UnaryExpression(
                UnaryExpression.Op.LOGICAL_NEGATION,
                new CallExpression(
                        new AccessExpression(new IdentifierExpression("query"), "is_baby"),
                        Collections.emptyList()
                )
        ));

        assertCreateTree("--math.abs(x)", new UnaryExpression(
                UnaryExpression.Op.ARITHMETICAL_NEGATION,
                new UnaryExpression(
                        UnaryExpression.Op.ARITHMETICAL_NEGATION,
                        new CallExpression(
                                new AccessExpression(new IdentifierExpression("math"), "abs"),
                                Collections.singletonList(new IdentifierExpression("x"))
                        )
                )
        ));

        assertCreateTree("-!query.is_baby()", new UnaryExpression(
                UnaryExpression.Op.ARITHMETICAL_NEGATION,
                new UnaryExpression(
                        UnaryExpression.Op.LOGICAL_NEGATION,
                        new CallExpression(
                                new AccessExpression(new IdentifierExpression("query"), "is_baby"),
                                Collections.emptyList()
                        )
                )
        ));
    }

    @Test
    void test_array_access_operand() {
        assertCreateTree("-array.values[0]", new UnaryExpression(
                UnaryExpression.Op.ARITHMETICAL_NEGATION,
                new ArrayAccessExpression(
                        new AccessExpression(new IdentifierExpression("array"), "values"),
                        FloatExpression.of(0)
                )
        ));

        assertCreateTree("!array.flags[query.index()]", new UnaryExpression(
                UnaryExpression.Op.LOGICAL_NEGATION,
                new ArrayAccessExpression(
                        new AccessExpression(new IdentifierExpression("array"), "flags"),
                        new CallExpression(
                                new AccessExpression(new IdentifierExpression("query"), "index"),
                                Collections.emptyList()
                        )
                )
        ));
    }

    @Test
    void test_hierarchy() throws Exception {
        // unary operators bind tighter than binary operators
        assertCreateSameTree("(-(math.abs(3))) * 2", "-math.abs(3) * 2");
        assertCreateSameTree("(-(math.abs(3))) - (-(math.abs(2)))", "-math.abs(3) - -math.abs(2)");
        assertCreateSameTree("(!(query.is_baby())) && (!(query.is_sneaking()))", "!query.is_baby() && !query.is_sneaking()");
        assertCreateSameTree("(!(query.is_baby())) ? 1 : 2", "!query.is_baby() ? 1 : 2");
        assertCreateSameTree("-(1 + 2) * 3", "(-(1 + 2)) * 3");
        assertCreateSameTree("math.abs(-(math.abs(3)))", "math.abs(-math.abs(3))");
    }

    @Test
    void test_incorrect() {
        // missing operand
        assertParseError("-)", 2);
        assertParseError("!)", 2);
    }
}
