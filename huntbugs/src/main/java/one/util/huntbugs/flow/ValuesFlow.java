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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.componentmodel.Key;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.CaseBlock;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Label;
import com.strobel.decompiler.ast.Lambda;
import com.strobel.decompiler.ast.Loop;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Switch;
import com.strobel.decompiler.ast.TryCatchBlock;

/**
 * @author Tagir Valeev
 *
 */
public class ValuesFlow {
    static final Key<Expression> SOURCE_KEY = Key.create("hb.valueSource");
    static final Key<Object> VALUE_KEY = Key.create("hb.value");
    static final Key<Set<Expression>> BACK_LINKS_KEY = Key.create("hb.backlinks");
    
    static class FrameSet {
        boolean valid = true;
        Frame passFrame, breakFrame, continueFrame, returnFrame;
        Map<Label, Frame> labelFrames = null;
    
        FrameSet(Frame start) {
            this.passFrame = start;
        }
        
        private void mergeLabels(FrameSet other) {
            if(labelFrames == null) {
                labelFrames = other.labelFrames;
            } else if(other.labelFrames != null) {
                other.labelFrames.forEach((label, frame) ->
                    labelFrames.merge(label, frame, Frame::combine));
            }
            returnFrame = Frame.combine(returnFrame, other.returnFrame);
        }
    
        void process(Context ctx, Block block) {
            boolean wasMonitor = false;
            for (Node n : block.getBody()) {
                if (!valid) {
                    // Something unsupported occurred
                    return;
                } else if (n instanceof Expression) {
                    Expression expr = (Expression) n;
                    switch (expr.getCode()) {
                    case LoopOrSwitchBreak:
                        if(expr.getOperand() instanceof Label) {
                            valid = false;
                            return;
                        }
                        breakFrame = Frame.combine(breakFrame, passFrame);
                        passFrame = null;
                        return;
                    case LoopContinue:
                        if(expr.getOperand() instanceof Label) {
                            valid = false;
                            return;
                        }
                        continueFrame = Frame.combine(continueFrame, passFrame);
                        passFrame = null;
                        return;
                    case Return:
                        returnFrame = passFrame.processChildren(expr);
                        passFrame = null;
                        return;
                    case AThrow:
                        passFrame.processChildren(expr);
                        passFrame = null;
                        return;
                    case Goto:
                        if(expr.getOperand() instanceof Label) {
                            Label label = (Label)expr.getOperand();
                            if(expr.getOffset() < label.getOffset()) {
                                if(labelFrames == null)
                                    labelFrames = new HashMap<>();
                                labelFrames.merge(label, passFrame, Frame::combine);
                            } else {
                                valid = false; // backward gotos are not supported yet
                            }
                        }
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
                    if(passFrame == null) {
                        throw new IllegalStateException(expr.toString());
                    }
                    passFrame = passFrame.process(expr);
                } else if (n instanceof Condition) {
                    Condition cond = (Condition) n;
                    passFrame = passFrame.process(cond.getCondition());
                    FrameSet left = new FrameSet(passFrame);
                    left.process(ctx, cond.getTrueBlock());
                    FrameSet right = new FrameSet(passFrame);
                    right.process(ctx, cond.getFalseBlock());
                    if (!left.valid || !right.valid) {
                        valid = false;
                        return;
                    }
                    passFrame = Frame.combine(left.passFrame, right.passFrame);
                    breakFrame = Frame.combine(breakFrame, Frame.combine(left.breakFrame, right.breakFrame));
                    continueFrame = Frame.combine(continueFrame, Frame.combine(left.continueFrame, right.continueFrame));
                    mergeLabels(left);
                    mergeLabels(right);
                } else if (n instanceof Label) {
                    if(labelFrames != null) {
                        passFrame = Frame.combine(passFrame, labelFrames.remove(n));
                        if(labelFrames.isEmpty())
                            labelFrames = null;
                    }
                } else if (n instanceof TryCatchBlock) {
                    TryCatchBlock tryCatch = (TryCatchBlock) n;
                    if (wasMonitor && tryCatch.getCatchBlocks().isEmpty() && Nodes.isSynchorizedBlock(tryCatch)) {
                        process(ctx, tryCatch.getTryBlock());
                        wasMonitor = false;
                        continue;
                    }
                    // TODO: support normal catch/finally
                    valid = false;
                    return;
                } else if (n instanceof Switch) {
                    Switch switchBlock = (Switch) n;
                    passFrame = passFrame.process(switchBlock.getCondition());
                    FrameSet switchBody = new FrameSet(passFrame);
                    boolean hasDefault = false;
                    for (CaseBlock caseBlock : switchBlock.getCaseBlocks()) {
                        switchBody.passFrame = Frame.combine(passFrame, switchBody.passFrame);
                        switchBody.process(ctx, caseBlock);
                        hasDefault |= caseBlock.isDefault();
                    }
                    if (!switchBody.valid) {
                        valid = false;
                        return;
                    }
                    if(hasDefault)
                        passFrame = Frame.combine(switchBody.passFrame, switchBody.breakFrame);
                    else
                        passFrame = Frame.combine(Frame.combine(passFrame, switchBody.passFrame), switchBody.breakFrame);
                    continueFrame = Frame.combine(continueFrame, switchBody.continueFrame);
                    mergeLabels(switchBody);
                } else if (n instanceof Loop) {
                    Loop loop = (Loop) n;
                    ctx.incStat("DivergedLoops.Total");
                    if(loop.getCondition() == null) { // endless loop
                        Frame loopEnd = null;
                        Frame loopStart = passFrame;
                        int iter = 0;
                        while(true) {
                            FrameSet loopBody = new FrameSet(loopStart);
                            loopBody.process(ctx, loop.getBody());
                            if(!loopBody.valid) {
                                cleanUn(loop);
                                valid = false;
                                return;
                            }
                            loopEnd = Frame.combine(loopBody.breakFrame, loopEnd);
                            Frame newLoopStart = Frame.combine(loopBody.passFrame, loopBody.continueFrame);
                            newLoopStart = Frame.combine(loopStart, newLoopStart);
                            mergeLabels(loopBody);
                            if(Frame.isEqual(loopStart, newLoopStart))
                                break;
                            loopStart = newLoopStart;
                            if(++iter > ctx.getOptions().loopTraversalIterations) {
                                ctx.incStat("DivergedLoops");
                                cleanUn(loop);
                                valid = false;
                                return;
                            }
                        }
                        passFrame = loopEnd;
                        if(loopEnd == null)
                            return;
                    } else {
                        switch(loop.getLoopType()) {
                        case PreCondition: {
                            Frame loopEnd = passFrame.process(loop.getCondition());
                            int iter = 0;
                            while(true) {
                                FrameSet loopBody = new FrameSet(loopEnd);
                                loopBody.process(ctx, loop.getBody());
                                if(!loopBody.valid) {
                                    cleanUn(loop);
                                    valid = false;
                                    return;
                                }
                                Frame newLoopStart = Frame.combine(loopBody.passFrame, loopBody.continueFrame);
                                Frame newLoopEnd = newLoopStart == null ? null : newLoopStart.process(loop.getCondition());
                                newLoopEnd = Frame.combine(loopBody.breakFrame, newLoopEnd);
                                newLoopEnd = Frame.combine(loopEnd, newLoopEnd);
                                mergeLabels(loopBody);
                                if(Frame.isEqual(loopEnd, newLoopEnd))
                                    break;
                                loopEnd = newLoopEnd;
                                if(++iter > ctx.getOptions().loopTraversalIterations) {
                                    ctx.incStat("DivergedLoops");
                                    cleanUn(loop);
                                    valid = false;
                                    return;
                                }
                            }
                            passFrame = loopEnd;
                            break;
                        }
                        case PostCondition: {
                            Frame loopEnd = null;
                            Frame loopStart = passFrame;
                            int iter = 0;
                            while(true) {
                                FrameSet loopBody = new FrameSet(loopStart);
                                loopBody.process(ctx, loop.getBody());
                                if(!loopBody.valid) {
                                    cleanUn(loop);
                                    valid = false;
                                    return;
                                }
                                Frame beforeCondition = Frame.combine(loopBody.passFrame, loopBody.continueFrame);
                                Frame newLoopEnd = beforeCondition == null ? null : beforeCondition.process(loop.getCondition());
                                mergeLabels(loopBody);
                                newLoopEnd = Frame.combine(loopEnd, newLoopEnd);
                                loopStart = newLoopEnd;
                                newLoopEnd = Frame.combine(loopBody.breakFrame, newLoopEnd);
                                if(Frame.isEqual(loopEnd, newLoopEnd))
                                    break;
                                loopEnd = newLoopEnd;
                                if(++iter > ctx.getOptions().loopTraversalIterations) {
                                    ctx.incStat("DivergedLoops");
                                    cleanUn(loop);
                                    valid = false;
                                    return;
                                }
                            }
                            passFrame = loopEnd;
                            break;
                        }
                        }
                    }
                } else {
                    valid = false;
                    return;
                }
                wasMonitor = false;
            }
        }
    
        private void cleanUn(Node node) {
            for(Node child : node.getChildrenAndSelfRecursive()) {
                if(child instanceof Expression) {
                    Expression expr = (Expression)child;
                    if(expr.getUserData(SOURCE_KEY) != null)
                        expr.putUserData(SOURCE_KEY, null);
                    if(expr.getUserData(VALUE_KEY) != null)
                        expr.putUserData(VALUE_KEY, null);
                }
            }
        }
    }

    private static void initBackLinks(Expression expr, List<Lambda> lambdas) {
        Set<Expression> backLink = Collections.singleton(expr);
        for(Expression child : expr.getArguments()) {
            child.putUserData(BACK_LINKS_KEY, backLink);
            initBackLinks(child, lambdas);
        }
        if(expr.getOperand() instanceof Lambda) {
            lambdas.add((Lambda) expr.getOperand());
        }
    }
    
    private static void initBackLinks(Node node, List<Lambda> lambdas) {
        if(node instanceof Expression)
            initBackLinks((Expression)node, lambdas);
        else
            for(Node child : node.getChildren())
                initBackLinks(child, lambdas);
    }

    public static List<Expression> annotate(Context ctx, MethodDefinition md, ClassFields cf, Block method) {
        ctx.incStat("ValuesFlow.Total");
        List<Lambda> lambdas = new ArrayList<>();
        initBackLinks(method, lambdas);
        FrameContext fc = new FrameContext(md, cf);
        Frame origFrame = new Frame(fc);
        List<Expression> origParams = new ArrayList<>(origFrame.initial.values());
        FrameSet fs = new FrameSet(origFrame);
        fs.process(ctx, method);
        if (fs.valid) {
            Frame exitFrame = Frame.combine(fs.returnFrame, fs.passFrame);
            fc.makeFieldsFrom(exitFrame);
            boolean valid = true;
            for(Lambda lambda : lambdas) {
                valid &= annotate(ctx, Nodes.getLambdaMethod(lambda), cf, lambda.getBody()) != null;
            }
            if (valid) {
                ctx.incStat("ValuesFlow");
                return origParams;
            }
        }
        return null;
    }

    public static <T> T reduce(Expression input, Function<Expression, T> mapper, BinaryOperator<T> reducer,
            Predicate<T> pred) {
        Expression source = getSource(input);
        if (source.getCode() == AstCode.TernaryOp) {
            T left = reduce(source.getArguments().get(1), mapper, reducer, pred);
            if(pred.test(left))
                return left;
            T right = reduce(source.getArguments().get(2), mapper, reducer, pred);
            return reducer.apply(left, right);
        }
        if (source.getCode() != Frame.PHI_TYPE)
            return mapper.apply(source);
        boolean first = true;
        T result = null;
        for (Expression child : source.getArguments()) {
            if (first) {
                result = reduce(child, mapper, reducer, pred);
                first = false;
            } else {
                result = reducer.apply(result, reduce(child, mapper, reducer, pred));
            }
            if(pred.test(result))
                return result;
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
        }, Objects::isNull);
    }

    public static Expression getSource(Expression input) {
        Expression source = input.getUserData(SOURCE_KEY);
        return source == null ? input : source;
    }
    
    public static Set<Expression> findUsages(Expression input) {
        Set<Expression> set = input.getUserData(BACK_LINKS_KEY);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    public static Object getValue(Expression input) {
        Object value = input.getUserData(VALUE_KEY);
        return value == Frame.UNKNOWN_VALUE ? null : value;
    }

    public static boolean allMatch(Expression src, Predicate<Expression> pred) {
        if(src.getCode() == Frame.PHI_TYPE)
            return src.getArguments().stream().allMatch(pred);
        if(src.getCode() == AstCode.TernaryOp)
            return allMatch(getSource(src.getArguments().get(1)), pred) &&
                    allMatch(getSource(src.getArguments().get(2)), pred);
        return pred.test(src);
    }

    public static boolean anyMatch(Expression src, Predicate<Expression> pred) {
        if(src.getCode() == Frame.PHI_TYPE)
            return src.getArguments().stream().anyMatch(pred);
        if(src.getCode() == AstCode.TernaryOp)
            return anyMatch(getSource(src.getArguments().get(1)), pred) ||
                    anyMatch(getSource(src.getArguments().get(2)), pred);
        return pred.test(src);
    }
    
    public static Expression findFirst(Expression src, Predicate<Expression> pred) {
        if(src.getCode() == Frame.PHI_TYPE)
            return src.getArguments().stream().filter(pred).findFirst().orElse(null);
        if(src.getCode() == AstCode.TernaryOp) {
            Expression result = findFirst(getSource(src.getArguments().get(1)), pred);
            return result == null ? findFirst(getSource(src.getArguments().get(2)), pred) : result;
        }
        return pred.test(src) ? src : null;
    }

    public static boolean hasPhiSource(Expression input) {
        Expression source = input.getUserData(SOURCE_KEY);
        return source != null && source.getCode() == Frame.PHI_TYPE;
    }

    public static Stream<Expression> findTransitiveUsages(Expression expr, boolean includePhi) {
        return findUsages(expr).stream().filter(includePhi ? x -> true : x -> !hasPhiSource(x))
            .flatMap(x -> {
                if(x.getCode() == AstCode.Store)
                    return null;
                if(x.getCode() == AstCode.Load)
                    return findTransitiveUsages(x, includePhi);
                return Stream.of(x);
            });
    }
    
    private static boolean isAssertionStatusCheck(Expression expr) {
        if(expr.getCode() != AstCode.LogicalNot)
            return false;
        Expression arg = expr.getArguments().get(0);
        if(arg.getCode() != AstCode.GetStatic)
            return false;
        FieldReference fr = (FieldReference) arg.getOperand();
        return fr.getName().startsWith("$assertions");
    }
    
    private static boolean isAssertionCondition(Expression expr) {
        if(expr.getCode() != AstCode.LogicalAnd)
            return false;
        return expr.getArguments().stream().anyMatch(ValuesFlow::isAssertionStatusCheck);
    }
    
    public static boolean isAssertion(Expression expr) {
        Set<Expression> usages = findUsages(expr);
        return !usages.isEmpty() && usages.stream().allMatch(parent -> isAssertionCondition(parent) || isAssertion(parent));
    }
    
    public static boolean isSpecial(Expression expr) {
        return expr.getCode() == Frame.PHI_TYPE || expr.getCode() == Frame.UPDATE_TYPE;
    }
}
