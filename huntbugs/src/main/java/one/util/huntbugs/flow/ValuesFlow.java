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

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.assembler.metadata.VariableDefinition;
import com.strobel.componentmodel.Key;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Label;
import com.strobel.decompiler.ast.Lambda;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.TryCatchBlock;
import com.strobel.decompiler.ast.Variable;

/**
 * @author lan
 *
 */
public class ValuesFlow {
    private static final Key<Expression> SOURCE_KEY = Key.create("hb.valueSource");

    private static final AstCode PHI_TYPE = AstCode.Wrap;
    private static final AstCode UPDATE_TYPE = AstCode.Nop;

    static <T> T reduce(Expression input, Function<Expression, T> mapper, BinaryOperator<T> reducer) {
        Expression source = getSource(input);
        if (source.getCode() != PHI_TYPE)
            return mapper.apply(source);
        boolean first = true;
        T result = null;
        for (Expression child : source.getArguments()) {
            if (first) {
                result = reduce(child, mapper, reducer);
                first = false;
            } else {
                result = reducer.apply(result, reduce(child, mapper, reducer));
            }
        }
        return result;
    }

    public static TypeReference reduceType(Expression input) {
        return reduce(input, Expression::getInferredType, (t1, t2) -> {
            if (t1 == null || t2 == null)
                return null;
            if (t1.equals(t2))
                return t1;
            List<TypeReference> chain1 = Types.getBaseTypes(t1);
            List<TypeReference> chain2 = Types.getBaseTypes(t2);
            for (int i = Math.min(chain1.size(), chain2.size()) - 1; i >= 0; i--) {
                if (chain1.get(i).equals(chain2.get(i)))
                    return chain1.get(i);
            }
            return null;
        });
    }

    public static Expression getSource(Expression input) {
        Expression source = input.getUserData(SOURCE_KEY);
        return source == null ? input : source;
    }

    static class Frame {
        final Expression[] sources;

        Frame(MethodDefinition md) {
            this.sources = new Expression[md.getBody().getMaxLocals()];
            for (ParameterDefinition pd : md.getParameters()) {
                Expression expression = new Expression(AstCode.Load, pd, 0);
                expression.setInferredType(pd.getParameterType());
                expression.setExpectedType(pd.getParameterType());
                sources[pd.getSlot()] = expression;
            }
        }

        private Frame(Expression[] sources) {
            this.sources = sources;
        }

        Frame replace(int pos, Expression replacement) {
            if (sources[pos] != replacement) {
                Expression[] res = sources.clone();
                res[pos] = replacement;
                return new Frame(res);
            }
            return this;
        }

        Frame replaceAll(UnaryOperator<Expression> op) {
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

        <A, B> Frame processBinaryOp(Expression expr, Class<A> leftType, Class<B> rightType, BiFunction<A, B, ?> op) {
            if (expr.getArguments().size() != 2)
                return this;
            Object left = Nodes.getConstant(getSource(expr.getArguments().get(0)));
            if (left == null || left.getClass() != leftType)
                return this;
            Object right = Nodes.getConstant(getSource(expr.getArguments().get(1)));
            if (right == null || right.getClass() != rightType)
                return this;
            Object result = op.apply(leftType.cast(left), rightType.cast(right));
            expr.putUserData(SOURCE_KEY, new Expression(AstCode.LdC, result, 0));
            return this;
        }

        <A> Frame processUnaryOp(Expression expr, Class<A> type, Function<A, ?> op) {
            if (expr.getArguments().size() != 1)
                return this;
            Object arg = Nodes.getConstant(getSource(expr.getArguments().get(0)));
            if (arg == null || arg.getClass() != type)
                return this;
            Object result = op.apply(type.cast(arg));
            expr.putUserData(SOURCE_KEY, new Expression(AstCode.LdC, result, 0));
            return this;
        }

        Frame processKnownMethods(Expression expr, MethodReference mr) {
            if(mr.getDeclaringType().getInternalName().equals("java/lang/String")) {
                if(mr.getName().equals("length"))
                    processUnaryOp(expr, String.class, String::length);
                else if(mr.getName().equals("toString") || mr.getName().equals("intern"))
                    processUnaryOp(expr, String.class, Function.identity());
                else if(mr.getName().equals("trim"))
                    processUnaryOp(expr, String.class, String::trim);
                else if(mr.getName().equals("substring"))
                    processBinaryOp(expr, String.class, Integer.class, String::substring);
            } else if(mr.getDeclaringType().getInternalName().equals("java/lang/Math")) {
                if(mr.getName().equals("abs")) {
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
            }
            return this;
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
                Expression source = getSource(expr.getArguments().get(0));
                expr.putUserData(SOURCE_KEY, source);
                VariableDefinition origVar = var.getOriginalVariable();
                if (origVar != null)
                    return target.replace(origVar.getSlot(), source);
                return target;
            }
            case Add: {
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
            case Sub: {
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
            case Mul: {
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
            case Div: {
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
            case Rem: {
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
                return target.processUnaryOp(expr, Integer.class, i -> (long)i);
            case I2B:
                return target.processUnaryOp(expr, Integer.class, i -> (int)(byte)(int)i);
            case I2C:
                return target.processUnaryOp(expr, Integer.class, i -> (int)(char)(int)i);
            case I2S:
                return target.processUnaryOp(expr, Integer.class, i -> (int)(short)(int)i);
            case I2D:
                return target.processUnaryOp(expr, Integer.class, i -> (double)i);
            case I2F:
                return target.processUnaryOp(expr, Integer.class, i -> (float)i);
            case L2I:
                return target.processUnaryOp(expr, Long.class, l -> (int)(long)l);
            case L2D:
                return target.processUnaryOp(expr, Long.class, l -> (double)l);
            case L2F:
                return target.processUnaryOp(expr, Long.class, l -> (float)l);
            case F2L:
                return target.processUnaryOp(expr, Float.class, l -> (long)(float)l);
            case F2I:
                return target.processUnaryOp(expr, Float.class, l -> (int)(float)l);
            case F2D:
                return target.processUnaryOp(expr, Float.class, l -> (double)l);
            case D2F:
                return target.processUnaryOp(expr, Double.class, l -> (float)(double)l);
            case D2I:
                return target.processUnaryOp(expr, Double.class, l -> (int)(double)l);
            case D2L:
                return target.processUnaryOp(expr, Double.class, l -> (long)(double)l);
            case Neg: {
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
            case Load: {
                Variable var = ((Variable) expr.getOperand());
                VariableDefinition origVar = var.getOriginalVariable();
                if (origVar != null)
                    expr.putUserData(SOURCE_KEY, sources[origVar.getSlot()]);
                return this;
            }
            case PostIncrement:
            case PreIncrement: {
                if (expr.getOperand() instanceof Variable) {
                    Variable var = ((Variable) expr.getOperand());
                    Expression child = expr.getArguments().get(0);
                    expr.putUserData(SOURCE_KEY, target.sources[var.getOriginalVariable().getSlot()]);
                    return target.replace(var.getOriginalVariable().getSlot(), child);
                }
                return target;
            }
            case InvokeInterface:
            case InvokeSpecial:
            case InvokeStatic:
            case InvokeVirtual: {
                MethodReference mr = (MethodReference) expr.getOperand();
                return target
                        .processKnownMethods(expr, mr)
                        .replaceAll(
                            src -> src.getCode() == AstCode.GetField || src.getCode() == AstCode.GetStatic ? makeUpdatedNode(src)
                                    : src);
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

        Frame process(Block method) {
            Frame result = this;
            boolean wasMonitor = false;
            for (Node n : method.getBody()) {
                if (result == null) {
                    // Something unsupported occurred
                    return null;
                } else if (n instanceof Expression) {
                    Expression expr = (Expression) n;
                    switch (expr.getCode()) {
                    case LoopOrSwitchBreak:
                    case LoopContinue:
                    case Ret:
                        return null;
                    case MonitorEnter:
                        result = result.process(expr);
                        wasMonitor = true;
                        continue;
                    default:
                    }
                    result = result.process(expr);
                } else if (n instanceof Condition) {
                    Condition cond = (Condition) n;
                    result = result.process(cond.getCondition());
                    Frame left = result.process(cond.getTrueBlock());
                    Frame right = result.process(cond.getFalseBlock());
                    if (left == null || right == null)
                        return null;
                    result = left.merge(right);
                } else if (n instanceof Label) {
                    // Skip
                } else if (n instanceof TryCatchBlock) {
                    TryCatchBlock tryCatch = (TryCatchBlock) n;
                    if (wasMonitor && tryCatch.getCatchBlocks().isEmpty()) {
                        Block block = tryCatch.getFinallyBlock();
                        if (block != null && block.getBody().size() == 1
                            && Nodes.isOp(block.getBody().get(0), AstCode.MonitorExit)) {
                            result = result.process(tryCatch.getTryBlock());
                            wasMonitor = false;
                            continue;
                        }
                    }
                    // TODO: support
                    return null;
                } else {
                    // TODO: support switch, loops, exceptions
                    return null;
                }
                wasMonitor = false;
            }
            return result;
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
                if (res == null)
                    res = sources.clone();
                if (left == null) {
                    res[i] = right;
                    continue;
                }
                res[i] = makePhiNode(left, right);
            }
            return res == null ? this : new Frame(res);
        }

        private Expression makePhiNode(Expression left, Expression right) {
            return new Expression(PHI_TYPE, null, 0, left, right);
        }

        private static Expression makeUpdatedNode(Expression src) {
            return new Expression(UPDATE_TYPE, null, src.getOffset(), src);
        }
    }

    public static void annotate(Context ctx, MethodDefinition md, Block method) {
        ctx.incStat("LoadsTracker.Total");
        if (new Frame(md).process(method) != null) {
            ctx.incStat("LoadsTracker.Success");
        }
    }
}
