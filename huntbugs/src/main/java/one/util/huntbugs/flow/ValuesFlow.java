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

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.componentmodel.Key;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Label;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Switch;
import com.strobel.decompiler.ast.TryCatchBlock;

/**
 * @author lan
 *
 */
public class ValuesFlow {
    static final Key<Expression> SOURCE_KEY = Key.create("hb.valueSource");
    static final Key<Object> VALUE_KEY = Key.create("hb.value");

    static final AstCode PHI_TYPE = AstCode.Wrap;
    static final AstCode UPDATE_TYPE = AstCode.Nop;

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
        return reduce(input, Types::getExpressionType, (t1, t2) -> {
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

    public static Object getValue(Expression input) {
        return input.getUserData(VALUE_KEY);
    }

    static class FrameSet {
        boolean valid = true;
        Frame passFrame, breakFrame, continueFrame;

        FrameSet(Frame start) {
            this.passFrame = start;
        }

        void process(Block block) {
            boolean wasMonitor = false;
            for (Node n : block.getBody()) {
                if (!valid) {
                    // Something unsupported occurred
                    return;
                } else if (n instanceof Expression) {
                    Expression expr = (Expression) n;
                    switch (expr.getCode()) {
                    case LoopOrSwitchBreak:
                        breakFrame = Frame.merge(breakFrame, passFrame);
                        passFrame = null;
                        return;
                    case LoopContinue:
                        continueFrame = Frame.merge(continueFrame, passFrame);
                        passFrame = null;
                        return;
                    case Ret:
                        valid = false;
                        return;
                    case MonitorEnter:
                        passFrame = passFrame.process(expr);
                        wasMonitor = true;
                        continue;
                    default:
                    }
                    passFrame = passFrame.process(expr);
                } else if (n instanceof Condition) {
                    Condition cond = (Condition) n;
                    passFrame = passFrame.process(cond.getCondition());
                    FrameSet left = new FrameSet(passFrame);
                    left.process(cond.getTrueBlock());
                    FrameSet right = new FrameSet(passFrame);
                    right.process(cond.getFalseBlock());
                    if (!left.valid || !right.valid) {
                        valid = false;
                        return;
                    }
                    passFrame = Frame.merge(left.passFrame, right.passFrame);
                    breakFrame = Frame.merge(breakFrame, Frame.merge(left.breakFrame, right.breakFrame));
                    continueFrame = Frame.merge(continueFrame, Frame.merge(left.continueFrame, right.continueFrame));
                } else if (n instanceof Label) {
                    // Skip
                } else if (n instanceof TryCatchBlock) {
                    TryCatchBlock tryCatch = (TryCatchBlock) n;
                    if (wasMonitor && tryCatch.getCatchBlocks().isEmpty()) {
                        Block finallyBlock = tryCatch.getFinallyBlock();
                        if (finallyBlock != null && finallyBlock.getBody().size() == 1
                            && Nodes.isOp(finallyBlock.getBody().get(0), AstCode.MonitorExit)) {
                            process(tryCatch.getTryBlock());
                            wasMonitor = false;
                            continue;
                        }
                    }
                    // TODO: support
                    valid = false;
                    return;
                } else if (n instanceof Switch) {
                    Switch switchBlock = (Switch) n;
                    passFrame = passFrame.process(switchBlock.getCondition());
                    FrameSet switchBody = new FrameSet(passFrame);
                    for (Block caseBlock : switchBlock.getCaseBlocks()) {
                        switchBody.passFrame = Frame.merge(passFrame, switchBody.passFrame);
                        switchBody.process(caseBlock);
                    }
                    if (!switchBody.valid) {
                        valid = false;
                        return;
                    }
                    passFrame = Frame.merge(switchBody.passFrame, switchBody.breakFrame);
                    continueFrame = Frame.merge(continueFrame, switchBody.continueFrame);
                } else {
                    // TODO: support loops, exceptions
                    valid = false;
                    return;
                }
                wasMonitor = false;
            }
        }
    }

    public static void annotate(Context ctx, MethodDefinition md, Block method) {
        ctx.incStat("ValuesFlow.Total");
        FrameSet fs = new FrameSet(new Frame(md));
        fs.process(method);
        if (fs.valid) {
            ctx.incStat("ValuesFlow");
        }
    }
}
