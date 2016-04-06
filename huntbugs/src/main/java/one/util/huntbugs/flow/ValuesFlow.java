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

        Frame process(Expression expr) {
            switch (expr.getCode()) {
            case Store: {
                Variable var = ((Variable) expr.getOperand());
                Expression child = expr.getArguments().get(0);
                Frame target = process(child);
                expr.putUserData(SOURCE_KEY, getSource(child));
                VariableDefinition origVar = var.getOriginalVariable();
                if (origVar != null)
                    return target.replace(origVar.getSlot(), getSource(child));
                return target;
            }
            case Load: {
                Variable var = ((Variable) expr.getOperand());
                VariableDefinition origVar = var.getOriginalVariable();
                if (origVar != null)
                    expr.putUserData(SOURCE_KEY, sources[origVar.getSlot()]);
                return this;
            }
            case TernaryOp: {
                Expression cond = expr.getArguments().get(0);
                Expression left = expr.getArguments().get(1);
                Expression right = expr.getArguments().get(2);
                Frame target = process(cond);
                Frame leftFrame = target.process(left);
                Frame rightFrame = target.process(right);
                return leftFrame.merge(rightFrame);
            }
            case PostIncrement:
            case PreIncrement: {
                if (expr.getOperand() instanceof Variable) {
                    Variable var = ((Variable) expr.getOperand());
                    Expression child = expr.getArguments().get(0);
                    Frame target = process(child);
                    expr.putUserData(SOURCE_KEY, target.sources[var.getOriginalVariable().getSlot()]);
                    return target.replace(var.getOriginalVariable().getSlot(), child);
                }
                return processChildren(expr);
            }
            case InvokeInterface:
            case InvokeSpecial:
            case InvokeStatic:
            case InvokeVirtual: {
                return processChildren(expr)
                        .replaceAll(
                            src -> src.getCode() == AstCode.GetField || src.getCode() == AstCode.GetStatic ? makeUpdatedNode(src)
                                    : src);
            }
            case PutField: {
                Frame target = processChildren(expr);
                FieldDefinition fr = ((FieldReference) expr.getOperand()).resolve();
                return target.replaceAll(src -> src.getCode() == AstCode.GetField
                    && fr.equals(((FieldReference) src.getOperand()).resolve()) ? makeUpdatedNode(src) : src);
            }
            case PutStatic: {
                Frame target = processChildren(expr);
                FieldDefinition fr = ((FieldReference) expr.getOperand()).resolve();
                return target.replaceAll(src -> src.getCode() == AstCode.GetStatic
                    && fr.equals(((FieldReference) src.getOperand()).resolve()) ? makeUpdatedNode(src) : src);
            }
            default: {
                return processChildren(expr);
            }
            }
        }

        private Frame processChildren(Expression expr) {
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
