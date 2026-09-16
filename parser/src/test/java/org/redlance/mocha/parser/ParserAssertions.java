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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;
import org.redlance.mocha.parser.ast.Expression;
import org.redlance.mocha.parser.util.ExprBytesUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public final class ParserAssertions {
    private ParserAssertions() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    public static @NotNull List<Expression> parse(final @NotNull String expr) throws ParseException {
        try (final StringReader reader = new StringReader(expr)) {
            return MolangParser.parser(reader).parseAll();
        } catch (final ParseException e) {
            throw e;
        } catch (final IOException e) {
            throw new AssertionError("StringReader IOException", e);
        }
    }

    public static void assertCreateSameTree(final @NotNull String expr1, final @NotNull String expr2) throws ParseException {
        final List<Expression> expressions1 = parse(expr1);
        final List<Expression> expressions2 = parse(expr2);
        assertEquals(expressions1, expressions2, () -> "Expressions:\n\t- " +
                expr1 +
                "\n\t- " +
                expr2 +
                "\nGenerated different syntax trees:\n\t- " +
                expressions1 +
                "\n\t- " +
                expressions2);
    }

    public static void assertCreateTree(final @NotNull String expr, final @NotNull Expression @NotNull ... expressions) {
        assertCreateTree(expr, Arrays.asList(expressions));
    }

    public static void assertCreateTree(final @NotNull String expr, final List<Expression> expressions) {
        final List<Expression> parsed;
        try {
            parsed = parse(expr);
        } catch (final ParseException e) {
            fail("Failed to parse expression: '" + expr + "'", e);
            return;
        }

        { // Network round-trip
            ByteBuf buf = Unpooled.buffer();
            ExprBytesUtils.writeExpressions(expressions, buf);

            List<Expression> readed = ExprBytesUtils.readExpressions(buf);
            buf.release();

            assertEquals(readed, expressions);
        }

        assertEquals(
                expressions,
                parsed,
                () -> "Expression: '" + expr + "' generated unexpected syntax tree:\n\t" +
                        "- Expected: " + expressions + "\n\t" +
                        "- Got: " + parsed
        );
    }

    public static void assertParseError(final @NotNull String expr, final int column) {
        try {
            final List<Expression> expressions = parse(expr);
            fail("Expected parse error at column " + column + " for expression: '" + expr + "', instead, it was parsed as: " + expressions);
        } catch (final ParseException e) {
            assertEquals(column, e.cursor().column(), "Expected parse error at column " + column + " for expression: '" + expr + "'");
        }
    }
}
