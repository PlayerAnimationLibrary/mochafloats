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

import com.google.j2objc.annotations.J2ObjCIncompatible;
import org.jetbrains.annotations.NotNull;
import team.unnamed.mocha.runtime.TypeCastException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.Map;
import java.util.Set;

import static java.lang.constant.ConstantDescs.*;
import static java.util.Objects.requireNonNull;

@J2ObjCIncompatible
public class ClassFileUtil {

    // Shared wrapper ClassDesc constants to avoid repeated ClassDesc.of() allocations
    private static final ClassDesc CD_W_Boolean = ClassDesc.of("java.lang.Boolean");
    private static final ClassDesc CD_W_Byte = ClassDesc.of("java.lang.Byte");
    private static final ClassDesc CD_W_Character = ClassDesc.of("java.lang.Character");
    private static final ClassDesc CD_W_Short = ClassDesc.of("java.lang.Short");
    private static final ClassDesc CD_W_Integer = ClassDesc.of("java.lang.Integer");
    private static final ClassDesc CD_W_Long = ClassDesc.of("java.lang.Long");
    private static final ClassDesc CD_W_Float = ClassDesc.of("java.lang.Float");
    private static final ClassDesc CD_W_Double = ClassDesc.of("java.lang.Double");

    private static final Set<ClassDesc> WRAPPER_TYPES = Set.of(
            CD_W_Boolean, CD_W_Byte, CD_W_Character, CD_W_Short,
            CD_W_Integer, CD_W_Long, CD_W_Float, CD_W_Double
    );

    private static final Map<ClassDesc, ClassDesc> PRIMITIVE_TO_WRAPPER = Map.of(
            CD_boolean, CD_W_Boolean, CD_byte, CD_W_Byte,
            CD_char, CD_W_Character, CD_short, CD_W_Short,
            CD_int, CD_W_Integer, CD_long, CD_W_Long,
            CD_float, CD_W_Float, CD_double, CD_W_Double
    );

    private static final Map<ClassDesc, String> WRAPPER_UNBOX_METHOD = Map.of(
            CD_W_Boolean, "booleanValue", CD_W_Byte, "byteValue",
            CD_W_Character, "charValue", CD_W_Short, "shortValue",
            CD_W_Integer, "intValue", CD_W_Long, "longValue",
            CD_W_Float, "floatValue", CD_W_Double, "doubleValue"
    );

    // ClassValue cache for Class -> ClassDesc mapping (lock-free, GC-friendly)
    private static final ClassValue<ClassDesc> CLASS_DESC_CACHE = new ClassValue<>() {
        @Override
        protected ClassDesc computeValue(final @NotNull Class<?> type) {
            return type.describeConstable().orElseThrow(
                    () -> new IllegalStateException("Cannot create ClassDesc for: " + type)
            );
        }
    };

    public static @NotNull ClassDesc classDescOf(final @NotNull Class<?> javaClass) {
        return CLASS_DESC_CACHE.get(javaClass);
    }

    public static boolean isWrapper(final @NotNull ClassDesc type) {
        requireNonNull(type, "type");
        return WRAPPER_TYPES.contains(type);
    }

    public static boolean isPrimitiveOrWrapper(final @NotNull ClassDesc type) {
        requireNonNull(type, "type");
        return type.isPrimitive() || isWrapper(type);
    }

    public static void addConstZero(final @NotNull CodeBuilder cb, final @NotNull ClassDesc type) {
        requireNonNull(cb, "cb");
        requireNonNull(type, "type");
        if (type.equals(CD_void)) {
            // nothing
        } else if (type.equals(CD_float)) {
            cb.fconst_0();
        } else if (type.equals(CD_double)) {
            cb.dconst_0();
        } else if (type.equals(CD_long)) {
            cb.lconst_0();
        } else if (type.isPrimitive()) {
            cb.iconst_0();
        } else {
            cb.aconst_null();
        }
    }

    public static void addReturn(final @NotNull CodeBuilder cb, final @NotNull ClassDesc type) {
        requireNonNull(cb, "cb");
        if (type == null || type.equals(CD_void)) {
            cb.return_();
        } else if (type.equals(CD_float)) {
            cb.freturn();
        } else if (type.equals(CD_double)) {
            cb.dreturn();
        } else if (type.equals(CD_long)) {
            cb.lreturn();
        } else if (type.isPrimitive()) {
            cb.ireturn();
        } else {
            cb.areturn();
        }
    }

    public static void addLoad(final @NotNull CodeBuilder cb, final int index, final @NotNull ClassDesc type) {
        requireNonNull(cb, "cb");
        requireNonNull(type, "type");
        if (type.equals(CD_float)) {
            cb.fload(index);
        } else if (type.equals(CD_double)) {
            cb.dload(index);
        } else if (type.equals(CD_long)) {
            cb.lload(index);
        } else if (type.isPrimitive()) {
            cb.iload(index);
        } else {
            cb.aload(index);
        }
    }

    public static void addCast(final @NotNull CodeBuilder cb, final @NotNull ClassDesc from, final @NotNull ClassDesc to) {
        requireNonNull(cb, "cb");
        requireNonNull(from, "from");
        requireNonNull(to, "to");

        if (from.equals(to)) {
            return;
        }

        if (from.equals(CD_void) || to.equals(CD_void)) {
            throw new IllegalArgumentException("Cannot cast to or from void");
        }

        if (from.isPrimitive()) {
            if (to.isPrimitive()) {
                if (from.equals(CD_int) || from.equals(CD_byte) || from.equals(CD_boolean)
                        || from.equals(CD_short) || from.equals(CD_char)) {
                    addCastIntTo(cb, to);
                } else if (from.equals(CD_long)) {
                    addCastLongTo(cb, to);
                } else if (from.equals(CD_float)) {
                    addCastFloatTo(cb, to);
                } else if (from.equals(CD_double)) {
                    addCastDoubleTo(cb, to);
                } else {
                    throw new TypeCastException("Cannot cast unknown primitive type: " + from.displayName());
                }
            } else {
                // primitive to wrapper (boxing)
                final ClassDesc wrapperDesc = PRIMITIVE_TO_WRAPPER.get(from);
                if (wrapperDesc == null) {
                    throw new TypeCastException("Cannot box unknown primitive type: " + from.displayName());
                }
                cb.invokestatic(wrapperDesc, "valueOf", MethodTypeDesc.of(wrapperDesc, from));
            }
        } else {
            if (to.isPrimitive()) {
                // wrapper to primitive (unboxing)
                final String unboxMethod = WRAPPER_UNBOX_METHOD.get(from);
                if (unboxMethod != null) {
                    cb.invokevirtual(from, unboxMethod, MethodTypeDesc.of(to));
                } else {
                    throw new TypeCastException("Cannot cast unknown type: " + from.displayName());
                }
            } else {
                // object to object
                cb.checkcast(to);
            }
        }
    }

    public static void addCastIntTo(final @NotNull CodeBuilder cb, final @NotNull ClassDesc to) {
        requireNonNull(cb, "cb");
        requireNonNull(to, "to");

        if (to.equals(CD_int)) {
            return;
        }

        if (to.equals(CD_byte)) {
            cb.i2b();
        } else if (to.equals(CD_boolean)) {
            Label pushZero = cb.newLabel();
            Label end = cb.newLabel();
            cb.ifeq(pushZero);
            cb.iconst_1();
            cb.goto_(end);
            cb.labelBinding(pushZero);
            cb.iconst_0();
            cb.labelBinding(end);
        } else if (to.equals(CD_short)) {
            cb.i2s();
        } else if (to.equals(CD_char)) {
            cb.i2c();
        } else if (to.equals(CD_long)) {
            cb.i2l();
        } else if (to.equals(CD_float)) {
            cb.i2f();
        } else if (to.equals(CD_double)) {
            cb.i2d();
        } else if (to.equals(CD_void)) {
            throw new TypeCastException("Cannot cast int to void");
        } else {
            throw new TypeCastException("Cannot cast int to unknown type: " + to.displayName());
        }
    }

    public static void addCastDoubleTo(final @NotNull CodeBuilder cb, final @NotNull ClassDesc to) {
        requireNonNull(cb, "cb");
        requireNonNull(to, "to");

        if (to.equals(CD_double)) {
            return;
        }

        if (to.equals(CD_int)) {
            cb.d2i();
        } else if (to.equals(CD_long)) {
            cb.d2l();
        } else if (to.equals(CD_float)) {
            cb.d2f();
        } else if (to.equals(CD_void)) {
            throw new IllegalArgumentException("Cannot cast double to void");
        } else {
            cb.d2i();
            try {
                addCastIntTo(cb, to);
            } catch (final TypeCastException e) {
                throw new TypeCastException("Cannot cast double to unknown type: " + to.displayName());
            }
        }
    }

    public static void addCastLongTo(final @NotNull CodeBuilder cb, final @NotNull ClassDesc to) {
        requireNonNull(cb, "cb");
        requireNonNull(to, "to");

        if (to.equals(CD_long)) {
            return;
        }

        if (to.equals(CD_int)) {
            cb.l2i();
        } else if (to.equals(CD_double)) {
            cb.l2d();
        } else if (to.equals(CD_float)) {
            cb.l2f();
        } else if (to.equals(CD_void)) {
            throw new IllegalArgumentException("Cannot cast long to void");
        } else {
            cb.l2i();
            try {
                addCastIntTo(cb, to);
            } catch (final TypeCastException e) {
                throw new TypeCastException("Cannot cast long to unknown type: " + to.displayName());
            }
        }
    }

    public static void addCastFloatTo(final @NotNull CodeBuilder cb, final @NotNull ClassDesc to) {
        requireNonNull(cb, "cb");
        requireNonNull(to, "to");

        if (to.equals(CD_float)) {
            return;
        }

        if (to.equals(CD_int)) {
            cb.f2i();
        } else if (to.equals(CD_double)) {
            cb.f2d();
        } else if (to.equals(CD_long)) {
            cb.f2l();
        } else if (to.equals(CD_void)) {
            throw new IllegalArgumentException("Cannot cast float to void");
        } else {
            cb.f2i();
            try {
                addCastIntTo(cb, to);
            } catch (final TypeCastException e) {
                throw new TypeCastException("Cannot cast float to unknown type: " + to.displayName());
            }
        }
    }
}
