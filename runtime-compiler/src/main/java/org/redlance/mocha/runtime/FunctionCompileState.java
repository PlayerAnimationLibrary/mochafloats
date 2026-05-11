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

import com.google.j2objc.annotations.J2ObjCIncompatible;
import org.jetbrains.annotations.NotNull;
import org.redlance.mocha.runtime.util.CaseInsensitiveStringHashMap;

import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@J2ObjCIncompatible
final class FunctionCompileState {
    private final MolangCompiler compiler;

    private final ClassDesc classDesc;
    private final CodeBuilder codeBuilder;
    private final Method method;

    private final Map<String, Object> requirements = new CaseInsensitiveStringHashMap<>();
    private final Scope scope;
    private final Map<String, Integer> argumentParameterIndexes;
    private int maxLocals = 0;

    FunctionCompileState(
            MolangCompiler compiler,
            ClassDesc classDesc,
            CodeBuilder codeBuilder,
            Method method,
            Scope scope,
            Map<String, Integer> argumentParameterIndexes
    ) {
        this.compiler = requireNonNull(compiler, "compiler");
        this.classDesc = requireNonNull(classDesc, "classDesc");
        this.codeBuilder = requireNonNull(codeBuilder, "codeBuilder");
        this.method = requireNonNull(method, "method");
        this.scope = requireNonNull(scope, "scope");
        this.argumentParameterIndexes = requireNonNull(argumentParameterIndexes, "argumentParameterIndexes");
    }

    public @NotNull MolangCompiler compiler() {
        return compiler;
    }

    public @NotNull ClassDesc classDesc() {
        return classDesc;
    }

    public @NotNull CodeBuilder codeBuilder() {
        return codeBuilder;
    }

    public @NotNull Method method() {
        return method;
    }

    public @NotNull Map<String, Object> requirements() {
        return requirements;
    }

    public @NotNull Scope scope() {
        return scope;
    }

    public @NotNull Map<String, Integer> argumentParameterIndexes() {
        return argumentParameterIndexes;
    }

    public int maxLocals() {
        return maxLocals;
    }

    public void maxLocals(int maxLocals) {
        this.maxLocals = maxLocals;
    }
}
