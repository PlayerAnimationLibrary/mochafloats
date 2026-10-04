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
import org.jetbrains.annotations.Nullable;
import org.redlance.mocha.parser.ast.*;
import org.redlance.mocha.runtime.binding.Binding;
import org.redlance.mocha.runtime.binding.Entity;
import org.redlance.mocha.runtime.binding.JavaFieldBinding;
import org.redlance.mocha.runtime.binding.JavaFunction;
import org.redlance.mocha.runtime.binding.JavaObjectBinding;
import org.redlance.mocha.runtime.binding.Lazy;
import org.redlance.mocha.runtime.value.Function;
import org.redlance.mocha.runtime.value.NumberValue;
import org.redlance.mocha.runtime.value.ObjectValue;
import org.redlance.mocha.runtime.value.Value;
import org.redlance.mocha.runtime.util.CaseInsensitiveStringHashMap;
import org.redlance.mocha.runtime.util.ClassFileUtil;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.lang.constant.ConstantDescs.*;
import static org.redlance.mocha.runtime.util.ClassFileUtil.*;

@J2ObjCIncompatible
final class MolangCompilingVisitor implements ExpressionVisitor<CompileVisitResult> {

    private final CodeBuilder codeBuilder;
    private final Method method;

    private final FunctionCompileState functionCompileState;
    private final Map<String, Object> requirements;
    private final Map<String, Integer> argumentParameterIndexes;

    private final Map<String, Integer> localsByName = new CaseInsensitiveStringHashMap<>();

    private final ExpressionVisitor<Value> scopeResolver;

    /**
     * The method return type
     */
    private final ClassDesc methodReturnType;
    /**
     * The type that the current visitor method is expecting
     * to be pushed to the stack.
     */
    private ClassDesc expectedType;

    MolangCompilingVisitor(final @NotNull FunctionCompileState compileState) {
        this.functionCompileState = compileState;
        this.codeBuilder = compileState.codeBuilder();
        this.method = compileState.method();
        this.requirements = compileState.requirements();
        this.argumentParameterIndexes = compileState.argumentParameterIndexes();

        this.methodReturnType = classDescOf(method.getReturnType());
        expectedType = methodReturnType;

        this.scopeResolver = new ExpressionVisitor<>() {
            @Override
            public @NotNull Value visitIdentifier(final @NotNull IdentifierExpression expression) {
                return functionCompileState.scope().get(expression.name());
            }

            @Override
            public @NotNull Value visitAccess(final @NotNull AccessExpression expression) {
                final Value object = expression.object().visit(this);
                if (object instanceof ObjectValue) {
                    return ((ObjectValue) object).get(expression.property());
                } else {
                    return NumberValue.zero();
                }
            }

            @Override
            public @NotNull Value visit(final @NotNull Expression expression) {
                return NumberValue.zero();
            }
        };
    }

    @Override
    public CompileVisitResult visitBinary(final @NotNull BinaryExpression expression) {
        final BinaryExpression.Op op = expression.op();

        final ClassDesc currentExpectedType = expectedType;

        if (op == BinaryExpression.Op.ASSIGN) {
            if (expression.left() instanceof AccessExpression access && isTemp(access)) {
                // temp variables are float locals, so the value must be a float
                // whatever the method returns, they are read back with fload
                expectedType = CD_float;
                expression.right().visit(this);
                expectedType = currentExpectedType;
                final int localIndex = localsByName.computeIfAbsent(access.property(), k -> {
                    final int index = functionCompileState.maxLocals();
                    functionCompileState.maxLocals(index + 1);
                    return index;
                });
                // keep the assigned value on the stack, the interpreter evaluates an assignment to it too
                codeBuilder.dup();
                codeBuilder.fstore(localIndex);
                return castTo(CD_float, currentExpectedType);
            }
            throw unsupported("assignments to anything but temp variables", expression);
        }

        //@formatter:off
        switch (op) {
            case AND: {
                expectedType = CD_boolean;
                expression.left().visit(this);
                Label falseLabel = codeBuilder.newLabel();
                codeBuilder.ifeq(falseLabel);
                expression.right().visit(this);
                Label trueEnd = codeBuilder.newLabel();
                codeBuilder.ifeq(falseLabel);
                addConst1(currentExpectedType);
                codeBuilder.goto_(trueEnd);
                codeBuilder.labelBinding(falseLabel);
                addConst0(currentExpectedType);
                codeBuilder.labelBinding(trueEnd);
                expectedType = currentExpectedType;
                return new CompileVisitResult(currentExpectedType);
            }
            case OR: {
                expectedType = CD_boolean;
                expression.left().visit(this);
                Label trueLabel = codeBuilder.newLabel();
                codeBuilder.ifne(trueLabel);
                expression.right().visit(this);
                Label falseLabel = codeBuilder.newLabel();
                codeBuilder.ifeq(falseLabel);
                codeBuilder.labelBinding(trueLabel);
                addConst1(currentExpectedType);
                Label end = codeBuilder.newLabel();
                codeBuilder.goto_(end);
                codeBuilder.labelBinding(falseLabel);
                addConst0(currentExpectedType);
                codeBuilder.labelBinding(end);
                expectedType = currentExpectedType;
                return new CompileVisitResult(currentExpectedType);
            }
            case EQ:
            case NEQ:
            case LT:
            case LTE:
            case GT:
            case GTE: {
                expectedType = CD_float;
                expression.left().visit(this);   // pushes lhs value to stack
                expression.right().visit(this);  // pushes rhs value to stack
                expectedType = currentExpectedType;

                codeBuilder.fcmpl();

                Label trueLabel = codeBuilder.newLabel();
                Label end = codeBuilder.newLabel();

                switch (op) {
                    case LT -> codeBuilder.iflt(trueLabel);
                    case LTE -> codeBuilder.ifle(trueLabel);
                    case GT -> codeBuilder.ifgt(trueLabel);
                    case GTE -> codeBuilder.ifge(trueLabel);
                    case EQ -> codeBuilder.ifeq(trueLabel);
                    case NEQ -> codeBuilder.ifne(trueLabel);
                    default -> throw new IllegalStateException();
                }
                addConst0(expectedType == null ? CD_boolean : expectedType);
                codeBuilder.goto_(end);
                codeBuilder.labelBinding(trueLabel);
                addConst1(expectedType == null ? CD_boolean : expectedType);
                codeBuilder.labelBinding(end);
                return new CompileVisitResult(expectedType == null ? CD_boolean : expectedType);
            }
            case ADD:
            case SUB:
            case MUL:
            case DIV: {
                expectedType = CD_float;
                expression.left().visit(this);   // pushes lhs value to stack
                expression.right().visit(this);  // pushes rhs value to stack
                expectedType = currentExpectedType;
                switch (op) {
                    case ADD -> codeBuilder.fadd();
                    case SUB -> codeBuilder.fsub();
                    case MUL -> codeBuilder.fmul();
                    default -> addDivision();
                }
                // like the interpreter, which never produces NaN or infinities
                addNormalize(CD_float);
                return castTo(CD_float, currentExpectedType);
            }
            case CONDITIONAL:
                // "a ? b" is "a ? b : 0"
                return new TernaryConditionalExpression(expression.left(), expression.right(), FloatExpression.ZERO).visit(this);
            case ARROW:
                throw unsupported("the arrow operator (->)", expression);
            case NULL_COALESCE:
                throw unsupported("the null coalescing operator (??)", expression);
            default:
                throw new IllegalStateException("Unknown binary operator: " + op);
        }
        //@formatter:on
    }

    // MoLang divides by zero as zero, the stack holds the dividend and the divisor
    private void addDivision() {
        final Label divide = codeBuilder.newLabel();
        final Label end = codeBuilder.newLabel();
        codeBuilder.dup();
        codeBuilder.fconst_0();
        codeBuilder.fcmpl();
        codeBuilder.ifne(divide);
        codeBuilder.pop2();
        codeBuilder.fconst_0();
        codeBuilder.goto_(end);
        codeBuilder.labelBinding(divide);
        codeBuilder.fdiv();
        codeBuilder.labelBinding(end);
    }

    // replaces NaN and infinities on the stack with zero, like NumberValue does
    private void addNormalize(final @NotNull ClassDesc type) {
        if (type.equals(CD_float) || type.equals(CD_double)) {
            codeBuilder.invokestatic(classDescOf(NumberValue.class), "normalize", MethodTypeDesc.of(type, type));
        }
    }

    // converts the value on the stack to the type the caller expects, if it expects one
    private @NotNull CompileVisitResult castTo(final @NotNull ClassDesc from, final @Nullable ClassDesc to) {
        if (to == null || to.equals(from)) {
            return new CompileVisitResult(from);
        }
        if (to.equals(CD_void)) {
            if (from.equals(CD_double) || from.equals(CD_long)) {
                codeBuilder.pop2();
            } else if (!from.equals(CD_void)) {
                codeBuilder.pop();
            }
            return CompileVisitResult.VOID;
        }
        ClassFileUtil.addCast(codeBuilder, from, to);
        return new CompileVisitResult(to);
    }

    private static boolean isTemp(final @NotNull AccessExpression access) {
        return access.object() instanceof IdentifierExpression identifier
                && (identifier.name().equals("temp") || identifier.name().equals("t"));
    }

    private static @NotNull UnsupportedOperationException unsupported(final @NotNull String what, final @NotNull Expression expression) {
        return new UnsupportedOperationException("The compiler doesn't support " + what + " (in '" + expression
                + "'), evaluate this expression with MolangInterpreter instead");
    }

    public void endVisit() {
        ClassFileUtil.addReturn(codeBuilder, methodReturnType);
    }

    @Override
    public @NotNull CompileVisitResult visitFloat(final @NotNull FloatExpression expression) {
        final float value = expression.value();
        if (expectedType != null && expectedType.equals(CD_void)) {
            // nothing!
            return CompileVisitResult.VOID;
        } else if (expectedType == null || expectedType.equals(CD_float)) {
            // expects a float, happy!
            codeBuilder.loadConstant(value);
            return CompileVisitResult.FLOAT;
        } else if (expectedType.equals(CD_boolean)) {
            // expects a boolean, push boolean
            if (value != 0.0D) {
                codeBuilder.iconst_1();
            } else {
                codeBuilder.iconst_0();
            }
            return CompileVisitResult.BOOLEAN;
        } else if (expectedType.equals(CD_int)) {
            // expects an int, push int
            codeBuilder.loadConstant((int) value);
            return CompileVisitResult.INT;
        } else if (expectedType.equals(CD_long)) {
            // expects a long, push long
            codeBuilder.loadConstant((long) value);
            return CompileVisitResult.LONG;
        } else if (expectedType.equals(CD_double)) {
            // expects a double, push double
            codeBuilder.loadConstant((double) value);
            return CompileVisitResult.DOUBLE;
        } else {
            System.err.println("[warning] expected type " + expectedType + " has no possible cast from float (" + expression + ")");
            // evaluate to zero
            addConstZero(codeBuilder, expectedType);
            return new CompileVisitResult(expectedType);
        }
    }

    @Override
    public @NotNull CompileVisitResult visitString(final @NotNull StringExpression expression) {
        if (expectedType != null && expectedType.equals(CD_void)) {
            // nothing!
            return CompileVisitResult.VOID;
        } else if (expectedType == null || expectedType.equals(CD_String)) {
            // expected a string, happy
            codeBuilder.loadConstant(expression.value());
            return CompileVisitResult.STRING;
        } else {
            // evaluate to zero
            addConstZero(codeBuilder, expectedType);
            return new CompileVisitResult(expectedType);
        }
    }

    @Override
    public @NotNull CompileVisitResult visitUnary(final @NotNull UnaryExpression expression) {
        switch (expression.op()) {
            case RETURN: {
                expectedType = methodReturnType;
                expression.expression().visit(this);
                expectedType = null;
                ClassFileUtil.addReturn(codeBuilder, methodReturnType);
                return new CompileVisitResult(methodReturnType, true);
            }
            case LOGICAL_NEGATION: {
                if (expectedType != null && expectedType.equals(CD_void)) {
                    // void,
                    // we must evaluate in case of weird expressions
                    // like: !query.print('hello')
                    // won't push anything since expectedType is set to voidType
                    expression.expression().visit(this);
                    return CompileVisitResult.VOID;
                }

                final ClassDesc currentExpectedType = expectedType;

                if (currentExpectedType != null && !currentExpectedType.isPrimitive()) {
                    // an unknown Object type, evaluate without pushing anything
                    // and then just push null in the stack
                    expectedType = CD_void; // set to void so that doesn't push anything
                    expression.expression().visit(this);
                    expectedType = currentExpectedType;
                    addConstZero(codeBuilder, currentExpectedType);
                    return new CompileVisitResult(currentExpectedType);
                }

                // todo: wrap primitives to their wrapper class if needed

                expectedType = CD_boolean;
                expression.expression().visit(this); // push boolean value to stack
                expectedType = currentExpectedType;

                if (currentExpectedType != null && currentExpectedType.equals(CD_boolean)) {
                    // For boolean, leave value on stack and branch
                    // We need: if (value != 0) push 0 else push 1
                    Label pushZero = codeBuilder.newLabel();
                    Label end = codeBuilder.newLabel();
                    codeBuilder.ifne(pushZero);
                    codeBuilder.iconst_1();
                    codeBuilder.goto_(end);
                    codeBuilder.labelBinding(pushZero);
                    codeBuilder.iconst_0();
                    codeBuilder.labelBinding(end);
                    return CompileVisitResult.BOOLEAN;
                }

                Label pushConst0 = codeBuilder.newLabel();
                Label end = codeBuilder.newLabel();
                codeBuilder.ifne(pushConst0);
                addConst1(currentExpectedType);
                codeBuilder.goto_(end);
                codeBuilder.labelBinding(pushConst0);
                addConst0(currentExpectedType);
                codeBuilder.labelBinding(end);
                return new CompileVisitResult(currentExpectedType);
            }
            case ARITHMETICAL_NEGATION: {
                final CompileVisitResult result = expression.expression().visit(this); // push value to stack
                if (result.is(CD_double)) {
                    codeBuilder.dneg();
                } else if (result.is(CD_long)) {
                    codeBuilder.lneg();
                } else if (result.is(CD_float)) {
                    codeBuilder.fneg();
                } else if (result.is(CD_int)) {
                    codeBuilder.ineg();
                } else if (result.is(CD_boolean)) {
                    // -x is non-zero only if x is non-zero,
                    // so the boolean value stays the same
                } else {
                    throw new IllegalStateException("Unsupported type for negation: " + result);
                }
                // the negated value keeps its type
                return result;
            }
            default:
                throw new UnsupportedOperationException("Unsupported unary operator: " + expression.op());
        }
    }

    @Override
    public @NotNull CompileVisitResult visitTernaryConditional(final @NotNull TernaryConditionalExpression expression) {
        final Expression trueExpr = expression.trueExpression();
        final Expression falseExpr = expression.falseExpression();

        final ClassDesc currentExpectedType = expectedType;
        expectedType = CD_boolean;
        final CompileVisitResult conditionRes = expression.condition().visit(this);
        expectedType = currentExpectedType;

        if (conditionRes != null && conditionRes.lastPushedType() != null
                && !conditionRes.is(CD_boolean) && !conditionRes.is(CD_int)) {
            addConstZero(codeBuilder, conditionRes.lastPushedType());
            if (conditionRes.is(CD_double)) {
                codeBuilder.dcmpl();
            } else if (conditionRes.is(CD_float)) {
                codeBuilder.fcmpl();
            } else if (conditionRes.is(CD_long)) {
                codeBuilder.lcmp();
            } else {
                throw new IllegalStateException("Unsupported type for comparison: " + conditionRes);
            }
        }

        Label falseLabel = codeBuilder.newLabel();
        Label end = codeBuilder.newLabel();
        codeBuilder.ifeq(falseLabel);
        trueExpr.visit(this);
        codeBuilder.goto_(end);
        codeBuilder.labelBinding(falseLabel);
        falseExpr.visit(this);
        codeBuilder.labelBinding(end);
        return new CompileVisitResult(currentExpectedType);
    }

    @Override
    public CompileVisitResult visitIdentifier(final @NotNull IdentifierExpression expression) {
        final String name = expression.name();
        final Integer paramIndex = argumentParameterIndexes.get(name);
        if (paramIndex == null) {
            throw new IllegalStateException("Unknown variable: " + name);
        }

        final Parameter[] parameters = method.getParameters();
        final Parameter parameter = parameters[paramIndex];
        int loadIndex = 1;
        for (int i = 0; i < paramIndex; i++) {
            final Parameter param = parameters[i];
            final Class<?> paramType = param.getType();
            if (paramType.equals(double.class) || paramType.equals(long.class)) {
                loadIndex += 2;
            } else {
                loadIndex += 1;
            }
        }

        final ClassDesc parameterType = classDescOf(parameter.getType());

        ClassFileUtil.addLoad(codeBuilder, loadIndex, parameterType);

        if (expectedType == null) {
            // we are free to use anything, no need to cast
            return new CompileVisitResult(parameterType);
        }

        // convert to the expected type
        ClassFileUtil.addCast(codeBuilder, parameterType, expectedType);
        return new CompileVisitResult(expectedType);
    }

    @Override
    public CompileVisitResult visitAccess(final @NotNull AccessExpression expression) {
        final String property = expression.property();

        if (isTemp(expression)) {
            // temps are float locals, an unassigned one reads as zero
            final Integer localIndex = localsByName.get(property);
            if (localIndex == null) {
                codeBuilder.fconst_0();
            } else {
                codeBuilder.fload(localIndex);
            }
            return castTo(CD_float, expectedType);
        }

        final Value objectValue = expression.object().visit(this.scopeResolver);

        if (objectValue instanceof JavaObjectBinding javaObjectBinding) {
            final JavaFieldBinding javaFieldBinding = javaObjectBinding.getField(property);
            if (javaFieldBinding == null) {
                // not a field, reads as zero like in the interpreter
                codeBuilder.fconst_0();
                return castTo(CD_float, expectedType);
            } else if (javaFieldBinding.constant()) {
                // inline const
                codeBuilder.loadConstant(javaFieldBinding.get().getAsNumber());
                return castTo(CD_float, expectedType);
            }

            final Field field = javaFieldBinding.field();
            if (field == null || !Modifier.isStatic(field.getModifiers())) {
                throw unsupported("non-static fields", expression);
            }
            final ClassDesc fieldType = classDescOf(field.getType());
            codeBuilder.getstatic(classDescOf(field.getDeclaringClass()), field.getName(), fieldType);
            return castTo(fieldType, expectedType);
        } else if (objectValue instanceof ObjectValue) {
            throw unsupported("reading properties of " + expression.object()
                    + ", only temp variables and @Binding static fields can be read", expression);
        }

        // unknown names evaluate to zero
        codeBuilder.fconst_0();
        return castTo(CD_float, expectedType);
    }

    @Override
    public CompileVisitResult visitCall(final @NotNull CallExpression expression) {
        final ClassDesc targetType = this.expectedType;
        final Expression functionExpr = expression.function();

        if (functionExpr instanceof IdentifierExpression identifier
                && (identifier.name().equals("loop") || identifier.name().equals("for_each"))) {
            throw unsupported(identifier.name(), expression);
        }

        final Value functionValue = functionExpr.visit(this.scopeResolver);

        if (!(functionValue instanceof Function<?>)) {
            // not a function, calling it evaluates to zero like in the interpreter
            codeBuilder.fconst_0();
            return castTo(CD_float, targetType);
        }

        final Function<?> function = (Function<?>) functionValue;

        if (function instanceof JavaFunction<?> javaFunction && javaFunction.method() != null) {
            // we can compile to directly call this function (Java Method)
            final Method nativeMethod = javaFunction.method();
            final Parameter[] parameters = nativeMethod.getParameters();
            final List<Expression> arguments = expression.arguments();

            final ClassDesc[] ctParameters = new ClassDesc[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                final Class<?> parameterType = parameters[i].getType();
                if (parameterType == ExecutionContext.class || parameterType == Lazy.class || parameters[i].isVarArgs()) {
                    throw unsupported("calling " + nativeMethod.getName() + ", which takes an ExecutionContext, a Lazy or varargs", expression);
                }
                ctParameters[i] = classDescOf(parameterType);
            }

            final boolean isStatic = Modifier.isStatic(nativeMethod.getModifiers());

            // load instance
            if (!isStatic) {
                final Object object = javaFunction.object();
                final String fieldName = object.getClass().getSimpleName().toLowerCase(Locale.ROOT) + Integer.toHexString(object.hashCode());
                requirements.put(fieldName, object);

                final ClassDesc requirementType = classDescOf(object.getClass());

                // we must load object
                codeBuilder.aload(0);
                codeBuilder.getfield(functionCompileState.classDesc(), fieldName, requirementType);
            }

            // load arguments
            final Iterator<Expression> it = arguments.iterator();
            for (int i = 0; i < parameters.length; i++) {
                final Parameter parameter = parameters[i];
                final ClassDesc paramType = ctParameters[i];

                if (parameter.isAnnotationPresent(Entity.class)) {
                    Object entity = functionCompileState.compiler().entity();
                    if (entity == null || !parameter.getType().isInstance(entity)) {
                        // load null
                        addConstZero(codeBuilder, paramType);
                    } else {
                        // add entity requirement
                        requirements.put("__entity__", entity);

                        // load entity requirement (field)
                        codeBuilder.aload(0); // load this
                        codeBuilder.getfield(
                                functionCompileState.classDesc(),
                                "__entity__",
                                paramType
                        );
                    }
                    continue;
                }

                if (!it.hasNext()) {
                    addConstZero(codeBuilder, paramType);
                    continue;
                }

                // Set the expected type, then load
                this.expectedType = paramType;
                it.next().visit(this);
            }

            // restore the expected type, otherwise the last parameter type
            // leaks into the rest of the expression (e.g. "q.f('a') - 1")
            this.expectedType = targetType;

            final ClassDesc declaringClassDesc = classDescOf(nativeMethod.getDeclaringClass());
            final ClassDesc returnTypeDesc = classDescOf(nativeMethod.getReturnType());
            final MethodTypeDesc methodTypeDesc = MethodTypeDesc.of(returnTypeDesc, ctParameters);

            if (isStatic) {
                // invoke static
                codeBuilder.invokestatic(declaringClassDesc, nativeMethod.getName(), methodTypeDesc);
            } else {
                codeBuilder.invokevirtual(declaringClassDesc, nativeMethod.getName(), methodTypeDesc);
            }

            if (nativeMethod.getReturnType() == void.class) {
                if (targetType != null && !targetType.equals(CD_void)) {
                    addConstZero(codeBuilder, targetType);
                    return new CompileVisitResult(targetType);
                }
                return CompileVisitResult.VOID;
            } else {
                // the interpreter replaces NaN and infinities returned by Java methods with zero
                final Binding binding = nativeMethod.getDeclaredAnnotation(Binding.class);
                if (binding == null || !binding.skipChecking()) {
                    addNormalize(returnTypeDesc);
                }
                return castTo(returnTypeDesc, targetType);
            }
        } else {
            throw unsupported("functions that aren't Java methods bound with @Binding", expression);
        }
    }

    @Override
    public CompileVisitResult visit(final @NotNull Expression expression) {
        final String what = switch (expression) {
            case ArrayAccessExpression ignored -> "array access";
            case ExecutionScopeExpression ignored -> "blocks ({ ... })";
            case StatementExpression ignored -> "break and continue";
            default -> expression.getClass().getSimpleName();
        };
        throw unsupported(what, expression);
    }

    private void addConst0(final ClassDesc type) {
        if (type == null || type.equals(CD_float)) {
            codeBuilder.fconst_0();
        } else if (type.equals(CD_double)) {
            codeBuilder.dconst_0();
        } else if (type.equals(CD_long)) {
            codeBuilder.lconst_0();
        } else {
            codeBuilder.iconst_0();
        }
    }

    private void addConst1(final ClassDesc type) {
        if (type == null || type.equals(CD_float)) {
            codeBuilder.fconst_1();
        } else if (type.equals(CD_double)) {
            codeBuilder.dconst_1();
        } else if (type.equals(CD_long)) {
            codeBuilder.lconst_1();
        } else {
            codeBuilder.iconst_1();
        }
    }
}
