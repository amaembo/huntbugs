/*
 * Copyright 2016 HuntBugs contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package one.util.huntbugs.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import one.util.huntbugs.flow.ValuesFlow.ThrowTargets;
import one.util.huntbugs.util.Maps;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

class Frame {
    static final TypeDefinition runtimeException;
    static final TypeDefinition nullPointerException;
    static final TypeDefinition arrayIndexOutOfBoundsException;
    static final TypeDefinition arrayStoreException;
    static final TypeDefinition outOfMemoryError;
    static final TypeDefinition linkageError;
    static final TypeDefinition error;
    static final TypeDefinition exception;
    
    static {
        MetadataSystem ms = MetadataSystem.instance();
        exception = ms.lookupType("java/lang/Exception").resolve();
        runtimeException = ms.lookupType("java/lang/RuntimeException").resolve();
        nullPointerException = ms.lookupType("java/lang/NullPointerException").resolve();
        arrayIndexOutOfBoundsException = ms.lookupType("java/lang/ArrayIndexOutOfBoundsException").resolve();
        arrayStoreException = ms.lookupType("java/lang/ArrayStoreException").resolve();
        outOfMemoryError = ms.lookupType("java/lang/OutOfMemoryError").resolve();
        linkageError = ms.lookupType("java/lang/LinkageError").resolve();
        error = ms.lookupType("java/lang/Error").resolve();
    }
    
    private final Map<Variable, Expression> sources;
    private final FrameContext fc;
    final Map<MemberInfo, Expression> fieldValues;
    final Map<ParameterDefinition, Expression> initial;
    static final AstCode PHI_TYPE = AstCode.Wrap;
    static final AstCode UPDATE_TYPE = AstCode.Nop;
    static final Object UNKNOWN_VALUE = new Object();
    
    Expression get(Variable var) {
        Expression expr = sources.get(var);
        if(expr != null)
            return expr;
        return initial.get(var.getOriginalParameter());
    }

    Frame(FrameContext fc) {
        this.sources = new IdentityHashMap<>();
        this.fieldValues = fc.getInitialFields();
        this.fc = fc;
        this.initial = new IdentityHashMap<>();
        for(ParameterDefinition pd : fc.md.getParameters()) {
            putInitial(pd);
        }
        ParameterDefinition thisParam = fc.md.getBody().getThisParameter();
        if(thisParam != null) {
            putInitial(thisParam);
        }
    }

    private void putInitial(ParameterDefinition thisParam) {
        Expression pde = new Expression(AstCode.Load, thisParam, 0);
        pde.setExpectedType(thisParam.getParameterType());
        pde.setInferredType(thisParam.getParameterType());
        initial.put(thisParam, pde);
    }

    Frame processChildren(Expression expr, ThrowTargets targets) {
        Frame result = this;
        for (Expression child : expr.getArguments()) {
            result = result.process(child, targets);
        }
        return result;
    }

    Frame process(Expression expr, ThrowTargets targets) {
        if (expr.getCode() == AstCode.TernaryOp) {
            Expression cond = expr.getArguments().get(0);
            Expression left = expr.getArguments().get(1);
            Expression right = expr.getArguments().get(2);
            Frame target = process(cond, targets);
            Frame leftFrame = target.process(left, targets);
            Frame rightFrame = target.process(right, targets);
            return leftFrame.merge(rightFrame);
        }
        Frame target = processChildren(expr, targets);
        switch (expr.getCode()) {
        case Store: {
            Variable var = ((Variable) expr.getOperand());
            Expression arg = expr.getArguments().get(0);
            Expression source = ValuesFlow.getSource(arg);
            Object val = arg.getUserData(ValuesFlow.VALUE_KEY);
            storeValue(expr, val);
            return target.replace(var, source);
        }
        case LdC: {
            storeValue(expr, expr.getOperand());
            return target;
        }
        case ArrayLength:
            targets.merge(nullPointerException, target);
            storeValue(expr, getArrayLength(expr.getArguments().get(0)));
            return target;
        case CmpEq:
            return processCmpEq(expr, target);
        case CmpNe:
            return processCmpNe(expr, target);
        case CmpLt:
            return processCmpLt(expr, target);
        case CmpLe:
            return processCmpLe(expr, target);
        case CmpGt:
            return processCmpGt(expr, target);
        case CmpGe:
            return processCmpGe(expr, target);
        case Add:
            return processAdd(expr, target);
        case Sub:
            return processSub(expr, target);
        case Mul:
            return processMul(expr, target);
        case Div:
            return processDiv(expr, target);
        case Rem:
            return processRem(expr, target);
        case Xor: {
            switch (getType(expr)) {
            case Integer:
                return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a ^ b);
            case Long:
                return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a ^ b);
            default:
            }
            return target;
        }
        case Or: {
            switch (getType(expr)) {
            case Integer:
                return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a | b);
            case Long:
                return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a | b);
            default:
            }
            return target;
        }
        case And: {
            switch (getType(expr)) {
            case Integer:
                return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a & b);
            case Long:
                return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a & b);
            default:
            }
            return target;
        }
        case Shl: {
            switch (getType(expr)) {
            case Integer:
                return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a << b);
            case Long:
                return target.processBinaryOp(expr, Long.class, Integer.class, (a, b) -> a << b);
            default:
            }
            return target;
        }
        case Shr: {
            switch (getType(expr)) {
            case Integer:
                return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a >> b);
            case Long:
                return target.processBinaryOp(expr, Long.class, Integer.class, (a, b) -> a >> b);
            default:
            }
            return target;
        }
        case UShr: {
            switch (getType(expr)) {
            case Integer:
                return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a >>> b);
            case Long:
                return target.processBinaryOp(expr, Long.class, Integer.class, (a, b) -> a >>> b);
            default:
            }
            return target;
        }
        case I2L:
            return target.processUnaryOp(expr, Integer.class, i -> (long) i);
        case I2B:
            return target.processUnaryOp(expr, Integer.class, i -> (int) (byte) (int) i);
        case I2C:
            return target.processUnaryOp(expr, Integer.class, i -> (int) (char) (int) i);
        case I2S:
            return target.processUnaryOp(expr, Integer.class, i -> (int) (short) (int) i);
        case I2D:
            return target.processUnaryOp(expr, Integer.class, i -> (double) i);
        case I2F:
            return target.processUnaryOp(expr, Integer.class, i -> (float) i);
        case L2I:
            return target.processUnaryOp(expr, Long.class, l -> (int) (long) l);
        case L2D:
            return target.processUnaryOp(expr, Long.class, l -> (double) l);
        case L2F:
            return target.processUnaryOp(expr, Long.class, l -> (float) l);
        case F2L:
            return target.processUnaryOp(expr, Float.class, l -> (long) (float) l);
        case F2I:
            return target.processUnaryOp(expr, Float.class, l -> (int) (float) l);
        case F2D:
            return target.processUnaryOp(expr, Float.class, l -> (double) l);
        case D2F:
            return target.processUnaryOp(expr, Double.class, l -> (float) (double) l);
        case D2I:
            return target.processUnaryOp(expr, Double.class, l -> (int) (double) l);
        case D2L:
            return target.processUnaryOp(expr, Double.class, l -> (long) (double) l);
        case Neg:
            return processNeg(expr, target);
        case Load: {
            Variable var = ((Variable) expr.getOperand());
            // TODO: support transferring variables from outer method to lambda
            Expression source = get(var);
            if (source != null) {
                expr.putUserData(ValuesFlow.SOURCE_KEY, source);
                link(expr, source);
                Object val = source.getUserData(ValuesFlow.VALUE_KEY);
                storeValue(expr, val);
            }
            return this;
        }
        case Inc:
            if (expr.getOperand() instanceof Variable) {
                Variable var = ((Variable) expr.getOperand());
                Expression source = get(var);
                target = target.replace(var, expr);
                if(source == null)
                    return target;
                link(expr, source);
                Object val = source.getUserData(ValuesFlow.VALUE_KEY);
                if (val == UNKNOWN_VALUE)
                    expr.putUserData(ValuesFlow.VALUE_KEY, UNKNOWN_VALUE);
                else if (val instanceof Integer)
                    return target.processUnaryOp(expr, Integer.class, inc -> ((int) val) + inc);
                else if (val instanceof Long)
                    return target.processUnaryOp(expr, Long.class, inc -> ((long) val) + inc);
            }
            return target;
        case PostIncrement:
        case PreIncrement: {
            Expression arg = expr.getArguments().get(0);
            if (arg.getOperand() instanceof Variable) {
                Variable var = ((Variable) arg.getOperand());
                Expression source = get(var);
                link(expr, source);
                // TODO: pass values
                return target.replace(var, expr);
            }
            return target;
        }
        case Bind:
            targets.merge(error, target);
            targets.merge(runtimeException, target);
            return target;
        case InitObject:
        case InvokeInterface:
        case InvokeSpecial:
        case InvokeStatic:
        case InvokeVirtual: {
            MethodReference mr = (MethodReference) expr.getOperand();
            if(!targets.isEmpty()) {
                targets.merge(error, target);
                targets.merge(runtimeException, target);
                MethodDefinition md = mr.resolve();
                if(md != null) {
                    for(TypeReference thrownType : md.getThrownTypes()) {
                        targets.merge(thrownType, target);
                    }
                } else {
                    targets.merge(exception, target);
                }
            }
            target.processKnownMethods(expr, mr);
            if (Methods.isSideEffectFree(mr))
                return target;
            return target.replaceAll(src -> src.getCode() == AstCode.GetField || src.getCode() == AstCode.GetStatic
                || src.getCode() == AstCode.LoadElement ? fc.makeUpdatedNode(src) : src).deleteFields();
        }
        case LoadElement:
            targets.merge(arrayIndexOutOfBoundsException, target);
            targets.merge(nullPointerException, target);
            return target;
        case StoreElement: {
            targets.merge(arrayIndexOutOfBoundsException, target);
            targets.merge(arrayStoreException, target);
            targets.merge(nullPointerException, target);
            return target.replaceAll(src -> src.getCode() == AstCode.LoadElement ? fc.makeUpdatedNode(src) : src);
        }
        case __New:
        case NewArray:
        case InitArray:
        case MultiANewArray:
            targets.merge(outOfMemoryError, target);
            return target;
        case GetStatic: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            if(!fr.getDeclaringType().isEquivalentTo(fc.md.getDeclaringType()))
                targets.merge(linkageError, target);
            FieldDefinition fd = fr.resolve();
            if (fd != null && fd.isEnumConstant()) {
                storeValue(expr, new EnumConstant(fd.getDeclaringType().getInternalName(), fd.getName()));
            } else {
                Expression source = fieldValues.get(new MemberInfo(fr));
                if (source != null) {
                    expr.putUserData(ValuesFlow.SOURCE_KEY, source);
                    link(expr, source);
                    Object val = source.getUserData(ValuesFlow.VALUE_KEY);
                    storeValue(expr, val);
                }
            }
            return target;
        }
        case GetField: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            if(fc.isThis(Nodes.getChild(expr, 0))) {
                Expression source = fieldValues.get(new MemberInfo(fr));
                if (source != null) {
                    expr.putUserData(ValuesFlow.SOURCE_KEY, source);
                    link(expr, source);
                    Object val = source.getUserData(ValuesFlow.VALUE_KEY);
                    storeValue(expr, val);
                }
            } else {
                targets.merge(nullPointerException, target);
                targets.merge(linkageError, target);
            }
            return target;
        }
        case PutField: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            if(fc.isThis(Nodes.getChild(expr, 0))) {
                target = target.replaceField(fr, Nodes.getChild(expr, 1));
            } else {
                targets.merge(nullPointerException, target);
                targets.merge(linkageError, target);
            }
            return target.replaceAll(src -> src.getCode() == AstCode.GetField
                && fr.isEquivalentTo((FieldReference) src.getOperand()) ? fc.makeUpdatedNode(src) : src);
        }
        case PutStatic: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            if(!fr.getDeclaringType().isEquivalentTo(fc.md.getDeclaringType()))
                targets.merge(linkageError, target);
            return target.replaceField(fr, Nodes.getChild(expr, 0)).replaceAll(src -> src
                    .getCode() == AstCode.GetStatic && fr.isEquivalentTo((FieldReference) src.getOperand())
                            ? fc.makeUpdatedNode(src) : src);
        }
        default:
            return target;
        }
    }

    private Integer getArrayLength(Expression expression) {
        return ValuesFlow.reduce(expression, e -> {
            switch(e.getCode()) {
            case InvokeVirtual: {
                MethodReference mr = (MethodReference) e.getOperand();
                if (mr.getName().equals("clone") && mr.getErasedSignature().startsWith("()")) {
                    return getArrayLength(Nodes.getChild(e, 0));
                }
                return null;
            }
            case CheckCast:
                return getArrayLength(Nodes.getChild(e, 0));
            case InitArray:
                return e.getArguments().size();
            case NewArray:
                Object constant = ValuesFlow.getValue(e.getArguments().get(0));
                if(constant instanceof Integer)
                    return (Integer)constant;
                return null;
            default:
                return null;
            }
        }, (a, b) -> Objects.equals(a, b) ? a : null, Objects::isNull);
    }

    private static void link(Expression target, Expression source) {
        if (source.getCode() == PHI_TYPE) {
            source.getArguments().forEach(arg -> link(target, arg));
            return;
        }
        Set<Expression> set = source.getUserData(ValuesFlow.BACK_LINKS_KEY);
        if (set == null) {
            set = new HashSet<>();
            source.putUserData(ValuesFlow.BACK_LINKS_KEY, set);
        } else if (!(set instanceof HashSet)) {
            set = new HashSet<>(set);
            source.putUserData(ValuesFlow.BACK_LINKS_KEY, set);
        }
        set.add(target);
    }

    private static void storeValue(Expression expr, Object val) {
        Object curValue = expr.getUserData(ValuesFlow.VALUE_KEY);
        if (Objects.equals(val, curValue) || curValue == UNKNOWN_VALUE)
            return;
        if (curValue == null)
            expr.putUserData(ValuesFlow.VALUE_KEY, val);
        else
            expr.putUserData(ValuesFlow.VALUE_KEY, UNKNOWN_VALUE);
    }

    Frame merge(Frame other) {
        Map<Variable, Expression> res = mergeSources(other);
        Map<MemberInfo, Expression> resFields = mergeFields(other);
        if(resFields == null && res == null)
            return this;
        if(resFields == null)
            resFields = fieldValues;
        if(res == null)
            res = sources;
        return new Frame(this, res, resFields);
    }

    private Map<MemberInfo, Expression> mergeFields(Frame other) {
        Map<MemberInfo, Expression> resFields = null;
        for (Entry<MemberInfo, Expression> e : fieldValues.entrySet()) {
            Expression left = e.getValue();
            Expression right = other.fieldValues.get(e.getKey());
            Expression phi = left == null || right == null ? null : makePhiNode(left, right);
            if (phi == left)
                continue;
            if (resFields == null)
                resFields = new HashMap<>(fieldValues);
            resFields.put(e.getKey(), phi);
        }
        if(resFields == null)
            return null;
        resFields.values().removeIf(Objects::isNull);
        return Maps.compactify(resFields);
    }

    private Map<Variable, Expression> mergeSources(Frame other) {
        Map<Variable, Expression> res = null;
        for (Entry<Variable, Expression> e : sources.entrySet()) {
            Expression left = e.getValue();
            Expression right = other.get(e.getKey());
            Expression phi = makePhiNode(left, right);
            if (phi == left)
                continue;
            if (res == null)
                res = new IdentityHashMap<>(sources);
            res.put(e.getKey(), phi);
        }
        for(Entry<Variable, Expression> e : other.sources.entrySet()) {
            if(!sources.containsKey(e.getKey())) {
                if (res == null)
                    res = new IdentityHashMap<>(sources);
                res.put(e.getKey(), makePhiNode(e.getValue(), initial.get(e.getKey().getOriginalParameter())));
            }
        }
        return res;
    }

    static Frame combine(Frame left, Frame right) {
        if (left == null || left == right)
            return right;
        if (right == null)
            return left;
        return left.merge(right);
    }

    private static boolean isEqual(Expression left, Expression right) {
        if (left == right)
            return true;
        if (left == null || right == null)
            return false;
        if (left.getCode() == PHI_TYPE && right.getCode() == PHI_TYPE) {
            List<Expression> leftArgs = left.getArguments();
            List<Expression> rightArgs = right.getArguments();
            if (leftArgs.size() != rightArgs.size())
                return false;
            for (Expression arg : rightArgs) {
                if (!leftArgs.contains(arg))
                    return false;
            }
            return true;
        }
        return false;
    }

    static boolean isEqual(Frame left, Frame right) {
        if (left == right)
            return true;
        if (left == null || right == null)
            return false;
        Map<Variable, Expression> l = left.sources;
        Map<Variable, Expression> r = right.sources;
        if(l.size() != r.size())
            return false;
        for(Entry<Variable, Expression> e : l.entrySet()) {
            if(!isEqual(e.getValue(), r.get(e.getKey())))
                return false;
        }
        Map<MemberInfo, Expression> lf = left.fieldValues;
        Map<MemberInfo, Expression> rf = right.fieldValues;
        if(lf.size() != rf.size())
            return false;
        for(Entry<MemberInfo, Expression> e : lf.entrySet()) {
            if(!isEqual(e.getValue(), rf.get(e.getKey())))
                return false;
        }
        return true;
    }

    private Frame(Frame parent, Map<Variable, Expression> sources, Map<MemberInfo, Expression> fields) {
        this.fc = parent.fc;
        this.initial = parent.initial;
        this.fieldValues = fields;
        this.sources = sources;
    }

    private Frame replace(Variable var, Expression replacement) {
        Expression expression = get(var);
        if (expression != replacement) {
            Map<Variable, Expression> res = new IdentityHashMap<>(sources);
            res.put(var, replacement);
            return new Frame(this, res, this.fieldValues);
        }
        return this;
    }
    
    private Frame replaceField(FieldReference fr, Expression replacement) {
        MemberInfo mi = new MemberInfo(fr);
        if(fieldValues.isEmpty()) {
            return new Frame(this, this.sources, Collections.singletonMap(mi, replacement));
        }
        Expression expression = fieldValues.get(mi);
        if (expression != replacement) {
            if(expression != null && fieldValues.size() == 1) {
                return new Frame(this, this.sources, Collections.singletonMap(mi, replacement));
            }
            Map<MemberInfo, Expression> res = new HashMap<>(fieldValues);
            res.put(mi, replacement);
            return new Frame(this, this.sources, res);
        }
        return this;
    }
    
    private Frame deleteFields() {
        if(fieldValues.isEmpty())
            return this;
        Map<MemberInfo, Expression> res = new HashMap<>();
        fieldValues.forEach((mi, expr) -> {
            if(expr.getCode() == UPDATE_TYPE || fc.cf.isKnownFinal(mi)) {
                res.put(mi, expr);
            } else {
                res.put(mi, fc.makeUpdatedNode(expr));
            }
        });
        return new Frame(this, this.sources, Maps.compactify(res));
    }

    private Frame replaceAll(UnaryOperator<Expression> op) {
        Map<Variable, Expression> res = null;
        for (Entry<Variable, Expression> e : sources.entrySet()) {
            Expression expr = op.apply(e.getValue());
            if (expr != e.getValue()) {
                if (res == null)
                    res = new IdentityHashMap<>(sources);
                res.put(e.getKey(), expr);
            }
        }
        return res == null ? this : new Frame(this, res, this.fieldValues);
    }

    private <A, B> Frame processBinaryOp(Expression expr, Class<A> leftType, Class<B> rightType, BiFunction<A, B, ?> op) {
        if (expr.getArguments().size() != 2)
            return this;
        Object left = expr.getArguments().get(0).getUserData(ValuesFlow.VALUE_KEY);
        if (left == UNKNOWN_VALUE) {
            storeValue(expr, left);
            return this;
        }
        if (left == null || left.getClass() != leftType)
            return this;
        Object right = expr.getArguments().get(1).getUserData(ValuesFlow.VALUE_KEY);
        if (right == UNKNOWN_VALUE) {
            storeValue(expr, right);
            return this;
        }
        if (right == null || right.getClass() != rightType)
            return this;
        Object result = UNKNOWN_VALUE;
        try {
            result = op.apply(leftType.cast(left), rightType.cast(right));
        } catch (Exception e) {
            // ignore
        }
        storeValue(expr, result);
        return this;
    }

    private <A> Frame processUnaryOp(Expression expr, Class<A> type, Function<A, ?> op) {
        if (expr.getArguments().size() != 1)
            return this;
        Object arg = expr.getArguments().get(0).getUserData(ValuesFlow.VALUE_KEY);
        if (arg == UNKNOWN_VALUE) {
            storeValue(expr, arg);
            return this;
        }
        if (!type.isInstance(arg))
            return this;
        Object result = op.apply(type.cast(arg));
        storeValue(expr, result);
        return this;
    }

    private Frame processKnownMethods(Expression expr, MethodReference mr) {
        if (mr.getDeclaringType().getInternalName().equals("java/lang/String")) {
            if (mr.getName().equals("length"))
                processUnaryOp(expr, String.class, String::length);
            else if (mr.getName().equals("toString") || mr.getName().equals("intern"))
                processUnaryOp(expr, String.class, Function.identity());
            else if (mr.getName().equals("trim"))
                processUnaryOp(expr, String.class, String::trim);
            else if (mr.getName().equals("substring"))
                processBinaryOp(expr, String.class, Integer.class, String::substring);
        } else if (mr.getDeclaringType().getInternalName().equals("java/lang/Math")) {
            if (mr.getName().equals("abs")) {
                switch (getType(expr)) {
                case Integer:
                    return processUnaryOp(expr, Integer.class, Math::abs);
                case Long:
                    return processUnaryOp(expr, Long.class, Math::abs);
                case Double:
                    return processUnaryOp(expr, Double.class, Math::abs);
                case Float:
                    return processUnaryOp(expr, Float.class, Math::abs);
                default:
                }
            }
        } else if (Nodes.isBoxing(expr) || Nodes.isUnboxing(expr)) {
            processUnaryOp(expr, Number.class, Function.identity());
        }
        return this;
    }

    private static JvmType getType(Expression expr) {
        TypeReference type = expr.getInferredType();
        return type == null ? JvmType.Void : type.getSimpleType();
    }

    private Frame processNeg(Expression expr, Frame target) {
        switch (getType(expr)) {
        case Integer:
            return target.processUnaryOp(expr, Integer.class, l -> -l);
        case Long:
            return target.processUnaryOp(expr, Long.class, l -> -l);
        case Double:
            return target.processUnaryOp(expr, Double.class, l -> -l);
        case Float:
            return target.processUnaryOp(expr, Float.class, l -> -l);
        default:
        }
        return target;
    }

    private Frame processRem(Expression expr, Frame target) {
        switch (getType(expr)) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a % b);
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a % b);
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a % b);
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a % b);
        default:
        }
        return target;
    }

    private Frame processDiv(Expression expr, Frame target) {
        switch (getType(expr)) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a / b);
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a / b);
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a / b);
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a / b);
        default:
        }
        return target;
    }

    private Frame processMul(Expression expr, Frame target) {
        switch (getType(expr)) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a * b);
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a * b);
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a * b);
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a * b);
        default:
        }
        return target;
    }

    private Frame processSub(Expression expr, Frame target) {
        switch (getType(expr)) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a - b);
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a - b);
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a - b);
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a - b);
        default:
        }
        return target;
    }

    private Frame processAdd(Expression expr, Frame target) {
        switch (getType(expr)) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, Integer::sum);
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, Long::sum);
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, Double::sum);
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, Float::sum);
        default:
        }
        return target;
    }

    private Frame processCmpGe(Expression expr, Frame target) {
        switch (getType(expr.getArguments().get(0))) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() >= b.intValue());
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() >= b.longValue());
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() >= b
                    .doubleValue());
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() >= b.floatValue());
        default:
        }
        return target;
    }

    private Frame processCmpGt(Expression expr, Frame target) {
        switch (getType(expr.getArguments().get(0))) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() > b.intValue());
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() > b.longValue());
        case Double:
            return target
                    .processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() > b.doubleValue());
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() > b.floatValue());
        default:
        }
        return target;
    }

    private Frame processCmpLe(Expression expr, Frame target) {
        switch (getType(expr.getArguments().get(0))) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() <= b.intValue());
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() <= b.longValue());
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() <= b
                    .doubleValue());
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() <= b.floatValue());
        default:
        }
        return target;
    }

    private Frame processCmpLt(Expression expr, Frame target) {
        switch (getType(expr.getArguments().get(0))) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() < b.intValue());
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() < b.longValue());
        case Double:
            return target
                    .processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() < b.doubleValue());
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() < b.floatValue());
        default:
        }
        return target;
    }

    private Frame processCmpNe(Expression expr, Frame target) {
        switch (getType(expr.getArguments().get(0))) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() != b.intValue());
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() != b.longValue());
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() != b
                    .doubleValue());
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() != b.floatValue());
        default:
        }
        return target;
    }

    private Frame processCmpEq(Expression expr, Frame target) {
        switch (getType(expr.getArguments().get(0))) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() == b.intValue());
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() == b.longValue());
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() == b
                    .doubleValue());
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() == b.floatValue());
        default:
        }
        return target;
    }

    static Expression makePhiNode(Expression left, Expression right) {
        if (left == null)
            return right;
        if (right == null || left == right)
            return left;
        if (left.getCode() == AstCode.LdC && right.getCode() == AstCode.LdC && Objects.equals(left.getOperand(), right
                .getOperand()))
            return left;
        if (left.getCode() == UPDATE_TYPE) {
            Expression leftContent = left.getArguments().get(0);
            if (leftContent == right || right.getCode() == UPDATE_TYPE && leftContent == right.getArguments().get(0))
                return left;
        } else if (right.getCode() == UPDATE_TYPE && right.getArguments().get(0) == left) {
            return right;
        }
        List<Expression> children = new ArrayList<>();
        if (left.getCode() == PHI_TYPE) {
            children.addAll(left.getArguments());
        } else {
            children.add(left);
        }
        int baseSize = children.size();
        if (right.getCode() == PHI_TYPE) {
            for (Expression arg : right.getArguments()) {
                if (!children.contains(arg))
                    children.add(arg);
            }
        } else {
            if (!children.contains(right))
                children.add(right);
        }
        if (children.size() == baseSize) {
            return left;
        }
        Expression phi = new Expression(PHI_TYPE, null, 0, children);
        Object leftValue = ValuesFlow.getValue(left);
        Object rightValue = ValuesFlow.getValue(right);
        if (leftValue != null || rightValue != null) {
            if (Objects.equals(leftValue, rightValue))
                storeValue(phi, leftValue);
            else
                storeValue(phi, UNKNOWN_VALUE);
        }
        return phi;
    }
}