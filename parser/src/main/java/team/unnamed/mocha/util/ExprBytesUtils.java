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
package team.unnamed.mocha.util;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;
import team.unnamed.mocha.parser.ast.*;
import team.unnamed.mocha.util.network.ProtocolUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ExprBytesUtils {
    private static final Map<Class<? extends Expression>, Byte> EXPR_TO_BYTE = new HashMap<>();
    private static final Map<Byte, Function<ByteBuf, ? extends Expression>> BYTE_TO_EXPR = new HashMap<>();

    static {
        registerExpression((byte) 0, UnaryExpression.class, UnaryExpression::new);
        registerExpression((byte) 1, TernaryConditionalExpression.class, TernaryConditionalExpression::new);
        registerExpression((byte) 2, StringExpression.class, StringExpression::new);
        registerExpression((byte) 3, StatementExpression.class, StatementExpression::new);
        registerExpression((byte) 4, IdentifierExpression.class, IdentifierExpression::new);
        registerExpression((byte) 5, FloatExpression.class, FloatExpression::new);
        registerExpression((byte) 6, ExecutionScopeExpression.class, ExecutionScopeExpression::new);
        registerExpression((byte) 7, CallExpression.class, CallExpression::new);
        registerExpression((byte) 8, BinaryExpression.class, BinaryExpression::new);
        registerExpression((byte) 9, ArrayAccessExpression.class, ArrayAccessExpression::new);
        registerExpression((byte) 10, AccessExpression.class, AccessExpression::new);
    }

    public static <T extends Expression> void registerExpression(byte id, Class<T> expression, Function<ByteBuf, T> reader) {
        EXPR_TO_BYTE.put(expression, id);
        BYTE_TO_EXPR.put(id, reader);
    }

    public static void writeExpression(Expression expression, ByteBuf buf) {
        Byte id = EXPR_TO_BYTE.get(expression.getClass());
        if (id == null) throw new IllegalArgumentException("Unknown expression class: " + expression.getClass());
        buf.writeByte(id);
        expression.write(buf);
    }

    public static @NotNull Expression readExpression(ByteBuf buf) {
        Function<ByteBuf, ? extends Expression> reader = BYTE_TO_EXPR.get(buf.readByte());
        if (reader == null) throw new IllegalArgumentException("Unknown expression id in buffer");
        return reader.apply(buf);
    }

    public static List<Expression> readExpressions(ByteBuf buf) {
        return ProtocolUtils.readList(buf, ExprBytesUtils::readExpression);
    }

    public static void writeExpressions(List<Expression> list, ByteBuf buf) {
        ProtocolUtils.writeList(buf, list, ExprBytesUtils::writeExpression);
    }
}
