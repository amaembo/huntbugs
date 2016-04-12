/*
 * Copyright 2015, 2016 Tagir Valeev
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
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import one.util.huntbugs.util.Nodes;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.assembler.metadata.VariableDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Lambda;
import com.strobel.decompiler.ast.Variable;

class Frame {
    final Expression[] sources;
    static final AstCode PHI_TYPE = AstCode.Wrap;
    static final AstCode UPDATE_TYPE = AstCode.Nop;
    static final Object UNKNOWN_VALUE = new Object();

    Frame(MethodDefinition md) {
        this.sources = new Expression[md.getBody().getMaxLocals()];
        for (ParameterDefinition pd : md.getParameters()) {
            Expression expression = new Expression(AstCode.Load, pd, 0);
            expression.setInferredType(pd.getParameterType());
            expression.setExpectedType(pd.getParameterType());
            sources[pd.getSlot()] = expression;
        }
    }

    Frame processChildren(Expression expr) {
        Frame result = this;
        for (Expression child : expr.getArguments()) {
            result = result.process(child);
        }
        if (expr.getOperand() instanceof Lambda) {
            Lambda lambda = (Lambda) expr.getOperand();
            MethodReference method = lambda.getMethod();
            // TODO: support lambdas
            /*
             * if (method != null) new
             * Frame(method).process(lambda.getBody());
             */
        }
        return result;
    }

    Frame process(Expression expr) {
        if (expr.getCode() == AstCode.TernaryOp) {
            Expression cond = expr.getArguments().get(0);
            Expression left = expr.getArguments().get(1);
            Expression right = expr.getArguments().get(2);
            Frame target = process(cond);
            Frame leftFrame = target.process(left);
            Frame rightFrame = target.process(right);
            return leftFrame.merge(rightFrame);
        }
        Frame target = processChildren(expr);
        switch (expr.getCode()) {
        case Store: {
            Variable var = ((Variable) expr.getOperand());
            Expression arg = expr.getArguments().get(0);
            Expression source = ValuesFlow.getSource(arg);
            expr.putUserData(ValuesFlow.SOURCE_KEY, source);
            Object val = arg.getUserData(ValuesFlow.VALUE_KEY);
            storeValue(expr, val);
            VariableDefinition origVar = var.getOriginalVariable();
            if (origVar != null)
                return target.replace(origVar.getSlot(), source);
            return target;
        }
        case LdC: {
            storeValue(expr, expr.getOperand());
            return target;
        }
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
            switch (expr.getInferredType().getSimpleType()) {
            case Integer:
                return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a ^ b);
            case Long:
                return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a ^ b);
            default:
            }
            return target;
        }
        case Or: {
            switch (expr.getInferredType().getSimpleType()) {
            case Integer:
                return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a | b);
            case Long:
                return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a | b);
            default:
            }
            return target;
        }
        case And: {
            switch (expr.getInferredType().getSimpleType()) {
            case Integer:
                return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a & b);
            case Long:
                return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a & b);
            default:
            }
            return target;
        }
        case Shl: {
            switch (expr.getInferredType().getSimpleType()) {
            case Integer:
                return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a << b);
            case Long:
                return target.processBinaryOp(expr, Long.class, Integer.class, (a, b) -> a << b);
            default:
            }
            return target;
        }
        case Shr: {
            switch (expr.getInferredType().getSimpleType()) {
            case Integer:
                return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a >> b);
            case Long:
                return target.processBinaryOp(expr, Long.class, Integer.class, (a, b) -> a >> b);
            default:
            }
            return target;
        }
        case UShr: {
            switch (expr.getInferredType().getSimpleType()) {
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
            VariableDefinition origVar = var.getOriginalVariable();
            if (origVar != null) {
                Expression source = sources[origVar.getSlot()];
                if (source != null) {
                    expr.putUserData(ValuesFlow.SOURCE_KEY, source);
                    Object val = ValuesFlow.getValue(source);
                    storeValue(expr, val);
                }
            }
            return this;
        }
        case Inc:
            if (expr.getOperand() instanceof Variable) {
                Variable var = ((Variable) expr.getOperand());
                int slot = var.getOriginalVariable().getSlot();
                Expression source = sources[slot];
                target = target.replace(slot, expr);
                Object val = ValuesFlow.getValue(source);
                if(val instanceof Integer)
                    return target.processUnaryOp(expr, Integer.class, inc -> ((int)val)+inc);
                if(val instanceof Long)
                    return target.processUnaryOp(expr, Long.class, inc -> ((long)val)+inc);
            }
            return target;
        case PostIncrement:
        case PreIncrement: {
            Expression arg = expr.getArguments().get(0);
            if (arg.getOperand() instanceof Variable) {
                Variable var = ((Variable) arg.getOperand());
                // TODO: pass values
                return target.replace(var.getOriginalVariable().getSlot(), expr);
            }
            return target;
        }
        case InvokeInterface:
        case InvokeSpecial:
        case InvokeStatic:
        case InvokeVirtual: {
            MethodReference mr = (MethodReference) expr.getOperand();
            target.processKnownMethods(expr, mr);
            if (Nodes.isSideEffectFreeMethod(expr))
                return target;
            return target.replaceAll(src -> src.getCode() == AstCode.GetField || src.getCode() == AstCode.GetStatic
                || src.getCode() == AstCode.LoadElement ? makeUpdatedNode(src) : src);
        }
        case StoreElement: {
            return target.replaceAll(src -> src.getCode() == AstCode.LoadElement ? makeUpdatedNode(src) : src);
        }
        case PutField: {
            FieldDefinition fr = ((FieldReference) expr.getOperand()).resolve();
            return target.replaceAll(src -> src.getCode() == AstCode.GetField
                && fr.equals(((FieldReference) src.getOperand()).resolve()) ? makeUpdatedNode(src) : src);
        }
        case PutStatic: {
            FieldDefinition fr = ((FieldReference) expr.getOperand()).resolve();
            return target.replaceAll(src -> src.getCode() == AstCode.GetStatic
                && fr.equals(((FieldReference) src.getOperand()).resolve()) ? makeUpdatedNode(src) : src);
        }
        default: {
            return target;
        }
        }
    }

    private void storeValue(Expression expr, Object val) {
        Object curValue = ValuesFlow.getValue(expr);
        if(Objects.equals(val, curValue)) return;
        if(curValue == null)
            expr.putUserData(ValuesFlow.VALUE_KEY, val);
        else
            expr.putUserData(ValuesFlow.VALUE_KEY, UNKNOWN_VALUE);
    }

    Frame merge(Frame other) {
        Expression[] res = null;
        for (int i = 0; i < sources.length; i++) {
            Expression left = sources[i];
            Expression right = other.sources[i];
            if (right == null || right == left)
                continue;
            if (left != null && left.getCode() == AstCode.LdC && right.getCode() == AstCode.LdC
                && Objects.equals(left.getOperand(), right.getOperand()))
                continue;
            Expression phi = makePhiNode(left, right);
            if(phi == left)
                continue;
            if (res == null)
                res = sources.clone();
            res[i] = phi;
        }
        return res == null ? this : new Frame(res);
    }
    
    private static boolean isEqual(Expression left, Expression right) {
        if(left == right)
            return true;
        if (left == null || right == null)
            return false;
        if (left.getCode() == PHI_TYPE && right.getCode() == PHI_TYPE) {
            List<Expression> leftArgs = left.getArguments();
            List<Expression> rightArgs = right.getArguments();
            if(leftArgs.size() != rightArgs.size())
                return false;
            for(Expression arg : rightArgs) {
                if(!leftArgs.contains(arg))
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
        Expression[] l = left.sources;
        Expression[] r = right.sources;
        for(int i=0; i<l.length; i++) {
            if(!isEqual(l[i], r[i]))
                return false;
        }
        return true;
    }

    static Frame merge(Frame left, Frame right) {
        if (left == null)
            return right;
        if (right == null)
            return left;
        return left.merge(right);
    }

    private Frame(Expression[] sources) {
        this.sources = sources;
    }

    private Frame replace(int pos, Expression replacement) {
        if (sources[pos] != replacement) {
            Expression[] res = sources.clone();
            res[pos] = replacement;
            return new Frame(res);
        }
        return this;
    }

    private Frame replaceAll(UnaryOperator<Expression> op) {
        Expression[] res = null;
        for (int i = 0; i < sources.length; i++) {
            if (sources[i] == null)
                continue;
            Expression expr = op.apply(sources[i]);
            if (expr != sources[i]) {
                if (res == null)
                    res = sources.clone();
                res[i] = expr;
            }
        }
        return res == null ? this : new Frame(res);
    }

    private <A, B> Frame processBinaryOp(Expression expr, Class<A> leftType, Class<B> rightType, BiFunction<A, B, ?> op) {
        if (expr.getArguments().size() != 2)
            return this;
        Object left = ValuesFlow.getValue(expr.getArguments().get(0));
        if (left == null || left.getClass() != leftType)
            return this;
        Object right = ValuesFlow.getValue(expr.getArguments().get(1));
        if (right == null || right.getClass() != rightType)
            return this;
        Object result = op.apply(leftType.cast(left), rightType.cast(right));
        storeValue(expr, result);
        return this;
    }

    private <A> Frame processUnaryOp(Expression expr, Class<A> type, Function<A, ?> op) {
        if (expr.getArguments().size() != 1)
            return this;
        Object arg = ValuesFlow.getValue(expr.getArguments().get(0));
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
                switch (expr.getInferredType().getSimpleType()) {
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

    private Frame processNeg(Expression expr, Frame target) {
        switch (expr.getInferredType().getSimpleType()) {
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
        switch (expr.getInferredType().getSimpleType()) {
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
        switch (expr.getInferredType().getSimpleType()) {
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
        switch (expr.getInferredType().getSimpleType()) {
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
        switch (expr.getInferredType().getSimpleType()) {
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
        switch (expr.getInferredType().getSimpleType()) {
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
        switch (expr.getArguments().get(0).getInferredType().getSimpleType()) {
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() >= b
                    .intValue());
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() >= b
                    .longValue());
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() >= b
                    .doubleValue());
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() >= b
                    .floatValue());
        default:
        }
        return target;
    }

    private Frame processCmpGt(Expression expr, Frame target) {
        switch (expr.getArguments().get(0).getInferredType().getSimpleType()) {
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() > b
                    .intValue());
        case Long:
            return target
                    .processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() > b.longValue());
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() > b
                    .doubleValue());
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() > b
                    .floatValue());
        default:
        }
        return target;
    }

    private Frame processCmpLe(Expression expr, Frame target) {
        switch (expr.getArguments().get(0).getInferredType().getSimpleType()) {
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() <= b
                    .intValue());
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() <= b.longValue());
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() <= b
                    .doubleValue());
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() <= b
                    .floatValue());
        default:
        }
        return target;
    }

    private Frame processCmpLt(Expression expr, Frame target) {
        switch (expr.getArguments().get(0).getInferredType().getSimpleType()) {
        case Integer:
            return target
                    .processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() < b.intValue());
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() < b.longValue());
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() < b
                    .doubleValue());
        case Float:
            return target
                    .processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() < b.floatValue());
        default:
        }
        return target;
    }

    private Frame processCmpNe(Expression expr, Frame target) {
        TypeReference type = expr.getArguments().get(0).getInferredType();
        if(type == null)
            return target;
        switch (type.getSimpleType()) {
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() != b
                    .intValue());
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() != b.longValue());
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() != b
                    .doubleValue());
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() != b
                    .floatValue());
        default:
        }
        return target;
    }

    private Frame processCmpEq(Expression expr, Frame target) {
        TypeReference type = expr.getArguments().get(0).getInferredType();
        if(type == null)
            return target;
        switch (type.getSimpleType()) {
        case Integer:
            return target.processBinaryOp(expr, Integer.class, Integer.class, (a, b) -> a.intValue() == b
                    .intValue());
        case Long:
            return target.processBinaryOp(expr, Long.class, Long.class, (a, b) -> a.longValue() == b.longValue());
        case Double:
            return target.processBinaryOp(expr, Double.class, Double.class, (a, b) -> a.doubleValue() == b
                    .doubleValue());
        case Float:
            return target.processBinaryOp(expr, Float.class, Float.class, (a, b) -> a.floatValue() == b
                    .floatValue());
        default:
        }
        return target;
    }

    private Expression makePhiNode(Expression left, Expression right) {
        if(left == null)
            return right;
        if(right == null)
            return left;
        List<Expression> children = new ArrayList<>();
        if(left.getCode() == PHI_TYPE) {
            children.addAll(left.getArguments());
        } else {
            children.add(left);
        }
        int baseSize = children.size();
        if(right.getCode() == PHI_TYPE) {
            for(Expression arg : right.getArguments()) {
                if(!children.contains(arg))
                    children.add(arg);
            }
        } else {
            if(!children.contains(right))
                children.add(right);
        }
        if(children.size() == baseSize) {
            return left;
        }
        Expression phi = new Expression(PHI_TYPE, null, 0, children);
        Object leftValue = ValuesFlow.getValue(left);
        Object rightValue = ValuesFlow.getValue(right);
        if(leftValue != null || rightValue != null) {
            if(Objects.equals(leftValue, rightValue))
                storeValue(phi, leftValue);
            else
                storeValue(phi, UNKNOWN_VALUE);
        }
        return phi;
    }

    private static Expression makeUpdatedNode(Expression src) {
        return new Expression(UPDATE_TYPE, null, src.getOffset(), src);
    }
}