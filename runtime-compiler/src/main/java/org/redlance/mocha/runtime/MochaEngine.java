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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.redlance.mocha.parser.MolangParser;

/**
 * Convenience holder that bundles a {@link MolangInterpreter} and a
 * {@link MolangCompiler} sharing the same {@link Scope}.
 *
 * <p>The engine itself does not implement the parsing / interpreter / compiler
 * APIs — those live on {@link MolangParser}, {@link MolangInterpreter} and
 * {@link MolangCompiler}, accessed via {@link #interpreter()} and
 * {@link #compiler()}. This class only exists so that callers that want both
 * can construct them in one go with a shared scope.</p>
 *
 * @since 3.0.0
 */
public final class MochaEngine<T> {
    private final MolangInterpreter<T> interpreter;
    private final MolangCompiler compiler;

    public MochaEngine(final @NotNull MolangInterpreter<T> interpreter, final @NotNull MolangCompiler compiler) {
        this.interpreter = interpreter;
        this.compiler = compiler;
    }

    /**
     * Builds a {@link MochaEngine} on top of an existing {@link MolangInterpreter},
     * sharing its {@link Scope} and entity with a freshly created
     * {@link MolangCompiler}.
     *
     * @since 5.0.0
     */
    @Contract("_ -> new")
    public static <T> @NotNull MochaEngine<T> from(final @NotNull MolangInterpreter<T> interpreter) {
        return new MochaEngine<>(interpreter, new MolangCompiler(interpreter.entity(), interpreter.scope()));
    }

    @Contract("_ -> new")
    public static <T> @NotNull MochaEngine<T> create(final T entity) {
        return from(MolangInterpreter.create(entity));
    }

    @Contract("-> new")
    public static @NotNull MochaEngine<?> create() {
        return from(MolangInterpreter.create());
    }

    /**
     * Creates a {@link MochaEngine} pre-populated with the standard
     * {@code math} / {@code variable} / {@code v} bindings.
     *
     * @since 3.0.0
     */
    @Contract("_ -> new")
    public static <T> @NotNull MochaEngine<T> createStandard(final T entity) {
        return from(MolangInterpreter.standard(entity));
    }

    @Contract("-> new")
    public static @NotNull MochaEngine<?> createStandard() {
        return from(MolangInterpreter.standard());
    }

    public @NotNull MolangInterpreter<T> interpreter() {
        return interpreter;
    }

    public @NotNull MolangCompiler compiler() {
        return compiler;
    }

    public @NotNull Scope scope() {
        return interpreter.scope();
    }
}
