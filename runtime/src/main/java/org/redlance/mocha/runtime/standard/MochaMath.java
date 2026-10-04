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
package org.redlance.mocha.runtime.standard;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.redlance.mocha.runtime.ExecutionContext;
import org.redlance.mocha.runtime.binding.BindExternalFunction;
import org.redlance.mocha.runtime.binding.Binding;
import org.redlance.mocha.runtime.value.Function;
import org.redlance.mocha.runtime.value.NumberValue;
import org.redlance.mocha.runtime.value.ObjectProperty;
import org.redlance.mocha.runtime.value.ObjectValue;
import org.redlance.mocha.runtime.util.CaseInsensitiveStringHashMap;
import org.redlance.mocha.runtime.value.Value;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Math function bindings inside an object
 * binding, commonly named 'math'
 */
@Binding("math")
@BindExternalFunction(at = Math.class, name = "abs", args = {float.class}, pure = true)
@BindExternalFunction(at = Math.class, name = "max", args = {float.class, float.class}, pure = true)
@BindExternalFunction(at = Math.class, name = "min", args = {float.class, float.class}, pure = true)
public final class MochaMath implements ObjectValue {
    @Binding("pi")
    public static final float PI = (float) Math.PI;
    @Binding("e")
    public static final float E = (float) Math.E;

    private static final double RADIAN = Math.toRadians(1);

    private static final Random RANDOM = new Random();

    // the functions below back the Java bindings of this class, and
    // JavaObjectBinding.of requires a backing function to be exactly as pure as its binding
    private static final Set<String> PURE_FUNCTIONS = pureFunctionNames();

    // kept out of entries(): a backing value would stop JavaObjectBinding from inlining the fields
    private static final Map<String, ObjectProperty> CONSTANTS = new CaseInsensitiveStringHashMap<>();

    static {
        CONSTANTS.put("pi", ObjectProperty.property(NumberValue.of(PI), true));
        CONSTANTS.put("e", ObjectProperty.property(NumberValue.of(E), true));
    }

    private final Map<String, ObjectProperty> entries = new CaseInsensitiveStringHashMap<>();
    private boolean frozen;

    public MochaMath() {
        setFunction("abs", Math::abs);
        setFunction("acos", MochaMath::acos);
        setFunction("asin", MochaMath::asin);
        setFunction("atan", MochaMath::atan);
        setFunction("atan2", MochaMath::atan2);
        setFunction("ceil", MochaMath::ceil);
        setFunction("clamp", MochaMath::clamp);
        setFunction("copy_sign", MochaMath::copySign);
        setFunction("cos", MochaMath::cos);
        setFunction("die_roll", MochaMath::dieRoll);
        setFunction("die_roll_integer", MochaMath::dieRollInteger);
        setFunction("exp", MochaMath::exp);
        setFunction("floor", MochaMath::floor);
        setFunction("hermite_blend", MochaMath::hermiteBlend);
        setFunction("inverse_lerp", MochaMath::inverseLerp);
        setFunction("lerp", MochaMath::lerp);
        setFunction("lerprotate", MochaMath::lerpRotate);
        setFunction("ln",  MochaMath::log);
        setFunction("max", Math::max);
        setFunction("min", Math::min);
        setFunction("min_angle", MochaMath::minAngle);
        setFunction("mod", MochaMath::mod);
        setFunction("pow", MochaMath::pow);
        setFunction("random", MochaMath::random);
        setFunction("random_integer", MochaMath::randomInteger);
        setFunction("round", MochaMath::round);
        setFunction("sign", MochaMath::sign);
        setFunction("sin", MochaMath::sin);
        setFunction("sqrt", MochaMath::sqrt);
        setFunction("trunc", MochaMath::trunc);
        setFunction("d2r", MochaMath::d2r);
        setFunction("r2d", MochaMath::r2d);

        // all of our properties are constant
        frozen = true;
    }

    private static @NotNull Set<String> pureFunctionNames() {
        final Set<String> names = new HashSet<>();
        for (final Method method : MochaMath.class.getDeclaredMethods()) {
            final Binding binding = method.getDeclaredAnnotation(Binding.class);
            if (binding != null && binding.pure()) {
                names.addAll(Arrays.asList(binding.value()));
            }
        }
        for (final BindExternalFunction function : MochaMath.class.getAnnotationsByType(BindExternalFunction.class)) {
            if (function.pure()) {
                names.add(function.as().isEmpty() ? function.name() : function.as());
            }
        }
        return names;
    }

    @Binding(value = "ceil", pure = true)
    public static float ceil(float a) {
        return (float) StrictMath.ceil(a);
    }

    @Binding(value = "exp", pure = true)
    public static float exp(float a) {
        return (float) StrictMath.exp(a);
    }

    @Binding(value = "floor", pure = true)
    public static float floor(float a) {
        return (float) StrictMath.floor(a);
    }

    @Binding(value = "ln", pure = true)
    public static float log(float a) {
        return (float) StrictMath.log(a);
    }

    @Binding(value = "pow", pure = true)
    public static float pow(float a, float b) {
        return (float) StrictMath.pow(a, b);
    }

    @Binding(value = "sqrt", pure = true)
    public static float sqrt(float a) {
        return (float) StrictMath.sqrt(a);
    }

    private static float radify(float n) {
        return (((n + 180) % 360) + 180) % 360;
    }

    @Binding(value = "acos", skipChecking = true, pure = true)
    public static float acos(final float value) {
        return (float) NumberValue.normalize(Math.acos(value) / RADIAN);
    }

    @Binding(value = "asin", skipChecking = true, pure = true)
    public static float asin(final float value) {
        return (float) NumberValue.normalize(Math.asin(value) / RADIAN);
    }

    @Binding(value = "atan", pure = true)
    public static float atan(final float value) {
        return (float) (Math.atan(value) / RADIAN);
    }

    @Binding(value = "atan2", pure = true)
    public static float atan2(final float y, final float x) {
        return (float) (Math.atan2(y, x) / RADIAN);
    }

    @Binding(value = "clamp", pure = true)
    public static float clamp(final float value, final float min, final float max) {
        return Math.max(Math.min(value, max), min);
    }

    @Binding(value = "copy_sign", pure = true)
    public static float copySign(float magnitude, final float sign) {
        magnitude = Math.abs(magnitude);
        return sign < 0 ? -magnitude : magnitude;
    }

    @Binding(value = "cos", pure = true)
    public static float cos(final float value) {
        return (float) Math.cos(value * RADIAN);
    }

    // the sum of "amount" random numbers between low and high
    @Binding("die_roll")
    public static float dieRoll(final float amount, final float low, final float high) {
        float result = 0;
        for (int i = 0; i < amount; i++) {
            result += random(low, high);
        }
        return result;
    }

    // the sum of "amount" random whole numbers from low to high, both included
    @Binding("die_roll_integer")
    public static float dieRollInteger(final float amount, final float low, final float high) {
        int result = 0;
        for (int i = 0; i < amount; i++) {
            result += randomInteger(low, high);
        }
        return result;
    }

    @Binding(value = "hermite_blend", pure = true)
    public static float hermiteBlend(final float t) {
        final float t2 = t * t;
        final float t3 = t2 * t;
        return 3 * t2 - 2 * t3;
    }

    @Binding(value = "inverse_lerp", pure = true)
    public static float inverseLerp(final float start, final float end, final float lerp) {
        if (start == end) return 0;
        return (lerp - start) / (end - start);
    }

    @Binding(value = "lerp", pure = true)
    public static float lerp(final float start, final float end, final float lerp) {
        return start + lerp * (end - start);
    }

    @Binding(value = "lerprotate", pure = true)
    public static float lerpRotate(float start, float end, float lerp) {
        start = radify(start);
        end = radify(end);

        if (start > end) {
            // swap
            float tmp = start;
            start = end;
            end = tmp;
        }

        float diff = end - start;
        if (diff > 180F) {
            return radify(end + lerp * (360F - diff));
        } else {
            return start + lerp * diff;
        }
    }

    @Binding(value = "min_angle", pure = true)
    public static float minAngle(float angle) {
        while (angle > 180)
            angle -= 360;
        while (angle < -180)
            angle += 360;
        return angle;
    }

    @Binding(value = "mod", pure = true)
    public static float mod(final float a, final float b) {
        return a % b;
    }

    // between the two bounds, in either order (Random.nextFloat(min, max) would throw unless min < max)
    @Binding("random")
    public static float random(final float min, final float max) {
        return min + RANDOM.nextFloat() * (max - min);
    }

    // from the smaller bound to the bigger one, both included
    @Binding("random_integer")
    public static int randomInteger(final float min, final float max) {
        final int low = (int) Math.min(min, max);
        final int high = (int) Math.max(min, max);
        return (int) (low + RANDOM.nextLong((long) high - low + 1));
    }

    @Binding(value = "sign", pure = true)
    public static float sign(final float value) {
        if (value < 0) return -1;
        if (value > 0) return 1;
        return 0;
    }

    @Binding(value = "sin", pure = true)
    public static float sin(final float value) {
        return (float) Math.sin(value * RADIAN);
    }

    // halves round away from zero like in Bedrock, so -2.5 is -3 (Math.round would give -2)
    @Binding(value = "round", pure = true)
    public static float round(final float value) {
        return (float) Math.copySign(Math.floor(Math.abs((double) value) + 0.5), value);
    }

    @Binding(value = "trunc", pure = true)
    public static float trunc(final float value) {
        return value - value % 1;
    }

    @Binding(value = "d2r", pure = true)
    public static float d2r(final float value) {
        return (float) Math.toRadians(value);
    }

    @Binding(value = "r2d", pure = true)
    public static float r2d(final float value) {
        return (float) Math.toDegrees(value);
    }

    @Override
    public @Nullable ObjectProperty getProperty(final @NotNull String name) {
        final ObjectProperty property = entries.get(name);
        return property != null ? property : CONSTANTS.get(name);
    }

    @Override
    public boolean set(final @NotNull String name, final @Nullable Value value) {
        if (frozen || value == null) {
            return false;
        }
        final Value function = value instanceof Function<?> && PURE_FUNCTIONS.contains(name)
                ? new PureFunction((Function<?>) value)
                : value;
        entries.put(name, ObjectProperty.property(function, true));
        return true;
    }

    @Override
    public @NotNull Map<String, ObjectProperty> entries() {
        return Collections.unmodifiableMap(entries);
    }

    private static final class PureFunction implements Function<Object> {
        private final Function<Object> function;

        @SuppressWarnings("unchecked")
        PureFunction(final @NotNull Function<?> function) {
            this.function = (Function<Object>) function;
        }

        @Override
        public @Nullable Value evaluate(final @NotNull ExecutionContext<Object> context, final @NotNull Arguments arguments) {
            return function.evaluate(context, arguments);
        }

        @Override
        public boolean pure() {
            return true;
        }
    }
}
