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
package team.unnamed.mocha.runtime;

import com.google.j2objc.annotations.J2ObjCIncompatible;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.unnamed.mocha.parser.MolangParser;
import team.unnamed.mocha.parser.ParseException;
import team.unnamed.mocha.parser.ast.Expression;
import team.unnamed.mocha.runtime.compiled.MochaCompiledFunction;
import team.unnamed.mocha.runtime.compiled.Named;
import team.unnamed.mocha.util.CaseInsensitiveStringHashMap;
import team.unnamed.mocha.util.ClassFileUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static team.unnamed.mocha.util.ClassFileUtil.classDescOf;

@J2ObjCIncompatible
public final class MolangCompiler {
    private static final AtomicLong CLASS_NAME_COUNTER = new AtomicLong();

    private final Object entity;
    private final Scope scope;
    private Consumer<byte @NotNull []> postCompile;
    private Consumer<@NotNull ParseException> parseExceptionHandler;

    public MolangCompiler(final @Nullable Object entity, final @NotNull Scope scope) {
        this.entity = entity;
        this.scope = requireNonNull(scope, "scope");
    }

    public @Nullable Object entity() {
        return entity;
    }

    /**
     * Sets the post-compile function, called after a script is compiled to a
     * new class but before the class is loaded. The argument is the class
     * bytecode, useful for writing it to a file for debugging.
     *
     * <p>By default this is null.</p>
     *
     * @return This compiler instance.
     * @since 3.0.0
     */
    @Contract("_ -> this")
    public @NotNull MolangCompiler postCompile(final @Nullable Consumer<byte @NotNull []> postCompile) {
        this.postCompile = postCompile;
        return this;
    }

    /**
     * Sets the {@link ParseException} handler. Called whenever an internal
     * call to {@link MolangParser} from {@link #compile(Reader, Class)} or
     * its overloads fails. After the handler runs, the offending compile
     * call falls back to compiling an empty body.
     *
     * <p>By default this is null.</p>
     *
     * @return This compiler instance.
     * @since 3.0.0
     */
    @Contract("_ -> this")
    public @NotNull MolangCompiler handleParseExceptions(final @Nullable Consumer<@NotNull ParseException> exceptionHandler) {
        this.parseExceptionHandler = exceptionHandler;
        return this;
    }

    public <T extends MochaCompiledFunction> @NotNull T compile(final @NotNull List<Expression> expressions, final @NotNull Class<T> clazz) {
        requireNonNull(expressions, "expressions");
        requireNonNull(clazz, "clazz");

        if (clazz == MochaFunction.class && expressions.isEmpty()) {
            // no expressions and the target type is MochaFunction,
            // we know the NOP function
            return clazz.cast(MochaFunction.nop());
        }

        if (!clazz.isInterface()) {
            throw new IllegalArgumentException("Target type must be an interface: " + clazz.getName());
        }

        Method implementedMethod = null;
        for (final Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.isDefault()) {
                continue;
            }
            if (implementedMethod != null) {
                throw new IllegalArgumentException("Target type must have only one method: " + clazz.getName());
            }
            implementedMethod = method;
        }

        if (implementedMethod == null) {
            throw new IllegalArgumentException("Target type must have a method to implement: " + clazz.getName());
        }

        final Map<String, Integer> argumentParameterIndexes = new CaseInsensitiveStringHashMap<>();
        final ClassDesc[] paramDescs;

        // check method parameter types
        {
            final Parameter[] parameters = implementedMethod.getParameters();
            paramDescs = new ClassDesc[parameters.length];

            for (int i = 0; i < parameters.length; ++i) {
                final Parameter parameter = parameters[i];
                final Named named = parameter.getDeclaredAnnotation(Named.class);
                final String name;

                if (named != null) {
                    name = named.value();
                } else if (parameter.isNamePresent()) {
                    name = parameter.getName();
                } else {
                    throw new IllegalArgumentException("Parameter " + parameter.getName() + " (index " + i
                            + ") must be annotated with @Named and specify a name");
                }

                argumentParameterIndexes.put(name, i);
                paramDescs[i] = classDescOf(parameter.getType());
            }
        }

        final ClassDesc interfaceDesc = classDescOf(clazz);
        final String scriptClassName = getClass().getPackage().getName() + ".MolangFunctionImpl_" + clazz.getSimpleName() + "_" + implementedMethod.getName()
                + "_" + Long.toHexString(CLASS_NAME_COUNTER.incrementAndGet());

        final ClassDesc scriptClassDesc = ClassDesc.of(scriptClassName);
        final ClassDesc returnDesc = classDescOf(implementedMethod.getReturnType());
        final MethodTypeDesc mainMethodTypeDesc = MethodTypeDesc.of(returnDesc, paramDescs);

        // We need to capture requirements and other state from within the lambda
        final Map<String, Object> requirements = new CaseInsensitiveStringHashMap<>();
        final Method finalMethod = implementedMethod;

        final byte[] classBytes = ClassFile.of().build(scriptClassDesc, classBuilder -> {
            classBuilder.withFlags(Modifier.PUBLIC | Modifier.FINAL);
            classBuilder.withInterfaceSymbols(interfaceDesc);

            // compute initial max locals
            final int initialMaxLocals;
            {
                int maxLocals = 1; // 1: this
                for (final ClassDesc paramType : paramDescs) {
                    if (paramType.equals(ConstantDescs.CD_double) || paramType.equals(ConstantDescs.CD_long)) {
                        maxLocals += 2;
                    } else {
                        maxLocals++;
                    }
                }
                initialMaxLocals = maxLocals;
            }

            // Add main method
            classBuilder.withMethod(
                    finalMethod.getName(),
                    mainMethodTypeDesc,
                    Modifier.PUBLIC | Modifier.FINAL,
                    methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                final FunctionCompileState compileState = new FunctionCompileState(
                        this, scriptClassDesc, codeBuilder, finalMethod, scope, argumentParameterIndexes
                );
                compileState.maxLocals(initialMaxLocals);

                // Copy requirements reference so visitor can populate it
                final Map<String, Object> stateRequirements = compileState.requirements();

                if (expressions.isEmpty()) {
                    ClassFileUtil.addConstZero(codeBuilder, returnDesc);
                    ClassFileUtil.addReturn(codeBuilder, returnDesc);
                } else {
                    final MolangCompilingVisitor compiler = new MolangCompilingVisitor(compileState);
                    CompileVisitResult lastVisitResult = null;

                    final ExpressionInliner inliner = new ExpressionInliner(new ExpressionInterpreter<>(null, scope), scope);

                    for (final Expression expression : expressions) {
                        lastVisitResult = expression.visit(inliner).visit(compiler);
                    }

                    if (lastVisitResult == null || !lastVisitResult.returned()) {
                        if (lastVisitResult == null || !returnDesc.equals(lastVisitResult.lastPushedType())) {
                            ClassFileUtil.addCast(
                                    codeBuilder,
                                    lastVisitResult == null ? ConstantDescs.CD_float : lastVisitResult.lastPushedType(),
                                    returnDesc
                            );
                        }

                        compiler.endVisit();
                    }
                }

                // Copy requirements from compile state to outer map
                requirements.putAll(stateRequirements);
            }));

            // Add fields for requirements (populated during method compilation above)
            for (final Map.Entry<String, Object> entry : requirements.entrySet()) {
                final String fieldName = entry.getKey();
                final Object fieldValue = entry.getValue();
                final ClassDesc fieldType = classDescOf(fieldValue.getClass());
                classBuilder.withField(fieldName, fieldType, Modifier.PRIVATE);
            }

            // Add constructor
            final ClassDesc[] constructorParamDescs = new ClassDesc[requirements.size()];
            int j = 0;
            for (final Map.Entry<String, Object> entry : requirements.entrySet()) {
                constructorParamDescs[j] = classDescOf(entry.getValue().getClass());
                ++j;
            }

            final MethodTypeDesc constructorTypeDesc = MethodTypeDesc.of(ConstantDescs.CD_void, constructorParamDescs);
            classBuilder.withMethod(
                    ConstantDescs.INIT_NAME,
                    constructorTypeDesc,
                    Modifier.PUBLIC,
                    methodBuilder -> methodBuilder.withCode(ctorCode -> {
                ctorCode.aload(0);
                ctorCode.invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);

                int parameterIndex = 0;
                for (final Map.Entry<String, Object> entry : requirements.entrySet()) {
                    final String fieldName = entry.getKey();
                    final Object fieldValue = entry.getValue();
                    final ClassDesc fieldType = classDescOf(fieldValue.getClass());
                    ctorCode.aload(0); // this
                    ctorCode.aload(parameterIndex + 1); // parameter
                    ctorCode.putfield(scriptClassDesc, fieldName, fieldType);
                    parameterIndex++;
                }
                ctorCode.return_();
            }));
        });

        if (postCompile != null) {
            postCompile.accept(classBytes);
        }

        final Class<?> compiledClass;
        try {
            compiledClass = MethodHandles.lookup()
                    .defineHiddenClass(classBytes, true, MethodHandles.Lookup.ClassOption.NESTMATE)
                    .lookupClass();
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException("Couldn't define hidden class", e);
        }

        // find the constructor with the requirements
        final Class<?>[] constructorParameterTypes = new Class[requirements.size()];
        final Object[] constructorArguments = new Object[requirements.size()];
        int i = 0;
        for (final Object requirement : requirements.values()) {
            constructorParameterTypes[i] = requirement.getClass();
            constructorArguments[i] = requirement;
            ++i;
        }

        final Constructor<?> constructor;
        try {
            constructor = compiledClass.getDeclaredConstructor(constructorParameterTypes);
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException("Couldn't find constructor with parameters " + requirements.keySet(), e);
        }
        final Object instance;
        try {
            instance = constructor.newInstance(constructorArguments);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Couldn't instantiate script class", e);
        }
        return clazz.cast(instance);
    }

    /**
     * Parses {@code reader} and compiles the resulting AST into the given
     * {@code interfaceType}. Parse errors are routed through
     * {@link #handleParseExceptions(Consumer)}; on a parse failure the
     * compiler falls back to compiling an empty body.
     *
     * @since 3.0.0
     */
    public <T extends MochaCompiledFunction> @NotNull T compile(final @NotNull Reader reader, final @NotNull Class<T> interfaceType) {
        List<Expression> parsed;
        try {
            parsed = MolangParser.parseAll(reader);
        } catch (final ParseException e) {
            if (parseExceptionHandler != null) parseExceptionHandler.accept(e);
            parsed = Collections.emptyList();
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to read from given reader", e);
        }
        return compile(parsed, interfaceType);
    }

    /**
     * @see #compile(Reader, Class)
     * @since 3.0.0
     */
    public <T extends MochaCompiledFunction> @NotNull T compile(final @NotNull String code, final @NotNull Class<T> interfaceType) {
        requireNonNull(code, "code");
        try (final StringReader reader = new StringReader(code)) {
            return compile(reader, interfaceType);
        }
    }

    /**
     * Compiles the given expressions into a no-arg {@link MochaFunction}.
     *
     * @since 4.0.4
     */
    public @NotNull MochaFunction compile(final @NotNull List<Expression> expressions) {
        return compile(expressions, MochaFunction.class);
    }

    /**
     * Parses {@code reader} and compiles the resulting AST into a no-arg
     * {@link MochaFunction}.
     *
     * @since 3.0.0
     */
    public @NotNull MochaFunction compile(final @NotNull Reader reader) {
        return compile(reader, MochaFunction.class);
    }

    /**
     * @see #compile(Reader)
     * @since 3.0.0
     */
    public @NotNull MochaFunction compile(final @NotNull String code) {
        return compile(code, MochaFunction.class);
    }
}
