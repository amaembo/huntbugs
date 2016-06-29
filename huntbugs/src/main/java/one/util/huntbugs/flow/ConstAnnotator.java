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

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.Nodes;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

/**
 * @author shustkost
 *
 */
public class ConstAnnotator extends Annotator<Object> {
    static final Object UNKNOWN_VALUE = new Object();

    ConstAnnotator() {
        super("value2", null);
    }
    
    /**
     * Returns statically known constant for given expression
     * 
     * @param input expression to text
     * @return statically known constant value (if any) or null (if value is unknown)
     * Possible return types are:
     * - boxed primitives
     * - String
     * - TypeReference (when constant is class literal)
     * - EnumConstant (which refers to enum type and enum constant name)
     * Note that sometimes boolean is returned as Integer (0 = false, 1 = true)
     */
    public Object getValue(Expression input) {
        Object value = get(input);
        return value == UNKNOWN_VALUE ? null : value;
    }
    
    public boolean isConst(Expression input, Object constant) {
        return constant.equals(get(input));
    }
    
    private class ContextValues {
        ContextValues parent;
        Expression expr;
        Object value;

        ContextValues(ContextValues parent, Expression expr, Object value) {
            this.parent = parent;
            this.expr = expr;
            this.value = value;
        }
        
        Object resolve(Expression expr) {
            if(this.expr == expr) {
                return value;
            }
            if(parent != null) {
                return parent.resolve(expr);
            }
            return UNKNOWN_VALUE;
        }
    }
    
    private boolean hasForwardLinks;
    private boolean isChanged;
    private ContextValues contextValues;
    
    private void push(Expression expr, Object value) {
        if (expr.getCode() == AstCode.LdC || value == null || value == UNKNOWN_VALUE)
            return;
        // Zero is excluded as 0.0 == -0.0
        if (value instanceof Double && ((double)value == 0.0 || Double.isNaN((double) value)))
            return;
        if (value instanceof Float && ((float)value == 0.0 || Float.isNaN((float) value)))
            return;
        Expression src = ValuesFlow.getSource(expr);
        if(src == expr)
            return;
        contextValues = new ContextValues(contextValues, src, value);
    }
    
    private Object resolve(Expression expr) {
        if(expr.getCode() == AstCode.LdC) {
            return expr.getOperand() == null ? UNKNOWN_VALUE : expr.getOperand();
        }
        Object val = get(expr);
        if(val instanceof Exceptional) {
            return UNKNOWN_VALUE;
        }
        if(val != UNKNOWN_VALUE && val != null) {
            return val;
        }
        if(contextValues != null) {
            return contextValues.resolve(expr);
        }
        return val;
    }

    void annotate(Block method) {
        hasForwardLinks = isChanged = false;
        contextValues = null;
        annotateNode(method);
        if(hasForwardLinks) {
            isChanged = false;
            annotateNode(method);
            if(isChanged) {
                isChanged = false;
                annotateNode(method);
                if(isChanged) {
                    throw new InternalError("Const annotation is diverged");
                }
            }
        }
    }

    private void annotateNode(Node node) {
        if(node instanceof Condition) {
            Condition cond = (Condition)node;
            update(cond.getCondition());
            ContextValues curCtx = contextValues;
            pushTrueContext(cond.getCondition());
            annotateNode(cond.getTrueBlock());
            contextValues = curCtx;
            pushFalseContext(cond.getCondition());
            annotateNode(cond.getFalseBlock());
            contextValues = curCtx;
            return;
        }
        for(Node child : Nodes.getChildren(node)) {
            if(child instanceof Expression) {
                update((Expression)child);
            } else {
                annotateNode(child);
            }
        }
    }

    private Object update(Expression expr) {
        Object val = computeValue(expr);
        Object curValue = get(expr);
        if (Objects.equals(val, curValue) || curValue == UNKNOWN_VALUE)
            return curValue;
        Object resValue = curValue == null ? val : UNKNOWN_VALUE;
        isChanged = true;
        put(expr, resValue);
        return resValue;
    }
    
    private Object computeValue(Expression expr) {
        switch(expr.getCode()) {
        case TernaryOp: {
            Expression cond = expr.getArguments().get(0);
            Expression left = expr.getArguments().get(1);
            Expression right = expr.getArguments().get(2);
            update(cond);
            ContextValues curCtx = contextValues;
            pushTrueContext(cond);
            Object leftVal = update(left);
            contextValues = curCtx;
            pushFalseContext(cond);
            Object rightVal = update(right);
            contextValues = curCtx;
            return Objects.equals(leftVal, rightVal) ? leftVal : UNKNOWN_VALUE;
        }
        case LogicalAnd: {
            update(expr.getArguments().get(0));
            ContextValues curCtx = contextValues;
            pushTrueContext(expr.getArguments().get(0));
            update(expr.getArguments().get(1));
            contextValues = curCtx;
            return processBinaryOp(expr, Boolean.class, Boolean.class, Boolean::logicalAnd);
        }
        case LogicalOr: {
            update(expr.getArguments().get(0));
            ContextValues curCtx = contextValues;
            pushFalseContext(expr.getArguments().get(0));
            update(expr.getArguments().get(1));
            contextValues = curCtx;
            return processBinaryOp(expr, Boolean.class, Boolean.class, Boolean::logicalOr);
        }
        default:
        }
        for(Expression child : expr.getArguments()) {
            update(child);
        }
        if (get(expr) == UNKNOWN_VALUE) {
            return UNKNOWN_VALUE;
        }
        switch (expr.getCode()) {
        case Store:
            return resolve(expr.getArguments().get(0));
        case LdC:
            return expr.getOperand();
        case ArrayLength: {
            Integer len = getArrayLength(expr.getArguments().get(0));
            return len == null ? UNKNOWN_VALUE : len;
        }
        case CmpEq:
            return processCmpEq(expr);
        case CmpNe:
            return processCmpNe(expr);
        case CmpLt:
            return processCmpLt(expr);
        case CmpLe:
            return processCmpLe(expr);
        case CmpGt:
            return processCmpGt(expr);
        case CmpGe:
            return processCmpGe(expr);
        case Add:
            return processAdd(expr);
        case Sub:
            return processSub(expr);
        case Mul:
            return processMul(expr);
        case Div:
            return processDiv(expr);
        case Rem:
            return processRem(expr);
        case Xor: {
            switch (getType(expr)) {
            case Integer:
                return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a ^ b);
            case Long:
                return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a ^ b);
            default:
            }
            return UNKNOWN_VALUE;
        }
        case Or: {
            switch (getType(expr)) {
            case Integer:
                return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a | b);
            case Long:
                return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a | b);
            default:
            }
            return UNKNOWN_VALUE;
        }
        case And: {
            switch (getType(expr)) {
            case Integer:
                return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a & b);
            case Long:
                return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a & b);
            default:
            }
            return UNKNOWN_VALUE;
        }
        case Shl: {
            switch (getType(expr)) {
            case Integer:
                return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a << b);
            case Long:
                return processBinaryOp(expr, Long.class, Integer.class, (a, b) -> a << b);
            default:
            }
            return UNKNOWN_VALUE;
        }
        case Shr: {
            switch (getType(expr)) {
            case Integer:
                return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a >> b);
            case Long:
                return processBinaryOp(expr, Long.class, Integer.class, (a, b) -> a >> b);
            default:
            }
            return UNKNOWN_VALUE;
        }
        case UShr: {
            switch (getType(expr)) {
            case Integer:
                return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a >>> b);
            case Long:
                return processBinaryOp(expr, Long.class, Integer.class, (a, b) -> a >>> b);
            default:
            }
            return UNKNOWN_VALUE;
        }
        case I2L:
            return processUnaryOp(expr, Integer.class, i -> (long) i);
        case I2B:
            return processUnaryOp(expr, Integer.class, i -> (int) (byte) (int) i);
        case I2C:
            return processUnaryOp(expr, Integer.class, i -> (int) (char) (int) i);
        case I2S:
            return processUnaryOp(expr, Integer.class, i -> (int) (short) (int) i);
        case I2D:
            return processUnaryOp(expr, Integer.class, i -> (double) i);
        case I2F:
            return processUnaryOp(expr, Integer.class, i -> (float) i);
        case L2I:
            return processUnaryOp(expr, Long.class, l -> (int) (long) l);
        case L2D:
            return processUnaryOp(expr, Long.class, l -> (double) l);
        case L2F:
            return processUnaryOp(expr, Long.class, l -> (float) l);
        case F2L:
            return processUnaryOp(expr, Float.class, l -> (long) (float) l);
        case F2I:
            return processUnaryOp(expr, Float.class, l -> (int) (float) l);
        case F2D:
            return processUnaryOp(expr, Float.class, l -> (double) l);
        case D2F:
            return processUnaryOp(expr, Double.class, l -> (float) (double) l);
        case D2I:
            return processUnaryOp(expr, Double.class, l -> (int) (double) l);
        case D2L:
            return processUnaryOp(expr, Double.class, l -> (long) (double) l);
        case Neg:
            return processNeg(expr);
        case Load:
        case GetField: {
            Expression src = ValuesFlow.getSource(expr);
            return src == expr ? UNKNOWN_VALUE : fromSource(src);
        }
        case Inc: {
            Expression src = ValuesFlow.getSource(expr);
            if(src.getCode() == Frame.UPDATE_TYPE) {
                src = src.getArguments().get(0);
                Object val = get(src);
                if (val instanceof Integer)
                    return processUnaryOp(expr, Integer.class, inc -> ((int) val) + inc);
                else if (val instanceof Long)
                    return processUnaryOp(expr, Long.class, inc -> ((long) val) + inc);
            }
            return UNKNOWN_VALUE;
        }
        case InitObject:
        case InvokeInterface:
        case InvokeSpecial:
        case InvokeStatic:
        case InvokeVirtual: {
            MethodReference mr = (MethodReference) expr.getOperand();
            return processKnownMethods(expr, mr);
        }
        case GetStatic: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            FieldDefinition fd = fr.resolve();
            if (fd != null && fd.isEnumConstant()) {
                return new EnumConstant(fd.getDeclaringType().getInternalName(), fd.getName());
            }
            Expression src = ValuesFlow.getSource(expr);
            return src == expr ? UNKNOWN_VALUE : fromSource(src);
        }
        default:
            return UNKNOWN_VALUE;
        }
    }

    private void pushTrueContext(Expression expr) {
        if (expr.getCode() == AstCode.LogicalAnd || expr.getCode() == AstCode.And) {
            pushTrueContext(expr.getArguments().get(0));
            pushTrueContext(expr.getArguments().get(1));
        } else if (expr.getCode() == AstCode.CmpEq
            || (expr.getCode() == AstCode.InvokeVirtual && Methods.isEqualsMethod((MethodReference) expr.getOperand()))) {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            push(left, resolve(right));
            push(right, resolve(left));
        } else if (expr.getCode() == AstCode.LogicalNot) {
            pushFalseContext(expr.getArguments().get(0));
        }
    }

    private void pushFalseContext(Expression expr) {
        if(expr.getCode() == AstCode.LogicalOr || expr.getCode() == AstCode.Or) {
            pushFalseContext(expr.getArguments().get(0));
            pushFalseContext(expr.getArguments().get(1));
        } else if(expr.getCode() == AstCode.CmpNe) {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            push(left, resolve(right));
            push(right, resolve(left));
        } else if(expr.getCode() == AstCode.LogicalNot) {
            pushTrueContext(expr.getArguments().get(0));
        }
    }

    private Object fromSource(Expression src) {
        Object value = resolve(src);
        if(value != null)
            return value;
        if(src.getCode() == Frame.PHI_TYPE) {
            Object val = null;
            for(Expression child : src.getArguments()) {
                Object newVal = resolve(child);
                if (newVal == null) {
                    if (Exprs.isParameter(child) || child.getCode() == Frame.UPDATE_TYPE) {
                        return UNKNOWN_VALUE;
                    }
                    hasForwardLinks = true;
                } else if (val == null) {
                    val = newVal;
                } else if (!val.equals(newVal)) {
                    return UNKNOWN_VALUE;
                }
            }
            return val;
        }
        return UNKNOWN_VALUE;
    }

    private Integer getArrayLength(Expression expression) {
        return ValuesFlow.reduce(expression, e -> {
            switch(e.getCode()) {
            case InvokeVirtual: {
                MethodReference mr = (MethodReference) e.getOperand();
                if (mr.getName().equals("clone") && mr.getErasedSignature().startsWith("()")) {
                    return getArrayLength(Exprs.getChild(e, 0));
                }
                return null;
            }
            case CheckCast:
                return getArrayLength(Exprs.getChild(e, 0));
            case InitArray:
                return e.getArguments().size();
            case NewArray:
                Object constant = getValue(e.getArguments().get(0));
                if(constant instanceof Integer)
                    return (Integer)constant;
                return null;
            default:
                return null;
            }
        }, (a, b) -> Objects.equals(a, b) ? a : null, Objects::isNull);
    }

    private Object processNeg(Expression expr) {
        switch (getType(expr)) {
        case Integer:
            return processUnaryOp(expr, Integer.class, l -> -l);
        case Long:
            return processUnaryOp(expr, Long.class, l -> -l);
        case Double:
            return processUnaryOp(expr, Double.class, l -> -l);
        case Float:
            return processUnaryOp(expr, Float.class, l -> -l);
        default:
        }
        return UNKNOWN_VALUE;
    }

    private Object processRem(Expression expr) {
        switch (getType(expr)) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a % b);
        case Long:
            return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a % b);
        case Double:
            return processBinaryOp(expr, Double.class, Double.class, (a, b) -> a % b);
        case Float:
            return processBinaryOp(expr, Float.class, Float.class, (a, b) -> a % b);
        default:
        }
        return UNKNOWN_VALUE;
    }

    private Object processDiv(Expression expr) {
        switch (getType(expr)) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a / b);
        case Long:
            return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a / b);
        case Double:
            return processBinaryOp(expr, Double.class, Double.class, (a, b) -> a / b);
        case Float:
            return processBinaryOp(expr, Float.class, Float.class, (a, b) -> a / b);
        default:
        }
        return UNKNOWN_VALUE;
    }

    private Object processMul(Expression expr) {
        switch (getType(expr)) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a * b);
        case Long:
            return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a * b);
        case Double:
            return processBinaryOp(expr, Double.class, Double.class, (a, b) -> a * b);
        case Float:
            return processBinaryOp(expr, Float.class, Float.class, (a, b) -> a * b);
        default:
        }
        return UNKNOWN_VALUE;
    }

    private Object processSub(Expression expr) {
        switch (getType(expr)) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a - b);
        case Long:
            return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a - b);
        case Double:
            return processBinaryOp(expr, Double.class, Double.class, (a, b) -> a - b);
        case Float:
            return processBinaryOp(expr, Float.class, Float.class, (a, b) -> a - b);
        default:
        }
        return UNKNOWN_VALUE;
    }

    private Object processAdd(Expression expr) {
        switch (getType(expr)) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return processBinaryOp(expr, Integer.class, Integer.class, Integer::sum);
        case Long:
            return processBinaryOp(expr, Long.class, Long.class, Long::sum);
        case Double:
            return processBinaryOp(expr, Double.class, Double.class, Double::sum);
        case Float:
            return processBinaryOp(expr, Float.class, Float.class, Float::sum);
        default:
        }
        return UNKNOWN_VALUE;
    }

    private Object processCmpGe(Expression expr) {
        switch (getType(expr.getArguments().get(0))) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() >= b.intValue());
        case Long:
            return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() >= b.longValue());
        case Double:
            return processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() >= b
                    .doubleValue());
        case Float:
            return processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() >= b.floatValue());
        default:
        }
        return UNKNOWN_VALUE;
    }

    private Object processCmpGt(Expression expr) {
        switch (getType(expr.getArguments().get(0))) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() > b.intValue());
        case Long:
            return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() > b.longValue());
        case Double:
            return processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() > b.doubleValue());
        case Float:
            return processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() > b.floatValue());
        default:
        }
        return UNKNOWN_VALUE;
    }

    private Object processCmpLe(Expression expr) {
        switch (getType(expr.getArguments().get(0))) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() <= b.intValue());
        case Long:
            return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() <= b.longValue());
        case Double:
            return processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() <= b
                    .doubleValue());
        case Float:
            return processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() <= b.floatValue());
        default:
        }
        return UNKNOWN_VALUE;
    }

    private Object processCmpLt(Expression expr) {
        switch (getType(expr.getArguments().get(0))) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() < b.intValue());
        case Long:
            return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() < b.longValue());
        case Double:
            return processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() < b.doubleValue());
        case Float:
            return processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() < b.floatValue());
        default:
        }
        return UNKNOWN_VALUE;
    }

    private Object processCmpNe(Expression expr) {
        switch (getType(expr.getArguments().get(0))) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() != b.intValue());
        case Long:
            return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() != b.longValue());
        case Double:
            return processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() != b
                    .doubleValue());
        case Float:
            return processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() != b.floatValue());
        default:
        }
        return UNKNOWN_VALUE;
    }

    private Object processCmpEq(Expression expr) {
        switch (getType(expr.getArguments().get(0))) {
        case Byte:
        case Short:
        case Character:
        case Integer:
            return processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() == b.intValue());
        case Long:
            return processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() == b.longValue());
        case Double:
            return processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() == b
                    .doubleValue());
        case Float:
            return processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() == b.floatValue());
        default:
        }
        return UNKNOWN_VALUE;
    }

    private Object processKnownMethods(Expression expr, MethodReference mr) {
        if (Methods.isEqualsMethod(mr)) {
            return processBinaryOp(expr, Object.class, Object.class, Object::equals);
        } else if (mr.getDeclaringType().getInternalName().equals("java/lang/String")) {
            if (mr.getName().equals("length"))
                return processUnaryOp(expr, String.class, String::length);
            else if (mr.getName().equals("toString") || mr.getName().equals("intern"))
                return processUnaryOp(expr, String.class, Function.identity());
            else if (mr.getName().equals("trim"))
                return processUnaryOp(expr, String.class, String::trim);
            else if (mr.getName().equals("substring"))
                return processBinaryOp(expr, String.class, Integer.class, String::substring);
            else if (mr.getName().equals("valueOf") && mr.getParameters().size() == 1) {
                if(mr.getErasedSignature().startsWith("(Z)")) {
                    // Handle specially to process possible Integer -> Boolean conversion
                    return processUnaryOp(expr, Boolean.class, String::valueOf);
                }
                return processUnaryOp(expr, Object.class, String::valueOf);
            }
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
            return processUnaryOp(expr, Number.class, Function.identity());
        } else if (mr.getName().equals("toString") && mr.getDeclaringType().getInternalName().startsWith("java/lang/")
            && expr.getArguments().size() == 1) {
            if(mr.getDeclaringType().getInternalName().equals("java/lang/Boolean")) {
                return processUnaryOp(expr, Boolean.class, Object::toString);
            }
            return processUnaryOp(expr, Object.class, Object::toString);
        } else if (expr.getCode() == AstCode.InvokeStatic && expr.getArguments().size() == 1) {
            if(mr.getName().equals("parseInt") && mr.getDeclaringType().getInternalName().equals("java/lang/Integer")) {
                return processUnaryOp(expr, String.class, Integer::parseInt);
            } else if(mr.getName().equals("parseLong") && mr.getDeclaringType().getInternalName().equals("java/lang/Long")) {
                return processUnaryOp(expr, String.class, Long::parseLong);
            } else if(mr.getName().equals("parseDouble") && mr.getDeclaringType().getInternalName().equals("java/lang/Double")) {
                return processUnaryOp(expr, String.class, Double::parseDouble);
            } else if(mr.getName().equals("parseFloat") && mr.getDeclaringType().getInternalName().equals("java/lang/Float")) {
                return processUnaryOp(expr, String.class, Float::parseFloat);
            }
        }
        return UNKNOWN_VALUE;
    }

    private <A> Object processUnaryOp(Expression expr, Class<A> type, Function<A, ?> op) {
        if (expr.getArguments().size() != 1)
            return UNKNOWN_VALUE;
        Object arg = get(expr.getArguments().get(0));
        if (arg == UNKNOWN_VALUE) {
            return UNKNOWN_VALUE;
        }
        if (!type.isInstance(arg)) {
            if(type == Boolean.class && arg instanceof Integer)
                arg = Integer.valueOf(1).equals(arg);
            else
                return UNKNOWN_VALUE;
        }
        try {
            return op.apply(type.cast(arg));
        } catch (Exception e) {
            return new Exceptional(e);
        }
    }

    private <A, B> Object processBinaryOp(Expression expr, Class<A> leftType, Class<B> rightType, BiFunction<A, B, ?> op) {
        if (expr.getArguments().size() != 2)
            return UNKNOWN_VALUE;
        Object left = get(expr.getArguments().get(0));
        if (left == UNKNOWN_VALUE || !leftType.isInstance(left))
            return UNKNOWN_VALUE;
        Object right = get(expr.getArguments().get(1));
        if (right == UNKNOWN_VALUE || !rightType.isInstance(right))
            return UNKNOWN_VALUE;
        try {
            return op.apply(leftType.cast(left), rightType.cast(right));
        } catch (Exception e) {
            return new Exceptional(e);
        }
    }

    private static JvmType getType(Expression expr) {
        TypeReference type = expr.getInferredType();
        return type == null ? JvmType.Void : type.getSimpleType();
    }
}
