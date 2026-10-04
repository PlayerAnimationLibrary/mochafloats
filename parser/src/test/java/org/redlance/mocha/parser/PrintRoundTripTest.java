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
import org.redlance.mocha.parser.ast.Expression;
import org.redlance.mocha.parser.util.ExpressionListUtils;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code toString()} must print text that parses back into the same tree.
 */
class PrintRoundTripTest {
    private static String print(final String source) throws IOException {
        return ExpressionListUtils.toString(MolangParser.parseAll(source));
    }

    private static void assertRoundTrip(final String source) throws IOException {
        final List<Expression> parsed = MolangParser.parseAll(source);
        final String printed = ExpressionListUtils.toString(parsed);
        assertEquals(parsed, MolangParser.parseAll(printed), () -> source + " was printed as " + printed);
    }

    @Test
    void parentheses_are_kept_where_needed() throws IOException {
        assertEquals("(1+2)*3", print("(1 + 2) * 3"));
        assertEquals("1+2*3", print("1 + (2 * 3)"));
        assertEquals("10-(4-1)", print("10 - (4 - 1)"));
        assertEquals("1-2-3", print("(1 - 2) - 3"));
        assertEquals("math.abs(1-(2-3))", print("math.abs(1 - (2 - 3))"));
        assertEquals("(v.a??2)+1", print("(v.a ?? 2) + 1"));
        assertEquals("v.x=1?5:6", print("v.x = (1 ? 5 : 6)"));
        assertEquals("(v.x=1)?5:6", print("(v.x = 1) ? 5 : 6"));
        assertEquals("1?2:3?4:5", print("1 ? 2 : (3 ? 4 : 5)"));
        assertEquals("(1?2:3)?4:5", print("(1 ? 2 : 3) ? 4 : 5"));
        assertEquals("q.a?(q.b?1):2", print("q.a ? (q.b ? 1) : 2"));
        assertEquals("(1+2)[0]", print("(1 + 2)[0]"));
    }

    @Test
    void random_expressions() throws IOException {
        final Random random = new Random(1234);
        for (int i = 0; i < 20_000; i++) {
            assertRoundTrip(randomExpression(random, 4));
        }
    }

    // fully parenthesized source text, so the tree is known
    private static String randomExpression(final Random random, final int depth) {
        if (depth <= 0 || random.nextInt(4) == 0) {
            // calls only while there is depth left, so the expression stays finite
            return switch (random.nextInt(depth <= 0 ? 4 : 6)) {
                case 0 -> Integer.toString(random.nextInt(10));
                case 1 -> "v.a";
                case 2 -> "t.b";
                case 3 -> "'text'";
                case 4 -> "math.abs(" + randomExpression(random, depth - 1) + ")";
                default -> "q.f(" + randomExpression(random, depth - 1) + ", " + randomExpression(random, depth - 1) + ")";
            };
        }
        final String a = randomExpression(random, depth - 1);
        final String b = randomExpression(random, depth - 1);
        return switch (random.nextInt(9)) {
            case 0 -> "-(" + a + ")";
            case 1 -> "!(" + a + ")";
            case 2 -> "(" + a + ") ? (" + b + ") : (" + randomExpression(random, depth - 1) + ")";
            case 3 -> "(" + a + ") ? (" + b + ")";
            case 4 -> "v.c = (" + a + ")";
            case 5 -> "(v.arr)[" + a + "]";
            case 6 -> "(v.d) -> (" + a + ")";
            default -> {
                final String[] operators = {"+", "-", "*", "/", "<", "<=", ">", ">=", "==", "!=", "&&", "||", "??"};
                yield "(" + a + ") " + operators[random.nextInt(operators.length)] + " (" + b + ")";
            }
        };
    }
}
