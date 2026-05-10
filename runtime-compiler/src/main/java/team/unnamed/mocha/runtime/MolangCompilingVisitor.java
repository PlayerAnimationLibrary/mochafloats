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
import org.jetbrains.annotations.NotNull;
import team.unnamed.mocha.parser.ast.*;
import team.unnamed.mocha.runtime.binding.Entity;
import team.unnamed.mocha.runtime.binding.JavaFieldBinding;
import team.unnamed.mocha.runtime.binding.JavaFunction;
import team.unnamed.mocha.runtime.binding.JavaObjectBinding;
import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.NumberValue;
import team.unnamed.mocha.runtime.value.ObjectValue;
import team.unnamed.mocha.runtime.value.Value;
import team.unnamed.mocha.util.CaseInsensitiveStringHashMap;
import team.unnamed.mocha.util.ClassFileUtil;

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
import static team.unnamed.mocha.util.ClassFileUtil.*;

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

        if (op == BinaryExpression.Op.ASSIGN) {
            final Expression left = expression.left();
            if (left instanceof AccessExpression) {
                final Expression objectExpr = ((AccessExpression) left).object();
                if (objectExpr instanceof IdentifierExpression) {
                    final String name = ((IdentifierExpression) objectExpr).name();
                    final String property = ((AccessExpression) left).property();

                    if (name.equals("temp") || name.equals("t")) {
                        final CompileVisitResult result = expression.right().visit(this);
                        final int localIndex = localsByName.computeIfAbsent(property, k -> {
                            int index = functionCompileState.maxLocals();
                            if (result.lastPushedType() != null &&
                                    (result.lastPushedType().equals(CD_double) || result.lastPushedType().equals(CD_long))) {
                                functionCompileState.maxLocals(index + 2);
                            } else {
                                functionCompileState.maxLocals(index + 1);
                            }
                            return index;
                        });
                        codeBuilder.fstore(localIndex);
                        return null;
                    }
                }
            }
        }

        final ClassDesc currentExpectedType = expectedType;

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
            case ADD: {
                expectedType = CD_float;
                expression.left().visit(this);
                expression.right().visit(this);
                expectedType = currentExpectedType;
                codeBuilder.fadd();
                return CompileVisitResult.FLOAT;
            }
            case SUB: {
                expectedType = CD_float;
                expression.left().visit(this);
                expression.right().visit(this);
                expectedType = currentExpectedType;
                codeBuilder.fsub();
                return CompileVisitResult.FLOAT;
            }
            case MUL: {
                expectedType = CD_float;
                expression.left().visit(this);
                expression.right().visit(this);
                expectedType = currentExpectedType;
                codeBuilder.fmul();
                return CompileVisitResult.FLOAT;
            }
            case DIV: {
                expectedType = CD_float;
                expression.left().visit(this);   // pushes lhs value to stack
                expression.right().visit(this);  // pushes rhs value to stack
                expectedType = currentExpectedType;
                codeBuilder.fdiv();
                return CompileVisitResult.FLOAT;
            }
            case ARROW:
            case NULL_COALESCE:
            case CONDITIONAL:
                break;
        }
        //@formatter:on
        return null;
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
                    // logical negation
                    codeBuilder.iconst_1();
                    codeBuilder.ixor();
                } else {
                    throw new IllegalStateException("Unsupported type for negation: " + result);
                }
                break;
            }
            default:
                throw new UnsupportedOperationException("Unsupported unary operator: " + expression.op());
        }
        return null;
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
        final Expression objectExpr = expression.object();
        final String property = expression.property();

        if (objectExpr instanceof IdentifierExpression) {
            final String name = ((IdentifierExpression) objectExpr).name();
            if (name.equals("temp") || name.equals("t")) {
                // temps are locals
                final Integer localIndex = localsByName.get(property);
                if (localIndex == null) {
                    codeBuilder.fconst_0();
                } else {
                    codeBuilder.fload(localIndex);
                }
                return CompileVisitResult.FLOAT;
            }
        }

        final Value objectValue = objectExpr.visit(this.scopeResolver);

        if (objectValue instanceof ObjectValue) {
            final ObjectValue actualObjectValue = (ObjectValue) objectValue;
            if (actualObjectValue instanceof JavaObjectBinding) {
                final JavaFieldBinding javaFieldBinding = ((JavaObjectBinding) actualObjectValue).getField(property);
                if (javaFieldBinding == null) {
                    // push zero only
                    codeBuilder.fconst_0();
                } else if (javaFieldBinding.constant()) {
                    // inline const
                    codeBuilder.loadConstant(javaFieldBinding.get().getAsNumber());
                } else {
                    final Field field = javaFieldBinding.field();
                    if (Modifier.isStatic(field.getModifiers())) {
                        codeBuilder.getstatic(
                                classDescOf(field.getDeclaringClass()),
                                field.getName(),
                                classDescOf(field.getType())
                        );
                    }
                }
            }
        }

        return null;
    }

    @Override
    public CompileVisitResult visitCall(final @NotNull CallExpression expression) {
        final ClassDesc targetType = this.expectedType;
        final Expression functionExpr = expression.function();

        final Value functionValue = functionExpr.visit(this.scopeResolver);

        if (!(functionValue instanceof Function<?>)) {
            // not a function, just add 0
            codeBuilder.fconst_0();
            return CompileVisitResult.FLOAT;
        }

        final Function<?> function = (Function<?>) functionValue;

        if (function instanceof JavaFunction<?>) {
            // we can compile to directly call this function (Java Method)
            final JavaFunction<?> javaFunction = (JavaFunction<?>) function;
            final Method nativeMethod = javaFunction.method();
            final Parameter[] parameters = nativeMethod.getParameters();
            final List<Expression> arguments = expression.arguments();

            final ClassDesc[] ctParameters = new ClassDesc[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                ctParameters[i] = classDescOf(parameters[i].getType());
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
                if (targetType != null && !returnTypeDesc.equals(targetType)) {
                    ClassFileUtil.addCast(codeBuilder, returnTypeDesc, targetType);
                    return new CompileVisitResult(targetType);
                }
                return new CompileVisitResult(returnTypeDesc);
            }
        } else {
            throw new UnsupportedOperationException("Not supporting non-Java functions yet");
        }
    }

    @Override
    public CompileVisitResult visit(final @NotNull Expression expression) {
        throw new UnsupportedOperationException("Unsupported expression type: " + expression);
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
