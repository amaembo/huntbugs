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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
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
import com.strobel.decompiler.ast.LoopType;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Switch;
import com.strobel.decompiler.ast.TryCatchBlock;

import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

/**
 * @author Tagir Valeev
 *
 */
public class CFG {
    private static final int BLOCKTYPE_UNKNOWN = -1;
    private static final int BLOCKTYPE_EXIT = -2;
    private static final int BLOCKTYPE_FAIL = -3;

    static final TypeDefinition runtimeException;
    static final TypeDefinition classCastException;
    static final TypeDefinition nullPointerException;
    static final TypeDefinition arrayIndexOutOfBoundsException;
    static final TypeDefinition arrayStoreException;
    static final TypeDefinition outOfMemoryError;
    static final TypeDefinition linkageError;
    static final TypeDefinition error;
    static final TypeDefinition throwable;
    static final TypeDefinition exception;
    
    static {
        MetadataSystem ms = MetadataSystem.instance();
        throwable = getException(ms, "java/lang/Throwable");
        exception = getException(ms, "java/lang/Exception");
        runtimeException = getException(ms, "java/lang/RuntimeException");
        classCastException = getException(ms, "java/lang/ClassCastException");
        nullPointerException = getException(ms, "java/lang/NullPointerException");
        arrayIndexOutOfBoundsException = getException(ms, "java/lang/ArrayIndexOutOfBoundsException");
        arrayStoreException = getException(ms, "java/lang/ArrayStoreException");
        outOfMemoryError = getException(ms, "java/lang/OutOfMemoryError");
        linkageError = getException(ms, "java/lang/LinkageError");
        error = getException(ms, "java/lang/Error");
    }

    static TypeDefinition getException(MetadataSystem ms, String internalName) {
        TypeReference tr = ms.lookupType(internalName);
        if(tr == null) {
            throw new InternalError("Unable to lookup exception "+internalName);
        }
        TypeDefinition td = tr.resolve();
        if(td == null) {
            throw new InternalError("Unable to resolve exception "+internalName);
        }
        return td;
    }
    
    final List<BasicBlock> blocks = new ArrayList<>();
    final Map<Lambda, CFG> lambdas = new HashMap<>();
    final BasicBlock closure;
    final MethodDefinition md;
    final Block body;
    final BasicBlock entry, exit = new BasicBlock(BLOCKTYPE_EXIT), fail = new BasicBlock(BLOCKTYPE_FAIL);
    final Map<Label, BasicBlock> labelTargets = new HashMap<>();
    // Number of block till which CFG is forward-only
    final int forwardTill;
    final boolean hasUnreachable;

    private CFG(MethodDefinition md, BasicBlock closure, Block methodBody) {
        this.md = md;
        this.body = methodBody;
        this.closure = closure;
        if (methodBody.getBody().isEmpty()) {
            entry = exit;
            hasUnreachable = false;
        } else {
            entry = new BasicBlock();
            buildBlock(entry, exit, new OuterJumpContext(), methodBody);
            fixBlocks();
            verify();
            hasUnreachable = blocks.stream().anyMatch(bb -> !bb.reached);
        }
        this.forwardTill = computeForwardTill();
    }

    private void verify() {
        for (BasicBlock bb : blocks) {
            if (bb.trueTarget == null ^ bb.falseTarget == null) {
                throw new IllegalStateException("Mismatch true/false targets CFG at [" + bb.getId() + "]: " + this);
            }
            if (bb.targets().anyMatch(target -> target.id == -1)) {
                throw new IllegalStateException("Not linked target block at [" + bb.getId() + "]: " + this);
            }
            if (bb.reached && bb.targets().anyMatch(target -> !target.reached)) {
                throw new IllegalStateException("Invalid flow control at [" + bb.getId() + "]: " + this);
            }
        }
    }

    private void fixBlocks() {
        entry.reached = true;
        for (BasicBlock bb : blocks) {
            while (bb.falseTarget != null && bb.falseTarget.expr != null && bb.falseTarget.expr
                    .getCode() == AstCode.LogicalAnd)
                bb.falseTarget = bb.falseTarget.passTarget == null ? bb.falseTarget.falseTarget
                        : bb.falseTarget.passTarget;
            while (bb.trueTarget != null && bb.trueTarget.expr != null && bb.trueTarget.expr
                    .getCode() == AstCode.LogicalOr)
                bb.trueTarget = bb.trueTarget.passTarget == null ? bb.trueTarget.trueTarget : bb.trueTarget.passTarget;
            if(bb.reached)
                bb.targets().forEach(t -> t.reached = true);
        }
    }

    private int computeForwardTill() {
        int forwardTill = blocks.size();
        for (BasicBlock bb : blocks) {
            int min = bb.targets().mapToInt(t -> t.id).filter(id -> id >= 0 && id < bb.id).min().orElse(forwardTill);
            if (min < forwardTill)
                forwardTill = min;
        }
        return forwardTill;
    }

    private void buildBlock(BasicBlock entry, BasicBlock exit, JumpContext jc, Block block) {
        BasicBlock curBlock = entry;
        BasicBlock nextBlock = null;
        Node prevNode = null;
        List<Node> body = block.getBody();
        if (body.isEmpty()) {
            throw new IllegalStateException("Empty body is supplied!");
        }
        if (body.size() == 1 && body.get(0) instanceof Label) {
            buildSingleLabel(entry, exit, (Label) body.get(0));
            return;
        }
        Set<Label> labels = new HashSet<>();
        for(Node node : body) {
            if(node instanceof Label) {
                labels.add((Label) node);
            }
        }
        if(!labels.isEmpty()) {
            jc = new LabelJumpContext(jc, labels);
        }
        Node last = body.get(body.size() - 1);
        if (last instanceof Label && labelTargets.put((Label) last, exit) != null) {
            throw new IllegalStateException("Label " + last + " is already linked");
        }
        for (Node node : body) {
            if (node instanceof Label) {
                Label label = (Label) node;
                if (prevNode == null) {
                    BasicBlock oldTarget = labelTargets.putIfAbsent(label, entry);
                    if (oldTarget != null && oldTarget != entry) {
                        oldTarget.setExpression(new Expression(AstCode.Goto, label, -1));
                        oldTarget.addTarget(EdgeType.PASS, entry);
                        register(oldTarget);
                    }
                } else {
                    nextBlock = labelTargets.computeIfAbsent(label, k -> new BasicBlock());
                }
                continue;
            }
            if (nextBlock == null)
                nextBlock = new BasicBlock();
            if (prevNode != null) {
                buildNode(curBlock, nextBlock, jc, prevNode);
                curBlock = nextBlock;
                nextBlock = null;
            }
            prevNode = node;
        }
        if (prevNode != null) {
            buildNode(curBlock, exit, jc, prevNode);
        }
    }

    private void buildSingleLabel(BasicBlock entry, BasicBlock exit, Label label) {
        BasicBlock oldTarget = labelTargets.putIfAbsent(label, entry);
        if (oldTarget != null && oldTarget != entry) {
            oldTarget.setExpression(new Expression(AstCode.Goto, label, -1));
            oldTarget.addTarget(EdgeType.PASS, entry);
            register(oldTarget);
        }
        entry.setExpression(new Expression(AstCode.Goto, label, -1));
        entry.addTarget(EdgeType.PASS, exit);
        register(entry);
        return;
    }

    private void buildNode(final BasicBlock curBlock, BasicBlock nextBlock, JumpContext jc, Node node) {
        if (node instanceof Expression) {
            BasicBlock lastBlock = buildExpr(curBlock, (Expression) node, jc);
            if (lastBlock != null)
                lastBlock.addTarget(EdgeType.PASS, nextBlock);
        } else if (node instanceof Condition) {
            buildCondition(curBlock, nextBlock, jc, (Condition) node);
        } else if (node instanceof Loop) {
            buildLoop(curBlock, nextBlock, jc, (Loop) node);
        } else if (node instanceof Switch) {
            buildSwitch(curBlock, nextBlock, jc, (Switch) node);
        } else if (node instanceof TryCatchBlock) {
            TryCatchBlock tryCatch = (TryCatchBlock) node;
            if (tryCatch.getFinallyBlock() == null || tryCatch.getFinallyBlock().getBody().isEmpty()) {
                buildTryCatch(curBlock, nextBlock, jc, tryCatch);
            } else if (tryCatch.getTryBlock().getBody().isEmpty() && tryCatch.getCatchBlocks().isEmpty()) {
                buildBlock(curBlock, nextBlock, jc, tryCatch.getFinallyBlock());
            } else {
                buildFinally(curBlock, nextBlock, jc, tryCatch);
            }
        }
    }

    private void buildFinally(final BasicBlock curBlock, BasicBlock nextBlock, JumpContext jc, TryCatchBlock tryCatch) {
        BasicBlock finallyBlock = new BasicBlock();
        FinallyJumpContext fjc = new FinallyJumpContext(jc);
        buildTryCatch(curBlock, finallyBlock, fjc, tryCatch);
        if(blocks.stream().flatMap(BasicBlock::targets).anyMatch(finallyBlock::equals)) {
            buildBlock(finallyBlock, nextBlock, jc, tryCatch.getFinallyBlock());
        }
        fjc.targetEntries.forEach((target, entry) -> {
            buildBlock(entry, target, jc, tryCatch.getFinallyBlock());
        });
    }

    // Processes try-catch only, ignores finally, if any
    private void buildTryCatch(final BasicBlock curBlock, BasicBlock nextBlock, JumpContext jc,
            TryCatchBlock tryCatch) {
        if (!tryCatch.getCatchBlocks().isEmpty()) {
            CatchJumpContext cjc = new CatchJumpContext(jc, nextBlock, tryCatch.getCatchBlocks());
            buildBlock(curBlock, nextBlock, cjc, tryCatch.getTryBlock());
            for (CatchBlock cb : tryCatch.getCatchBlocks()) {
                BasicBlock catchEntry = cjc.getEntry(cb);
                if (catchEntry != nextBlock)
                    buildBlock(catchEntry, nextBlock, jc, cb);
            }
        } else {
            buildBlock(curBlock, nextBlock, jc, tryCatch.getTryBlock());
        }
    }

    private void buildSwitch(final BasicBlock curBlock, BasicBlock nextBlock, JumpContext jc, Switch switchBlock) {
        Expression condition = switchBlock.getCondition();
        BasicBlock condBlock = buildExpr(curBlock, condition, jc);
        jc = new SwitchJumpContext(jc, nextBlock);
        List<CaseBlock> caseBlocks = switchBlock.getCaseBlocks();
        BasicBlock finalCondBlock = nextBlock;
        Map<Integer, BasicBlock> targets = new LinkedHashMap<>();
        List<BasicBlock> blocks = new ArrayList<>();
        CaseBlock prevBlock = null;
        for (CaseBlock cb : caseBlocks) {
            BasicBlock curBB;
            if (prevBlock != null && prevBlock.isDefault() && prevBlock.getBody().isEmpty()) {
                curBB = blocks.get(blocks.size() -1);
            } else {
                curBB = new BasicBlock();
            }
            blocks.add(curBB);
            if (cb.isDefault()) {
                finalCondBlock = curBB;
            } else {
                for(Integer val : cb.getValues()) {
                    targets.put(val, curBB);
                }
            }
            if(!cb.getBody().isEmpty() && cb.getBody().get(0) instanceof Label) {
                Label l = (Label) cb.getBody().get(0);
                labelTargets.putIfAbsent(l, curBB);
            }
        }
        blocks.add(nextBlock);
        BasicBlock prevCondBlock = condBlock;
        for(Entry<Integer, BasicBlock> entry : targets.entrySet()) {
            Expression ldc = new Expression(AstCode.LdC, entry.getKey(), -1);
            Expression eq = new Expression(AstCode.CmpEq, null, -1, condition, ldc);
            BasicBlock curCondBlock = register(new BasicBlock(eq));
            prevCondBlock.addTarget(prevCondBlock == condBlock ? EdgeType.PASS : EdgeType.FALSE, curCondBlock);
            curCondBlock.addTarget(EdgeType.TRUE, entry.getValue());
            prevCondBlock = curCondBlock;
        }
        prevCondBlock.addTarget(prevCondBlock == condBlock ? EdgeType.PASS : EdgeType.FALSE, finalCondBlock);
        for(int i=0; i<caseBlocks.size(); i++) {
            CaseBlock caseBlock = caseBlocks.get(i);
            BasicBlock caseBB = blocks.get(i);
            BasicBlock nextBB = blocks.get(i+1); 
            if (caseBlock.getBody().isEmpty()) {
                // likely a procyon bug: seems that return is expected
                buildNode(caseBB, nextBB, jc, new Expression(AstCode.Return, null, -1));
            } else {
                buildBlock(caseBB, nextBB, jc, caseBlock);
            }
        }
    }

    private void buildLoop(final BasicBlock curBlock, BasicBlock nextBlock, JumpContext jc, Loop loop)
            throws InternalError {
        Expression condition = loop.getCondition();
        if (condition == null) {
            jc = new LoopJumpContext(jc, curBlock, nextBlock, curBlock);
            buildBlock(curBlock, curBlock, jc, loop.getBody());
        } else if (loop.getLoopType() == LoopType.PreCondition) {
            BasicBlock condExitBlock = buildExpr(curBlock, condition, jc);
            if (loop.getBody().getBody().isEmpty()) {
                condExitBlock.addTarget(EdgeType.TRUE, curBlock);
                condExitBlock.addTarget(EdgeType.FALSE, nextBlock);
            } else {
                jc = new LoopJumpContext(jc, curBlock, nextBlock, curBlock);
                BasicBlock bodyBlock = new BasicBlock();
                condExitBlock.addTarget(EdgeType.TRUE, bodyBlock);
                condExitBlock.addTarget(EdgeType.FALSE, nextBlock);
                buildBlock(bodyBlock, curBlock, jc, loop.getBody());
            }
        } else if (loop.getLoopType() == LoopType.PostCondition) {
            if (loop.getBody().getBody().isEmpty())
                throw new InternalError("Unexpected empty do-while loop");
            BasicBlock condEntryBlock = new BasicBlock();
            jc = new LoopJumpContext(jc, curBlock, nextBlock, condEntryBlock);
            buildBlock(curBlock, condEntryBlock, jc, loop.getBody());
            BasicBlock condExitBlock = buildExpr(condEntryBlock, condition, jc);
            condExitBlock.addTarget(EdgeType.TRUE, curBlock);
            condExitBlock.addTarget(EdgeType.FALSE, nextBlock);
        } else
            throw new InternalError("Unexpected loop type: " + loop.getLoopType());
    }

    private void buildCondition(final BasicBlock curBlock, BasicBlock nextBlock, JumpContext jc, Condition condition) {
        BasicBlock lastBlock = buildExpr(curBlock, condition.getCondition(), jc);
        if (!condition.getTrueBlock().getBody().isEmpty()) {
            BasicBlock leftBlock = new BasicBlock();
            lastBlock.addTarget(EdgeType.TRUE, leftBlock);
            buildBlock(leftBlock, nextBlock, jc, condition.getTrueBlock());
        } else {
            lastBlock.addTarget(EdgeType.TRUE, nextBlock);
        }
        if (!condition.getFalseBlock().getBody().isEmpty()) {
            BasicBlock rightBlock = new BasicBlock();
            lastBlock.addTarget(EdgeType.FALSE, rightBlock);
            buildBlock(rightBlock, nextBlock, jc, condition.getFalseBlock());
        } else {
            lastBlock.addTarget(EdgeType.FALSE, nextBlock);
        }
    }

    private BasicBlock buildExpr(BasicBlock entry, Expression expr, JumpContext jc) {
        switch (expr.getCode()) {
        case TernaryOp: {
            Expression cond = expr.getArguments().get(0);
            Expression left = expr.getArguments().get(1);
            Expression right = expr.getArguments().get(2);
            BasicBlock condBlock = buildExpr(entry, cond, jc);
            BasicBlock leftBlock = new BasicBlock();
            BasicBlock rightBlock = new BasicBlock();
            BasicBlock ternary = new BasicBlock(expr);
            condBlock.addTarget(EdgeType.TRUE, leftBlock);
            condBlock.addTarget(EdgeType.FALSE, rightBlock);
            buildExpr(leftBlock, left, jc).addTarget(EdgeType.PASS, ternary);
            buildExpr(rightBlock, right, jc).addTarget(EdgeType.PASS, ternary);
            return register(ternary);
        }
        case LogicalAnd: {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            BasicBlock rightBlock = new BasicBlock();
            BasicBlock and = new BasicBlock(expr);
            BasicBlock leftRes = buildExpr(entry, left, jc);
            leftRes.addTarget(EdgeType.TRUE, rightBlock);
            leftRes.addTarget(EdgeType.FALSE, and);
            BasicBlock rightRes = buildExpr(rightBlock, right, jc);
            rightRes.addTarget(EdgeType.TRUE, and);
            rightRes.addTarget(EdgeType.FALSE, and);
            return register(and);
        }
        case LogicalOr: {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            BasicBlock rightBlock = new BasicBlock();
            BasicBlock or = new BasicBlock(expr);
            BasicBlock leftBlock = buildExpr(entry, left, jc);
            leftBlock.addTarget(EdgeType.FALSE, rightBlock);
            leftBlock.addTarget(EdgeType.TRUE, or);
            BasicBlock rightRes = buildExpr(rightBlock, right, jc);
            rightRes.addTarget(EdgeType.TRUE, or);
            rightRes.addTarget(EdgeType.FALSE, or);
            return register(or);
        }
        default:
            break;
        }
        BasicBlock block = entry;
        for (Expression child : expr.getArguments()) {
            BasicBlock lastExpr = buildExpr(block, child, jc);
            block = new BasicBlock();
            lastExpr.addTarget(EdgeType.PASS, block);
        }
        block.setExpression(expr);
        register(block);
        if (expr.getOperand() instanceof Lambda) {
            Lambda lambda = (Lambda) expr.getOperand();
            CFG lambdaCFG = new CFG(Nodes.getLambdaMethod(lambda), block, lambda.getBody());
            lambdas.put(lambda, lambdaCFG);
        }
        switch (expr.getCode()) {
        case AThrow: {
            TypeReference exc = expr.getInferredType();
            if (exc == null)
                exc = expr.getExpectedType();
            if (exc == null)
                exc = throwable;
            jc.addExceptional(block, exc);
            return null;
        }
        case CheckCast:
            jc.addExceptional(block, classCastException);
            jc.addExceptional(block, linkageError);
            break;
        case InstanceOf:
            jc.addExceptional(block, linkageError);
            break;
        case Goto:
            jc.addJump(block, (Label) expr.getOperand());
            return null;
        case LoopContinue:
            jc.addContinue(block, (Label) expr.getOperand());
            return null;
        case LoopOrSwitchBreak:
            jc.addBreak(block, (Label) expr.getOperand());
            return null;
        case Return:
            jc.addReturn(block);
            return null;
        case ArrayLength:
            jc.addExceptional(block, nullPointerException);
            break;
        case Bind:
        case InvokeDynamic:
            jc.addExceptional(block, error);
            jc.addExceptional(block, runtimeException);
            break;
        case InitObject:
        case InvokeInterface:
        case InvokeSpecial:
        case InvokeStatic:
        case InvokeVirtual: {
            MethodReference mr = (MethodReference) expr.getOperand();
            jc.addExceptional(block, error);
            jc.addExceptional(block, runtimeException);
            MethodDefinition md = mr.resolve();
            if (md != null) {
                for (TypeReference thrownType : md.getThrownTypes()) {
                    jc.addExceptional(block, thrownType);
                }
            } else {
                jc.addExceptional(block, exception);
            }
            break;
        }
        case LoadElement:
            jc.addExceptional(block, arrayIndexOutOfBoundsException);
            jc.addExceptional(block, nullPointerException);
            break;
        case StoreElement:
            jc.addExceptional(block, arrayIndexOutOfBoundsException);
            jc.addExceptional(block, arrayStoreException);
            jc.addExceptional(block, nullPointerException);
            break;
        case __New:
        case NewArray:
        case InitArray:
        case MultiANewArray:
            jc.addExceptional(block, outOfMemoryError);
            break;
        case PutStatic:
        case GetStatic: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            if (!fr.getDeclaringType().isEquivalentTo(md.getDeclaringType()))
                jc.addExceptional(block, linkageError);
            break;
        }
        case PutField:
        case GetField: {
            if (md.isStatic() || !Exprs.isThis(expr.getArguments().get(0))) {
                jc.addExceptional(block, nullPointerException);
                jc.addExceptional(block, linkageError);
            }
            break;
        }
        default:
        }
        return block;
    }

    private BasicBlock register(BasicBlock block) {
        block.setId(blocks.size());
        blocks.add(block);
        return block;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CFG for ").append(new MemberInfo(md)).append("\n");
        for (BasicBlock bb : blocks)
            sb.append(bb);
        return sb.toString();
    }

    public static CFG build(MethodDefinition md, Block body) {
        try {
            return new CFG(md, null, body);
        } catch (Exception e) {
            throw new RuntimeException("Unable to build CFG for "+new MemberInfo(md)+"\n"+body, e);
        }
    }

    public <STATE, FACT> boolean runDFA(Annotator<FACT> annotator, BiFunction<MethodDefinition, STATE, Dataflow<FACT, STATE>> dfFactory, int maxIter) {
        @SuppressWarnings("unchecked")
        STATE closureState = closure == null ? null : (STATE) closure.state;
        boolean valid = new DFARunner<>(annotator, dfFactory.apply(md, closureState)).run(maxIter);
        for (CFG subCFG : lambdas.values()) {
            valid &= subCFG.runDFA(annotator, dfFactory, maxIter);
        }
        return valid;
    }
    
    public void forBodies(BiConsumer<MethodDefinition, Block> consumer) {
        consumer.accept(md, body);
        lambdas.values().forEach(cfg -> cfg.forBodies(consumer));
    }
    
    public CFG getLambdaCFG(Lambda lambda) {
        return lambdas.get(lambda);
    }
    
    public boolean isReachable(Expression expr) {
        if(!hasUnreachable)
            return true;
        return blocks.stream().filter(bb -> bb.expr == expr).anyMatch(bb -> bb.reached);
    }

    void clearChanged() {
        for (BasicBlock bb : blocks) {
            bb.changed = false;
        }
        exit.changed = false;
        fail.changed = false;
    }

    class DFARunner<STATE, FACT, DF extends Annotator<FACT> & Dataflow<FACT, STATE>> {
        private final Annotator<FACT> annotator;
        private final Dataflow<FACT, STATE> df;
        private boolean changed = false;

        DFARunner(Annotator<FACT> annotator, Dataflow<FACT, STATE> df) {
            this.df = df;
            this.annotator = annotator;
        }

        boolean run(int maxIteration) {
            if (blocks.isEmpty()) {
                return true;
            }
            initialize();
            runIteration(blocks);
            boolean valid = true;
            if (changed && forwardTill < blocks.size()) {
                List<BasicBlock> subList = blocks.subList(forwardTill, blocks.size());
                valid = false;
                for (int iter = 0; iter < maxIteration; iter++) {
                    runIteration(subList);
                    if (!changed) {
                        valid = true;
                        break;
                    }
                }
                if (!valid) {
                    for (BasicBlock bb : subList) {
                        if (bb.changed) {
                            annotator.put(bb.expr, df.makeUnknownFact());
                            bb.state = null;
                        }
                    }
                    if(exit.changed) {
                        exit.state = null;
                    }
                    if(fail.changed) {
                        fail.state = null;
                    }
                }
            }
            @SuppressWarnings("unchecked")
            STATE exitState = (STATE) exit.state;
            df.onSuccess(exitState);
            @SuppressWarnings("unchecked")
            STATE failState = (STATE) fail.state;
            df.onFail(failState);
            return valid;
        }

        private void initialize() {
            for (BasicBlock bb : blocks) {
                bb.state = null;
            }
            exit.state = null;
            fail.state = null;
            entry.state = df.makeEntryState();
        }

        private void runIteration(List<BasicBlock> blocks) {
            changed = false;
            clearChanged();
            for (BasicBlock bb : blocks) {
                try {
                    if(!bb.reached) {
                        annotator.put(bb.expr, df.makeUnknownFact());
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    STATE state = (STATE) bb.state;
                    FACT fact = df.makeFact(state, bb.expr);
                    FACT oldFact = annotator.get(bb.expr);
                    if (!df.sameFact(oldFact, fact)) {
                        FACT updatedFact = df.mergeFacts(oldFact, fact);
                        if (!df.sameFact(updatedFact, oldFact)) {
                            annotator.put(bb.expr, updatedFact);
                            bb.changed = changed = true;
                        }
                    }
                    if (bb.passTarget != null) {
                        updateState(df.transferState(state, bb.expr), bb.passTarget);
                    }
                    if (bb.trueTarget != null || bb.falseTarget != null) {
                        TrueFalse<STATE> tf = df.transferConditionalState(state, bb.expr);
                        updateState(tf.trueState, bb.trueTarget);
                        updateState(tf.falseState, bb.falseTarget);
                    }
                    if (bb.failTargets != null) {
                        STATE newState = bb.expr.getCode() == AstCode.Ret ? df.transferState(state, bb.expr)
                                : df.transferExceptionalState(state, bb.expr);
                        for (BasicBlock target : bb.failTargets) {
                            updateState(newState, target);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error running DFA at block "+bb+"\n"+CFG.this+CFG.this.body, e);
                }
            }
        }

        private void updateState(STATE newState, BasicBlock target) {
            @SuppressWarnings("unchecked")
            STATE oldState = (STATE) target.state;
            if (oldState == null) {
                if (newState != null) {
                    target.state = newState;
                    target.changed = changed = true;
                }
            } else if (!df.sameState(oldState, newState)) {
                STATE updatedState = df.mergeStates(oldState, newState);
                target.state = updatedState;
                if (!df.sameState(oldState, updatedState)) {
                    target.changed = changed = true;
                }
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (BasicBlock bb : blocks) {
                sb.append(getBlockDescription(bb));
            }
            sb.append(getBlockDescription(exit));
            sb.append(getBlockDescription(fail));
            return sb.toString();
        }

        String getBlockDescription(BasicBlock bb) {
            return "[" + bb.getId() + "] " + (bb.changed ? "*" : " ") + " " + bb.state + " | " + (bb.expr == null ? "?"
                    : annotator.get(bb.expr)) + "\n";
        }
    }

    public enum EdgeType {
        PASS, TRUE, FALSE, FAIL;
    }

    static class BasicBlock {
        Object state;
        boolean changed, reached;
        int id = -1;
        Expression expr;
        BasicBlock passTarget;
        BasicBlock trueTarget;
        BasicBlock falseTarget;
        List<BasicBlock> failTargets;

        BasicBlock() {
        }

        BasicBlock(int id) {
            this.id = id;
        }

        BasicBlock(Expression expr) {
            this.expr = expr;
        }

        void setId(int id) {
            if (this.id >= 0)
                throw new IllegalStateException("Double id: " + this.id + "/" + id);
            this.id = id;
        }

        void setExpression(Expression expr) {
            if (this.expr != null)
                throw new IllegalStateException("Expression is set twice: " + expr + "\n" + this);
            this.expr = expr;
        }

        void addTarget(EdgeType type, BasicBlock target) {
            switch (type) {
            case FAIL:
                if (failTargets == null)
                    failTargets = new ArrayList<>();
                if (!failTargets.contains(target))
                    failTargets.add(target);
                break;
            case FALSE:
                if (falseTarget != null || passTarget != null)
                    throw new IllegalStateException("False target is set twice: " + expr);
                falseTarget = target;
                break;
            case PASS:
                if (passTarget != null || trueTarget != null || falseTarget != null)
                    throw new IllegalStateException("Pass target is set twice: " + expr);
                passTarget = target;
                break;
            case TRUE:
                if (trueTarget != null || passTarget != null)
                    throw new IllegalStateException("True target is set twice: " + expr);
                trueTarget = target;
                break;
            default:
                throw new InternalError();
            }
        }

        public String getId() {
            switch (id) {
            case BLOCKTYPE_UNKNOWN:
                return "UNKNOWN";
            case BLOCKTYPE_EXIT:
                return "EXIT";
            case BLOCKTYPE_FAIL:
                return "FAIL";
            default:
                return String.valueOf(id);
            }
        }

        public Stream<BasicBlock> targets() {
            Stream<BasicBlock> stream = Stream.of(passTarget, trueTarget, falseTarget);
            if (failTargets != null)
                stream = Stream.concat(stream, failTargets.stream());
            return stream.filter(Objects::nonNull);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[").append(getId()).append("]").append(!reached?"-":" ").append(expr).append("\n  ");
            if (passTarget != null)
                sb.append("PASS -> [").append(passTarget.getId()).append("] ");
            if (trueTarget != null)
                sb.append("TRUE -> [").append(trueTarget.getId()).append("] ");
            if (falseTarget != null)
                sb.append("FALSE -> [").append(falseTarget.getId()).append("] ");
            if (failTargets != null)
                sb.append("FAIL -> [").append(failTargets.stream().map(BasicBlock::getId).collect(Collectors.joining(
                    ","))).append("]");
            sb.append("\n");
            return sb.toString();
        }
    }

    interface JumpContext {
        void addReturn(BasicBlock block);

        void addBreak(BasicBlock block, Label label);

        void addContinue(BasicBlock block, Label label);

        void addJump(BasicBlock block, Label label);

        void addExceptional(BasicBlock block, TypeReference exception);
    }

    class OuterJumpContext implements JumpContext {
        @Override
        public void addExceptional(BasicBlock block, TypeReference exception) {
            block.addTarget(EdgeType.FAIL, fail);
        }

        @Override
        public void addReturn(BasicBlock block) {
            block.addTarget(EdgeType.PASS, exit);
        }

        @Override
        public void addBreak(BasicBlock block, Label label) {
            if (label != null)
                addJump(block, label);
            else
                throw new IllegalStateException("Misplaced break");
        }

        @Override
        public void addContinue(BasicBlock block, Label label) {
            throw new IllegalStateException("Misplaced continue");
        }

        @Override
        public void addJump(BasicBlock block, Label label) {
            block.addTarget(EdgeType.PASS, labelTargets.computeIfAbsent(label, k -> new BasicBlock()));
        }
    }

    static abstract class DelegatingJumpContext implements JumpContext {
        private final JumpContext parent;

        protected DelegatingJumpContext(JumpContext parent) {
            this.parent = parent;
        }

        @Override
        public void addReturn(BasicBlock block) {
            parent.addReturn(block);
        }

        @Override
        public void addBreak(BasicBlock block, Label label) {
            parent.addBreak(block, label);
        }

        @Override
        public void addContinue(BasicBlock block, Label label) {
            parent.addContinue(block, label);
        }

        @Override
        public void addJump(BasicBlock block, Label label) {
            parent.addJump(block, label);
        }

        @Override
        public void addExceptional(BasicBlock block, TypeReference exception) {
            parent.addExceptional(block, exception);
        }
    }

    static class SwitchJumpContext extends DelegatingJumpContext {
        private final BasicBlock exit;

        public SwitchJumpContext(JumpContext parent, BasicBlock exit) {
            super(parent);
            this.exit = exit;
        }

        @Override
        public void addBreak(BasicBlock block, Label label) {
            if (label == null) {
                block.addTarget(EdgeType.PASS, exit);
            } else {
                super.addBreak(block, label);
            }
        }
    }

    class LoopJumpContext extends DelegatingJumpContext {
        private final BasicBlock exit;
        private final BasicBlock continueTarget;
        private final BasicBlock entry;

        public LoopJumpContext(JumpContext parent, BasicBlock entry, BasicBlock exit, BasicBlock continueTarget) {
            super(parent);
            this.entry = entry;
            this.exit = exit;
            this.continueTarget = continueTarget;
        }

        @Override
        public void addBreak(BasicBlock block, Label label) {
            if (label == null) {
                block.addTarget(EdgeType.PASS, exit);
            } else {
                super.addBreak(block, label);
            }
        }

        @Override
        public void addContinue(BasicBlock block, Label label) {
            if (label == null || labelTargets.get(label) == entry) {
                block.addTarget(EdgeType.PASS, continueTarget);
            } else {
                super.addBreak(block, label);
            }
        }
    }
    
    class LabelJumpContext extends DelegatingJumpContext {
        private final Set<Label> labels;
        
        LabelJumpContext(JumpContext parent, Set<Label> labels) {
            super(parent);
            this.labels = labels;
        }

        @Override
        public void addBreak(BasicBlock block, Label label) {
            if(label != null) {
                addJump(block, label);
            } else {
                super.addBreak(block, label);
            }
        }

        @Override
        public void addJump(BasicBlock block, Label label) {
            if(labels.contains(label)) {
                block.addTarget(EdgeType.PASS, labelTargets.computeIfAbsent(label, k -> new BasicBlock()));
            } else {
                super.addJump(block, label);
            }
        }
    }

    static class CatchJumpContext extends DelegatingJumpContext {
        private final Map<CatchBlock, BasicBlock> catches;

        CatchJumpContext(JumpContext parent, BasicBlock nextBlock, List<CatchBlock> catchBlocks) {
            super(parent);
            this.catches = catchBlocks.stream().collect(Collectors.toMap(Function.identity(), cb -> cb.getBody()
                    .isEmpty() ? nextBlock : new BasicBlock(), (a, b) -> a, LinkedHashMap::new));
        }

        @Override
        public void addExceptional(BasicBlock block, TypeReference exception) {
            for (Entry<CatchBlock, BasicBlock> entry : catches.entrySet()) {
                for (TypeReference tr : types(entry.getKey())) {
                    if (Types.isInstance(exception, tr)) {
                        // Exact match: catch and return
                        block.addTarget(EdgeType.FAIL, entry.getValue());
                        return;
                    } else if (Types.isInstance(tr, exception) || !Types.hasCompleteHierarchy(tr.resolve())) {
                        // Inexact match: probably caught here or by another catch block
                        block.addTarget(EdgeType.FAIL, entry.getValue());
                    }
                }
            }
            super.addExceptional(block, exception);
        }

        BasicBlock getEntry(CatchBlock cb) {
            return catches.get(cb);
        }

        private static Collection<TypeReference> types(CatchBlock cb) {
            if (cb.getCaughtTypes().isEmpty())
                return Collections.singleton(cb.getExceptionType());
            return cb.getCaughtTypes();
        }
    }

    static class FinallyJumpContext extends DelegatingJumpContext {
        final Map<BasicBlock, BasicBlock> targetEntries = new HashMap<>();

        FinallyJumpContext(JumpContext parent) {
            super(parent);
        }

        private void replacePass(BasicBlock block) {
            if (block.passTarget == null)
                throw new IllegalStateException("Passtarget is null for " + block);
            block.passTarget = targetEntries.computeIfAbsent(block.passTarget, t -> new BasicBlock());
        }

        @Override
        public void addReturn(BasicBlock block) {
            super.addReturn(block);
            replacePass(block);
        }

        @Override
        public void addBreak(BasicBlock block, Label label) {
            super.addBreak(block, label);
            replacePass(block);
        }

        @Override
        public void addContinue(BasicBlock block, Label label) {
            super.addContinue(block, label);
            replacePass(block);
        }

        @Override
        public void addJump(BasicBlock block, Label label) {
            super.addJump(block, label);
            replacePass(block);
        }

        @Override
        public void addExceptional(BasicBlock block, TypeReference exception) {
            List<BasicBlock> oldTargets = block.failTargets;
            block.failTargets = null;
            super.addExceptional(block, exception);
            List<BasicBlock> newTargets = block.failTargets;
            block.failTargets = oldTargets;
            newTargets.forEach(t -> block.addTarget(EdgeType.FAIL, targetEntries.computeIfAbsent(t,
                tt -> new BasicBlock())));
        }
    }
}
