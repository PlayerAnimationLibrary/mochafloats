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
package org.redlance.mocha.runtime;

import com.google.j2objc.annotations.AutoreleasePool;
import com.google.j2objc.annotations.LoopTranslation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.redlance.mocha.parser.MolangParser;
import org.redlance.mocha.parser.ParseException;
import org.redlance.mocha.parser.ast.Expression;
import org.redlance.mocha.parser.ast.FloatExpression;
import org.redlance.mocha.runtime.binding.Binding;
import org.redlance.mocha.runtime.binding.JavaObjectBinding;
import org.redlance.mocha.runtime.standard.MochaMath;
import org.redlance.mocha.runtime.value.MutableObjectBinding;
import org.redlance.mocha.runtime.value.NumberValue;
import org.redlance.mocha.runtime.value.ObjectValue;
import org.redlance.mocha.runtime.value.Value;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Stateful entry point for interpretation. Holds the entity, the global
 * {@link Scope} and the runtime-side configuration (parse exception handler,
 * "warn on reflective function usage" flag), and exposes {@code eval(...)} /
 * {@code prepareEval(...)} variants plus binding helpers.
 *
 * <p>Each {@code eval} call gets its own copy of the scope with a fresh
 * {@code temp} / {@code t} binding so concurrent or nested evaluations do
 * not see each other's local state.</p>
 *
 * @since 5.0.0
 */
public final class MolangInterpreter<T> {
    private final T entity;
    private final Scope scope;
    private Consumer<@NotNull ParseException> parseExceptionHandler;
    private boolean warnOnReflectiveFunctionUsage;

    public MolangInterpreter(final @Nullable T entity, final @NotNull Scope scope) {
        this.entity = entity;
        this.scope = requireNonNull(scope, "scope");
    }

    /**
     * Creates an interpreter with the given {@code entity} and an empty,
     * user-built {@link Scope}.
     *
     * @since 5.0.0
     */
    @Contract("_, _ -> new")
    public static <T> @NotNull MolangInterpreter<T> create(final @Nullable T entity, final @NotNull Consumer<Scope.Builder> scopeBuilder) {
        final Scope.Builder builder = Scope.builder();
        scopeBuilder.accept(builder);
        return new MolangInterpreter<>(entity, builder.build());
    }

    @Contract("_ -> new")
    public static <T> @NotNull MolangInterpreter<T> create(final @Nullable T entity) {
        return create(entity, b -> {
        });
    }

    @Contract("-> new")
    public static @NotNull MolangInterpreter<?> create() {
        return create(null);
    }

    /**
     * Creates an interpreter pre-populated with the standard
     * {@code math} / {@code variable} / {@code v} bindings. Lives in the
     * runtime module so a caller that only needs interpretation does not have
     * to depend on the compiler module.
     *
     * @since 5.0.0
     */
    @Contract("_ -> new")
    public static <T> @NotNull MolangInterpreter<T> standard(final @Nullable T entity) {
        return create(entity, builder -> {
            builder.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
            final MutableObjectBinding variableBinding = new MutableObjectBinding();
            builder.set("variable", variableBinding);
            builder.set("v", variableBinding);
        });
    }

    @Contract("-> new")
    public static @NotNull MolangInterpreter<?> standard() {
        return standard(null);
    }

    public @Nullable T entity() {
        return entity;
    }

    /**
     * Returns the bindings for this interpreter instance.
     *
     * @since 3.0.0
     */
    public @NotNull Scope scope() {
        return scope;
    }

    //#region INTERPRETER API

    /**
     * Evaluates the given {@code expressions}, these expressions are already
     * parsed and are interpreted as fast as possible.
     *
     * @param expressions    The expressions to evaluate.
     * @param scopeConsumer  Optional callback to populate per-call bindings on
     *                       the local scope copy before evaluation.
     * @return The result of the evaluation.
     * @since 4.1.0
     */
    @AutoreleasePool
    public float eval(final @NotNull List<Expression> expressions, final @Nullable Consumer<Scope> scopeConsumer) {
        if (expressions.size() == 1 && expressions.get(0) instanceof FloatExpression expression) {
            return expression.value();
        }

        final Scope local = scope.copy();
        {
            final MutableObjectBinding temp = new MutableObjectBinding();
            local.set("temp", temp);
            local.set("t", temp);
        }
        if (scopeConsumer != null && !local.readOnly()) scopeConsumer.accept(local);
        local.readOnly(true);

        final ExpressionInterpreter<T> evaluator = new ExpressionInterpreter<>(entity, local);
        evaluator.warnOnReflectiveFunctionUsage(warnOnReflectiveFunctionUsage);
        Value lastResult = NumberValue.zero();

        for (@LoopTranslation(LoopTranslation.LoopStyle.FAST_ENUMERATION) final Expression expression : expressions) {
            lastResult = expression.visit(evaluator);
            final Value returnValue = evaluator.popReturnValue();
            if (returnValue != null) {
                lastResult = returnValue;
                break;
            }
        }

        return lastResult == null ? 0F : lastResult.getAsNumber();
    }

    /**
     * @see #eval(List, Consumer)
     * @since 3.0.0
     */
    public float eval(final @NotNull List<Expression> expressions) {
        return eval(expressions, null);
    }

    /**
     * Parses and evaluates the given Molang source.
     *
     * <p>Note that this method does not cache parsed expressions. If you want
     * to re-use parsed expressions, parse them yourself with
     * {@link MolangParser} and use {@link #eval(List, Consumer)}.</p>
     *
     * <p>Parse errors are routed through the handler set by
     * {@link #handleParseExceptions(Consumer)} and the call returns {@code 0}.</p>
     *
     * @since 4.1.0
     */
    public float eval(final @NotNull Reader source, final @Nullable Consumer<Scope> scopeConsumer) {
        final List<Expression> parsed;
        try {
            parsed = MolangParser.parseAll(source);
        } catch (final ParseException e) {
            if (parseExceptionHandler != null) parseExceptionHandler.accept(e);
            return 0F;
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to read from given reader", e);
        }
        return eval(parsed, scopeConsumer);
    }

    /**
     * @see #eval(Reader, Consumer)
     * @since 3.0.0
     */
    public float eval(final @NotNull Reader source) {
        return eval(source, null);
    }

    /**
     * Parses and evaluates the given Molang source string.
     *
     * @see #eval(Reader, Consumer)
     * @since 4.1.0
     */
    public float eval(final @NotNull String source, final @Nullable Consumer<Scope> scopeConsumer) {
        requireNonNull(source, "source");
        try (final StringReader reader = new StringReader(source)) {
            return eval(reader, scopeConsumer);
        }
    }

    /**
     * @see #eval(String, Consumer)
     * @since 3.0.0
     */
    public float eval(final @NotNull String source) {
        return eval(source, null);
    }

    /**
     * Parses {@code reader} once and returns a {@link Supplier} that runs the
     * cached AST through the interpreter on each call. Parse errors go through
     * {@link #handleParseExceptions(Consumer)}; a failed parse yields a
     * supplier that always produces {@code 0}.
     *
     * @since 4.1.0
     */
    public @NotNull Supplier<@NotNull Float> prepareEval(final @NotNull Reader reader, final @Nullable Consumer<Scope> scopeConsumer) {
        final List<Expression> parsed;
        try {
            parsed = MolangParser.parseAll(reader);
        } catch (final ParseException e) {
            if (parseExceptionHandler != null) parseExceptionHandler.accept(e);
            return () -> 0F;
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to read from given reader", e);
        }
        return () -> eval(parsed, scopeConsumer);
    }

    /**
     * @see #prepareEval(Reader, Consumer)
     * @since 3.0.0
     */
    public @NotNull Supplier<@NotNull Float> prepareEval(final @NotNull Reader reader) {
        return prepareEval(reader, null);
    }

    /**
     * @see #prepareEval(Reader, Consumer)
     * @since 4.1.0
     */
    public @NotNull Supplier<@NotNull Float> prepareEval(final @NotNull String code, final @Nullable Consumer<Scope> scopeConsumer) {
        requireNonNull(code, "code");
        try (final StringReader reader = new StringReader(code)) {
            return prepareEval(reader, scopeConsumer);
        }
    }

    /**
     * @see #prepareEval(String, Consumer)
     * @since 3.0.0
     */
    public @NotNull Supplier<@NotNull Float> prepareEval(final @NotNull String code) {
        return prepareEval(code, null);
    }

    //#endregion

    //#region BINDING API

    /**
     * Binds the given {@code clazz}'s static fields and methods into this
     * interpreter's {@link Scope}.
     *
     * <p>Fields and methods are bound in the following format:</p>
     * <pre>
     *     namespace.field
     *     namespace.method()
     *     namespace.method(arg1, arg2)
     * </pre>
     *
     * <p>Where {@code namespace} comes from the class' {@link Binding}
     * annotation, and field/method names from {@link Binding} annotations on
     * the members.</p>
     *
     * @see Binding
     * @since 3.0.0
     */
    public void bind(final @NotNull Class<?> clazz) {
        final JavaObjectBinding javaObjectBinding = JavaObjectBinding.of(clazz, null, null);
        for (final String name : javaObjectBinding.names()) {
            scope.set(name, javaObjectBinding);
        }
    }

    /**
     * Binds the given {@code instance}'s non-static fields and methods.
     *
     * <p>Fields and methods are bound in the format:</p>
     * <pre>
     *     name.field
     *     name.method()
     *     name.method(arg1, arg2)
     * </pre>
     *
     * <p>Where {@code name} comes from the {@code name} parameter, and field
     * /method names from {@link Binding} annotations.</p>
     *
     * @param clazz    The instance's class (or interface) to use.
     * @param instance The instance to bind.
     * @param name     The name to bind the instance to.
     * @param aliases  Additional names to bind the instance to.
     * @param <B>      The instance's type.
     * @since 3.0.0
     */
    public <B> void bindInstance(final @NotNull Class<? super B> clazz, final @NotNull B instance, final @NotNull String name, final @NotNull String @NotNull ... aliases) {
        final JavaObjectBinding javaObjectBinding = JavaObjectBinding.of(clazz, instance, null);
        scope.set(name, javaObjectBinding);
        for (final String alias : aliases) {
            scope.set(alias, javaObjectBinding);
        }
    }

    //#endregion

    //#region CONFIGURATION API

    public boolean warnOnReflectiveFunctionUsage() {
        return warnOnReflectiveFunctionUsage;
    }

    /**
     * Sets the boolean value for the "warn on reflective function usage"
     * option.
     *
     * <p>When set to true, {@link #eval} may log a warning when evaluating
     * code that includes a call to a function that was registered using only
     * annotations and therefore has to be invoked via Reflection, taking some
     * extra time.</p>
     *
     * <p>Note that this behavior can be avoided by setting an
     * {@link ObjectValue} when binding static or non-static methods and fields.</p>
     *
     * <p>By default this is false.</p>
     *
     * @return This interpreter instance.
     * @since 3.0.0
     */
    @Contract("_ -> this")
    public @NotNull MolangInterpreter<T> warnOnReflectiveFunctionUsage(final boolean warnOnReflectiveFunctionUsage) {
        this.warnOnReflectiveFunctionUsage = warnOnReflectiveFunctionUsage;
        return this;
    }

    /**
     * Sets the {@link ParseException} handler. This handler will be called
     * whenever an internal call to {@link MolangParser} from
     * {@link #eval(Reader, Consumer)} or {@link #prepareEval(Reader, Consumer)}
     * fails.
     *
     * <p>Usually useful for logging/debugging purposes.</p>
     *
     * <p>By default this is null.</p>
     *
     * @return This interpreter instance.
     * @since 3.0.0
     */
    @Contract("_ -> this")
    public @NotNull MolangInterpreter<T> handleParseExceptions(final @Nullable Consumer<@NotNull ParseException> exceptionHandler) {
        this.parseExceptionHandler = exceptionHandler;
        return this;
    }

    //#endregion
}
