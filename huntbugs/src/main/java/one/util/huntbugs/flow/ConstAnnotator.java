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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

/**
 * @author Tagir Valeev
 *
 */
public class ConstAnnotator extends Annotator<Object> implements Dataflow<Object, ConstAnnotator.ContextValues> {
    static final Object UNKNOWN_VALUE = new Object() {
        public String toString() {
            return "??";
        }
    };

    ConstAnnotator() {
        super("value", null);
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
    
    static final class ContextValues {
        static final ContextValues DEFAULT = new ContextValues(null);
        
        final Map<Variable, Object> values;
        
        private ContextValues(Map<Variable, Object> values) {
            this.values = values;
        }
        
        ContextValues merge(ContextValues other) {
            if(this == other || other == DEFAULT)
                return this;
            if(this == DEFAULT)
                return other;
            Map<Variable, Object> newValues = new HashMap<>(values);
            other.values.forEach((k, v) -> newValues.merge(k, v, (v1, v2) -> Objects.equals(v1, v2) ? v1 : UNKNOWN_VALUE));
            return new ContextValues(newValues);
        }
        
        ContextValues add(Variable var, Object value) {
            if(values == null) {
                return new ContextValues(Collections.singletonMap(var, value));
            }
            if(Objects.equals(value, values.get(var)))
                return this;
            Map<Variable, Object> newValues = new HashMap<>(values);
            newValues.put(var, value);
            return new ContextValues(newValues);
        }
        
        ContextValues remove(Variable var) {
            if(values != null && values.containsKey(var)) {
                if(values.size() == 1)
                    return DEFAULT;
                Map<Variable, Object> newValues = new HashMap<>(values);
                newValues.remove(var);
                return new ContextValues(newValues);
            }
            return this;
        }
        
        ContextValues transfer(Expression expr) {
            Variable var = Nodes.getWrittenVariable(expr);
            return var == null ? this : add(var, UNKNOWN_VALUE);
        }
        
        Object resolve(Expression expr) {
            Object oper = expr.getOperand();
            Object result = oper instanceof Variable && values != null ? values.get(oper) : null;
            return result == UNKNOWN_VALUE ? null : result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            ContextValues other = (ContextValues) obj;
            return Objects.equals(values, other.values);
        }
        
        @Override
        public String toString() {
            return values == null ? "{}" : values.toString();
        }
    }
    
    private Object resolve(ContextValues contextValues, Expression expr) {
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
        Object resolved = contextValues.resolve(expr);
        if(resolved != null)
            return resolved;
        return val;
    }

    private Object resolveConditional(ContextValues contextValues, Expression expr) {
        Object val = resolve(contextValues, expr);
        // Zero is excluded as 0.0 == -0.0
        if (val instanceof Double && ((double)val == 0.0 || Double.isNaN((double) val)))
            return UNKNOWN_VALUE;
        if (val instanceof Float && ((float)val == 0.0 || Float.isNaN((float) val)))
            return UNKNOWN_VALUE;
        return val;
    }

    private Object fromSource(ContextValues ctx, Expression expr) {
        Object value = ctx.resolve(expr);
        if(value != null)
            return value;
        Expression src = ValuesFlow.getSource(expr);
        if(src == expr)
            return UNKNOWN_VALUE;
        value = resolve(ctx, src);
        if(value != null)
            return value;
        if(src.getCode() == Frame.PHI_TYPE) {
            for(Expression child : src.getArguments()) {
                Object newVal = resolve(ctx, child);
                if (newVal == null) {
                    if (Exprs.isParameter(child) || child.getCode() == Frame.UPDATE_TYPE) {
                        return UNKNOWN_VALUE;
                    }
                } else if (value == null) {
                    value = newVal;
                } else if (!value.equals(newVal)) {
                    return UNKNOWN_VALUE;
                }
            }
            return value;
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

    @Override
    public ContextValues makeInitialState() {
        return ContextValues.DEFAULT;
    }
    
    @Override
    public ContextValues transferState(ContextValues src, Expression expr) {
        return src.transfer(expr);
    }

    @Override
    public ContextValues transferExceptionalState(ContextValues src, Expression expr) {
        return src.transfer(expr);
    }

    @Override
    public TrueFalse<ContextValues> transferConditionalState(ContextValues src, Expression expr) {
        boolean invert = false;
        while(expr.getCode() == AstCode.LogicalNot /*|| expr.getCode() == AstCode.LogicalAnd || expr.getCode() == AstCode.LogicalOr*/) {
            if(expr.getCode() == AstCode.LogicalNot)
                invert = !invert;
            expr = expr.getArguments().get(expr.getArguments().size()-1);
        }
        Expression arg = null;
        Object cst = null;
        if (expr.getCode() == AstCode.CmpEq || expr.getCode() == AstCode.CmpNe
            || (expr.getCode() == AstCode.InvokeVirtual && Methods.isEqualsMethod((MethodReference) expr.getOperand()))) {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            if(expr.getCode() == AstCode.CmpNe)
                invert = !invert;
            cst = resolveConditional(src, right);
            if(cst != null && cst != UNKNOWN_VALUE) {
                arg = left;  
            } else {
                cst = resolveConditional(src, left);
                if(cst != null && cst != UNKNOWN_VALUE) {
                    arg = right;
                }
            }
        }
        Variable var = null;
        if (arg != null && arg.getCode() == AstCode.Load) {
            var = (Variable) arg.getOperand();
        }
        return var == null ? new TrueFalse<>(src.transfer(expr))
                : new TrueFalse<>(src.add(var, cst), src.add(var, UNKNOWN_VALUE), invert);
    }

    @Override
    public ContextValues mergeStates(ContextValues s1, ContextValues s2) {
        return s1.merge(s2);
    }

    @Override
    public boolean sameState(ContextValues s1, ContextValues s2) {
        return s1.equals(s2);
    }

    @Override
    public Object makeFact(ContextValues ctx, Expression expr) {
        switch(expr.getCode()) {
        case LogicalAnd:
            return processBinaryOp(expr, Boolean.class, Boolean.class, Boolean::logicalAnd);
        case LogicalOr:
            return processBinaryOp(expr, Boolean.class, Boolean.class, Boolean::logicalOr);
        case TernaryOp: {
            Object cond = get(expr.getArguments().get(0));
            Object left = get(expr.getArguments().get(1));
            Object right = get(expr.getArguments().get(2));
            if(Integer.valueOf(1).equals(cond) || Boolean.TRUE.equals(cond)) {
                return left; 
            }
            if(Integer.valueOf(0).equals(cond) || Boolean.FALSE.equals(cond)) {
                return right;
            }
            return mergeFacts(left, right);
        }
        case Store:
            return resolve(ctx, expr.getArguments().get(0));
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
        case GetField:
            return fromSource(ctx, expr);
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
            return fromSource(ctx, expr);
        }
        default:
            return UNKNOWN_VALUE;
        }
    }

    @Override
    public Object mergeFacts(Object f1, Object f2) {
        if(f1 == null)
            return f2;
        if(f2 == null)
            return f1;
        if(f1 == UNKNOWN_VALUE || f2 == UNKNOWN_VALUE || !Objects.equals(f1, f2))
            return UNKNOWN_VALUE;
        return f1;
    }

    @Override
    public boolean sameFact(Object f1, Object f2) {
        return Objects.equals(f1, f2);
    }

    @Override
    public Object makeUnknownFact() {
        return UNKNOWN_VALUE;
    }
}
