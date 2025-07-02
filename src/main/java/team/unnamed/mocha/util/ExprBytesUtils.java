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
import io.netty.buffer.ByteBufUtil;
import org.jetbrains.annotations.NotNull;
import team.unnamed.mocha.parser.ast.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ExprBytesUtils {
    private static final Map<Class<? extends Expression>, Byte> EXPR_TO_BYTE = new ConcurrentHashMap<>();
    private static final Map<Byte, Function<ByteBuf, ? extends Expression>> BYTE_TO_EXPR = new ConcurrentHashMap<>();

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
        buf.writeByte(EXPR_TO_BYTE.get(expression.getClass()));
        expression.write(buf);
    }

    public static @NotNull Expression readExpression(ByteBuf buf) {
        return BYTE_TO_EXPR.get(buf.readByte()).apply(buf);
    }

    public static <T> List<T> readList(ByteBuf buf, Function<ByteBuf, T> reader) {
        int count = VarIntUtils.readVarInt(buf);
        List<T> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    public static <T> void writeList(ByteBuf buf, List<T> list, BiConsumer<T, ByteBuf> writer) {
        VarIntUtils.writeVarInt(buf, list.size());
        for (T entry : list) {
            writer.accept(entry, buf);
        }
    }

    public static <T> T getEnum(T[] values, ByteBuf buf) {
        int ordinal = buf.readByte();
        if (ordinal < 0 || ordinal > values.length) {
            return values[0]; // TODO
        }
        return values[ordinal];
    }

    public static String readString(ByteBuf buf) {
        int length = VarIntUtils.readVarInt(buf);
        if (length <= 0) return null;
        String str = buf.toString(buf.readerIndex(), length, StandardCharsets.UTF_8);
        buf.skipBytes(length);
        return str;
    }

    public static void writeString(ByteBuf buf, String str) {
        int size = ByteBufUtil.utf8Bytes(str);
        VarIntUtils.writeVarInt(buf, size);
        buf.writeCharSequence(str, StandardCharsets.UTF_8);
    }
}
