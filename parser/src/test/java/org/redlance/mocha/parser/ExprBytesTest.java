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
import org.junit.jupiter.api.Test;
import org.redlance.mocha.parser.ast.Expression;
import org.redlance.mocha.parser.util.ExprBytesUtils;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Expressions written with {@link ExprBytesUtils} must be read back
 * unchanged, and corrupt data must not be read silently.
 */
class ExprBytesTest {
    private static void assertRoundTrip(final String source) throws IOException {
        final List<Expression> expressions = MolangParser.parseAll(source);
        final ByteBuf buf = Unpooled.buffer();
        ExprBytesUtils.writeExpressions(expressions, buf);

        assertEquals(expressions, ExprBytesUtils.readExpressions(buf), source);
        assertFalse(buf.isReadable(), () -> "bytes left after reading " + source);
    }

    @Test
    void round_trip() throws IOException {
        assertRoundTrip("v.a = 1; return math.sin(q.anim_time * 360) * 30;");
        assertRoundTrip("t.x = 'minecraft:pig'; return t.x;");
        assertRoundTrip("loop(3, { t.i = t.i + 1; (t.i > 1) ? break; }); return t.i;");
        assertRoundTrip("q.is_baby() ? -math.abs(-3) : !v.flag");
        assertRoundTrip("v.values[1] + (v.missing ?? 7)");
        assertRoundTrip("v.target -> q.health");
        assertRoundTrip("1.5e-3 * 2");
    }

    @Test
    void empty_string() throws IOException {
        assertRoundTrip("q.length('')");
        assertRoundTrip("'' == ''");
    }

    @Test
    void unknown_operator() {
        final ByteBuf buf = Unpooled.buffer();
        buf.writeByte(8); // BinaryExpression
        buf.writeByte(200); // no such operator
        assertThrows(IllegalArgumentException.class, () -> ExprBytesUtils.readExpression(buf));
    }
}
