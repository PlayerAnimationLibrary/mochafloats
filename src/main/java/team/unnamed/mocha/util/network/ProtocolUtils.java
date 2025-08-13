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
package team.unnamed.mocha.util.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ProtocolUtils {
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

    public static void writeEnum(Enum<?> enumValue, ByteBuf buf) {
        buf.writeByte(enumValue.ordinal());
    }

    public static <T> T readEnum(Class<T> enumClass, ByteBuf buf) {
        int ordinal = buf.readUnsignedByte();

        T[] constants = enumClass.getEnumConstants();
        if (ordinal < 0 || ordinal >= constants.length) {
            return constants[0]; // TODO
        }
        return constants[ordinal];
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
