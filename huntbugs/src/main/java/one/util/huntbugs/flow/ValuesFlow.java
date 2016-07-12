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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

import com.strobel.assembler.metadata.BuiltinTypes;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.CaseBlock;
import com.strobel.decompiler.ast.CatchBlock;
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
    static final ThrowTargets EMPTY_TARGETS = new ThrowTargets(null, Collections.emptySet());
    
    static class ThrowTargets {
        final ThrowTargets parent;
        final Map<TypeReference, Frame> targets;
        
        public ThrowTargets(ThrowTargets parent, Set<TypeReference> exceptions) {
            this.parent = parent;
            if(exceptions.isEmpty())
                targets = Collections.emptyMap();
            else {
                targets = new LinkedHashMap<>();
                exceptions.forEach(e -> targets.put(e, null));
            }
        }
        
        public void merge(TypeReference exception, Frame frame) {
            if(!targets.isEmpty()) {
                for(Entry<TypeReference, Frame> entry : targets.entrySet()) {
                    if(Types.isInstance(exception, entry.getKey())) {
                        entry.setValue(Frame.combine(entry.getValue(), frame));
                        return;
                    } else if(Types.isInstance(entry.getKey(), exception) ||
                            !Types.hasCompleteHierarchy(entry.getKey().resolve())) {
                        entry.setValue(Frame.combine(entry.getValue(), frame));
                    }
                }
            }
            if(parent != null) {
                parent.merge(exception, frame);
            }
        }

        public boolean isEmpty() {
            return parent == null && targets.isEmpty();
        }

        public Frame getEntryFrame(CatchBlock catchBlock) {
            Frame frame = null;
            for(TypeReference tr : catchBlock.getCaughtTypes()) {
                frame = Frame.combine(frame, targets.get(tr));
            }
            if(catchBlock.getExceptionType() != null) {
                frame = Frame.combine(frame, targets.get(catchBlock.getExceptionType()));
            }
            return frame;
        }
    }
    
    static class FrameSet {
        boolean valid = true;
        Frame passFrame, breakFrame, continueFrame, returnFrame;
        ThrowTargets targets;
        Map<Label, Frame> labelFrames = null;
    
        FrameSet(Frame start, ThrowTargets targets) {
            this.passFrame = start;
            this.targets = targets;
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
                    if(passFrame == null) {
                        // strange case: expression is unreachable
                        valid = false;
                        return;
                    }
                    Expression expr = (Expression) n;
                    switch (expr.getCode()) {
                    case LoopOrSwitchBreak:
                        if(expr.getOperand() instanceof Label) {
                            valid = false;
                            return;
                        }
                        breakFrame = Frame.combine(breakFrame, passFrame);
                        passFrame = null;
                        continue;
                    case LoopContinue:
                        if(expr.getOperand() instanceof Label) {
                            valid = false;
                            return;
                        }
                        continueFrame = Frame.combine(continueFrame, passFrame);
                        passFrame = null;
                        continue;
                    case Return:
                        returnFrame = Frame.combine(returnFrame, passFrame.processChildren(expr, targets));
                        passFrame = null;
                        continue;
                    case AThrow: {
                        passFrame.processChildren(expr, targets);
                        TypeReference exc = expr.getInferredType();
                        if(exc == null) exc = expr.getExpectedType();
                        if(exc == null) exc = Frame.throwable;
                        targets.merge(exc, passFrame);
                        passFrame = null;
                        continue;
                    }
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
                        continue;
                    case Ret:
                        valid = false;
                        return;
                    case MonitorEnter:
                        passFrame = passFrame.process(expr, targets);
                        wasMonitor = true;
                        continue;
                    default:
                    }
                    passFrame = passFrame.process(expr, targets);
                } else if (n instanceof Condition) {
                    Condition cond = (Condition) n;
                    passFrame = passFrame.process(cond.getCondition(), targets);
                    FrameSet left = new FrameSet(passFrame, targets);
                    left.process(ctx, cond.getTrueBlock());
                    FrameSet right = new FrameSet(passFrame, targets);
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
                    if (tryCatch.getFinallyBlock() == null) {
                        Set<TypeReference> exceptions = new LinkedHashSet<>();
                        for(CatchBlock catchBlock : tryCatch.getCatchBlocks()) {
                            if(catchBlock.getCaughtTypes().isEmpty())
                                exceptions.add(catchBlock.getExceptionType());
                            else
                                exceptions.addAll(catchBlock.getCaughtTypes());
                        }
                        exceptions.remove(null);
                        ThrowTargets tryTargets = new ThrowTargets(targets, exceptions);
                        FrameSet trySet = new FrameSet(passFrame, tryTargets);
                        trySet.process(ctx, tryCatch.getTryBlock());
                        if(!trySet.valid) {
                            valid = false;
                            return;
                        }
                        passFrame = Frame.combine(passFrame, trySet.passFrame);
                        breakFrame = Frame.combine(breakFrame, trySet.breakFrame);
                        continueFrame = Frame.combine(continueFrame, trySet.continueFrame);
                        mergeLabels(trySet);
                        for(CatchBlock catchBlock : tryCatch.getCatchBlocks()) {
                            Frame entryFrame = tryTargets.getEntryFrame(catchBlock);
                            if(entryFrame == null)
                                continue;
                            FrameSet catchSet = new FrameSet(entryFrame, targets);
                            catchSet.process(ctx, catchBlock);
                            if(!catchSet.valid) {
                                valid = false;
                                return;
                            }
                            passFrame = Frame.combine(passFrame, catchSet.passFrame);
                            breakFrame = Frame.combine(breakFrame, catchSet.breakFrame);
                            continueFrame = Frame.combine(continueFrame, catchSet.continueFrame);
                            mergeLabels(catchSet);
                        }
                        continue;
                    }
                    // TODO: support normal catch/finally
                    valid = false;
                    return;
                } else if (n instanceof Switch) {
                    Switch switchBlock = (Switch) n;
                    passFrame = passFrame.process(switchBlock.getCondition(), targets);
                    FrameSet switchBody = new FrameSet(passFrame, targets);
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
                            FrameSet loopBody = new FrameSet(loopStart, targets);
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
                            Frame loopStart = passFrame;
                            Frame loopEnd = passFrame.process(loop.getCondition(), targets);
                            int iter = 0;
                            while(true) {
                                FrameSet loopBody = new FrameSet(loopEnd, targets);
                                loopBody.process(ctx, loop.getBody());
                                if(!loopBody.valid) {
                                    cleanUn(loop);
                                    valid = false;
                                    return;
                                }
                                Frame newLoopStart = Frame.combine(loopStart, Frame.combine(loopBody.passFrame, loopBody.continueFrame));
                                Frame newLoopEnd = newLoopStart == null ? null : newLoopStart.process(loop.getCondition(), targets);
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
                                FrameSet loopBody = new FrameSet(loopStart, targets);
                                loopBody.process(ctx, loop.getBody());
                                if(!loopBody.valid) {
                                    cleanUn(loop);
                                    valid = false;
                                    return;
                                }
                                Frame beforeCondition = Frame.combine(loopBody.passFrame, loopBody.continueFrame);
                                Frame newLoopEnd = beforeCondition == null ? null : beforeCondition.process(loop.getCondition(), targets);
                                mergeLabels(loopBody);
                                newLoopEnd = Frame.combine(loopEnd, newLoopEnd);
                                loopStart = Frame.combine(loopStart, newLoopEnd);
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
                    Inf.SOURCE.remove(expr);
                }
            }
        }
    }

    private static void collectLambdas(Expression expr, List<Lambda> lambdas) {
        for(Expression child : expr.getArguments()) {
            collectLambdas(child, lambdas);
        }
        if(expr.getOperand() instanceof Lambda) {
            lambdas.add((Lambda) expr.getOperand());
        }
    }
    
    public static List<Expression> annotate(Context ctx, MethodDefinition md, ClassFields cf, Block method, Frame closure) {
        ctx.incStat("ValuesFlow.Total");
        CFG cfg = CFG.build(md, method);
        List<Lambda> lambdas = new ArrayList<>();
        Annotator.forExpressions(method, expr -> collectLambdas(expr, lambdas));
        FrameContext fc = new FrameContext(md, cf);
        Frame origFrame = new Frame(fc, closure);
        List<Expression> origParams = new ArrayList<>(origFrame.initial.values());
        FrameSet fs = new FrameSet(origFrame, EMPTY_TARGETS);
        fs.process(ctx, method);
        boolean valid = fs.valid;
        if (fs.valid) {
            Frame exitFrame = Frame.combine(fs.returnFrame, fs.passFrame);
            fc.makeFieldsFrom(exitFrame);
            for(Lambda lambda : lambdas) {
                valid &= annotate(ctx, Nodes.getLambdaMethod(lambda), cf, lambda.getBody(), exitFrame) != null;
            }
            if (valid) {
                ctx.incStat("ValuesFlow");
            }
        }
        Inf.CONST.annotate(method);
        Inf.PURITY.annotate(method, fc);
        Inf.BACKLINK.annotate(method);
        return valid ? origParams : null;
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
    
    private static TypeReference mergeTypes(TypeReference t1, TypeReference t2) {
        if (t1 == null || t2 == null)
            return null;
        if (t1 == BuiltinTypes.Null)
            return t2;
        if (t2 == BuiltinTypes.Null)
            return t1;
        if (t1.isEquivalentTo(t2))
            return t1;
        if(t1.isArray() ^ t2.isArray())
            return null;
        if(t1.isArray()) {
            TypeReference merged = mergeTypes(t1.getElementType(), t2.getElementType());
            return merged == null ? null : merged.makeArrayType();
        }
        List<TypeReference> chain1 = Types.getBaseTypes(t1);
        List<TypeReference> chain2 = Types.getBaseTypes(t2);
        for (int i = Math.min(chain1.size(), chain2.size()) - 1; i >= 0; i--) {
            if (chain1.get(i).equals(chain2.get(i)))
                return chain1.get(i);
        }
        return null;
    }

    public static TypeReference reduceType(Expression input) {
        return reduce(input, e -> e.getCode() == AstCode.AConstNull ?
                BuiltinTypes.Null : Types.getExpressionType(e), ValuesFlow::mergeTypes, Objects::isNull);
    }

    public static Expression getSource(Expression input) {
        Expression source = Inf.SOURCE.get(input);
        return source == null ? input : source;
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
        Expression source = Inf.SOURCE.get(input);
        return source != null && source.getCode() == Frame.PHI_TYPE;
    }

    public static boolean isSpecial(Expression expr) {
        return expr.getCode() == Frame.PHI_TYPE || expr.getCode() == Frame.UPDATE_TYPE;
    }

    public static boolean hasUpdatedSource(Expression e) {
        return ValuesFlow.getSource(e).getCode() == Frame.UPDATE_TYPE;
    }
}
