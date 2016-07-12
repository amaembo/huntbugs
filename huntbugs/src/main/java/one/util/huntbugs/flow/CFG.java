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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
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
 * @author shustkost
 *
 */
public class CFG {
    private static final int BLOCKTYPE_UNKNOWN = -1;
    private static final int BLOCKTYPE_EXIT = -2;
    private static final int BLOCKTYPE_FAIL = -3;

    final List<BasicBlock> blocks = new ArrayList<>();
    final Map<Lambda, CFG> lambdas = new HashMap<>();
    final BasicBlock closure;
    final MethodDefinition md;
    final BasicBlock entry = new BasicBlock(), exit = new BasicBlock(BLOCKTYPE_EXIT), fail = new BasicBlock(
            BLOCKTYPE_FAIL);
    final Map<Label, BasicBlock> labelTargets = new HashMap<>();
    
    private CFG(MethodDefinition md, BasicBlock closure, Block methodBody) {
        this.md = md;
        this.closure = closure;
        buildBlock(entry, exit, new OuterJumpContext(), methodBody);
    }

    private void buildBlock(BasicBlock entry, BasicBlock exit, JumpContext jc, Block block) {
        BasicBlock curBlock = entry;
        BasicBlock nextBlock = null;
        Node prevNode = null;
        for (Node node : block.getBody()) {
            if (node instanceof Label) {
                nextBlock = labelTargets.computeIfAbsent((Label) node, k -> new BasicBlock());
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
            if(tryCatch.getFinallyBlock() == null) {
                buildTryCatch(curBlock, nextBlock, jc, tryCatch);
            } else {
                BasicBlock finallyBlock = new BasicBlock();
                FinallyJumpContext fjc = new FinallyJumpContext(jc, finallyBlock);
                buildTryCatch(curBlock, finallyBlock, fjc, tryCatch);
                BasicBlock finallyExit = new BasicBlock(new Expression(AstCode.Ret, null, -1));
                buildBlock(finallyBlock, finallyExit, jc, tryCatch.getFinallyBlock());
                finallyExit.failTargets = new ArrayList<>(fjc.targets);
            }
        }
    }

    // Processes try-catch only, ignores finally, if any
    private void buildTryCatch(final BasicBlock curBlock, BasicBlock nextBlock, JumpContext jc, TryCatchBlock tryCatch) {
        if (!tryCatch.getCatchBlocks().isEmpty()) {
            CatchJumpContext cjc = new CatchJumpContext(jc, tryCatch.getCatchBlocks());
            buildBlock(curBlock, nextBlock, cjc, tryCatch.getTryBlock());
            for(CatchBlock cb : tryCatch.getCatchBlocks()) {
                BasicBlock catchEntry = cjc.getEntry(cb);
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
        BasicBlock curCaseBlock = new BasicBlock();
        BasicBlock nextCaseBlock;
        BasicBlock curCondBlock = new BasicBlock();
        BasicBlock nextCondBlock;
        condBlock.addTarget(EdgeType.PASS, curCondBlock);
        for (int i = 0; i < caseBlocks.size(); i++) {
            nextCaseBlock = i == caseBlocks.size() - 1 ? nextBlock : new BasicBlock();
            CaseBlock caseBlock = caseBlocks.get(i);
            List<Integer> vals = caseBlock.getValues();
            for (int j = 0; j < vals.size(); j++) {
                int val = vals.get(j);
                Expression ldc = new Expression(AstCode.LdC, val, -1);
                Expression eq = new Expression(AstCode.CmpEq, null, -1, condition, ldc);
                curCondBlock.setExpression(eq);
                BasicBlock eqBlock = register(curCondBlock);
                if (j < vals.size() - 1 || i < caseBlocks.size() - 1 && !caseBlocks.get(i + 1).isDefault()) {
                    nextCondBlock = new BasicBlock();
                } else {
                    nextCondBlock = nextCaseBlock;
                }
                eqBlock.addTarget(EdgeType.TRUE, curCaseBlock);
                eqBlock.addTarget(EdgeType.FALSE, nextCondBlock);
                curCondBlock = nextCondBlock;
            }
            buildBlock(curCaseBlock, nextCaseBlock, jc, caseBlock);
            curCaseBlock = nextCaseBlock;
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
            BasicBlock leftBlock = buildExpr(entry, left, jc);
            leftBlock.addTarget(EdgeType.TRUE, rightBlock);
            leftBlock.addTarget(EdgeType.FALSE, and);
            buildExpr(rightBlock, right, jc).addTarget(EdgeType.PASS, and);
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
            buildExpr(rightBlock, right, jc).addTarget(EdgeType.PASS, or);
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
        if(expr.getOperand() instanceof Lambda) {
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
                exc = Frame.throwable;
            jc.addExceptional(block, exc);
            return null;
        }
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
            jc.addExceptional(block, Frame.nullPointerException);
            break;
        case Bind:
        case InvokeDynamic:
            jc.addExceptional(block, Frame.error);
            jc.addExceptional(block, Frame.runtimeException);
            break;
        case InitObject:
        case InvokeInterface:
        case InvokeSpecial:
        case InvokeStatic:
        case InvokeVirtual: {
            MethodReference mr = (MethodReference) expr.getOperand();
            jc.addExceptional(block, Frame.error);
            jc.addExceptional(block, Frame.runtimeException);
            MethodDefinition md = mr.resolve();
            if (md != null) {
                for (TypeReference thrownType : md.getThrownTypes()) {
                    jc.addExceptional(block, thrownType);
                }
            } else {
                jc.addExceptional(block, Frame.exception);
            }
            break;
        }
        case LoadElement:
            jc.addExceptional(block, Frame.arrayIndexOutOfBoundsException);
            jc.addExceptional(block, Frame.nullPointerException);
            break;
        case StoreElement:
            jc.addExceptional(block, Frame.arrayIndexOutOfBoundsException);
            jc.addExceptional(block, Frame.arrayStoreException);
            jc.addExceptional(block, Frame.nullPointerException);
            break;
        case __New:
        case NewArray:
        case InitArray:
        case MultiANewArray:
            jc.addExceptional(block, Frame.outOfMemoryError);
            break;
        case PutStatic:
        case GetStatic: {
            FieldReference fr = ((FieldReference) expr.getOperand());
            if (!fr.getDeclaringType().isEquivalentTo(md.getDeclaringType()))
                jc.addExceptional(block, Frame.linkageError);
            break;
        }
        case PutField:
        case GetField: {
            if (md.isStatic() || !Exprs.isThis(expr.getArguments().get(0))) {
                jc.addExceptional(block, Frame.nullPointerException);
                jc.addExceptional(block, Frame.linkageError);
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

    public BasicBlock getEntryBlock() {
        return entry;
    }

    public BasicBlock getExitBlock() {
        return exit;
    }

    public BasicBlock getFailBlock() {
        return fail;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CFG for ").append(new MemberInfo(md)).append("\n");
        for (BasicBlock bb : blocks)
            sb.append(bb);
        return sb.toString();
    }

    public static CFG build(MethodDefinition md, Block body) {
        return new CFG(md, null, body);
    }

    public enum EdgeType {
        PASS, TRUE, FALSE, FAIL;
    }

    static class BasicBlock {
        Object state, fact;
        boolean changed;
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
                throw new IllegalStateException("Expression is set twice: " + expr);
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

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[").append(getId()).append("] ").append(expr).append("\n  ");
            if (passTarget != null)
                sb.append("PASS -> [").append(passTarget.getId()).append("] ");
            if (trueTarget != null)
                sb.append("TRUE -> [").append(trueTarget.getId()).append("] ");
            if (falseTarget != null)
                sb.append("FALSE -> [").append(falseTarget.getId()).append("] ");
            if (failTargets != null)
                sb.append("FAIL -> [").append(
                    failTargets.stream().map(BasicBlock::getId).collect(Collectors.joining(","))).append("]");
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
            block.addTarget(EdgeType.FAIL, getFailBlock());
        }

        @Override
        public void addReturn(BasicBlock block) {
            block.addTarget(EdgeType.PASS, getExitBlock());
        }

        @Override
        public void addBreak(BasicBlock block, Label label) {
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
            if (label == null || labelTargets.get(label) == entry) {
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

    static class CatchJumpContext extends DelegatingJumpContext {
        private final Map<CatchBlock, BasicBlock> catches;

        CatchJumpContext(JumpContext parent, List<CatchBlock> catchBlocks) {
            super(parent);
            this.catches = catchBlocks.stream().collect(
                Collectors.toMap(Function.identity(), cb -> new BasicBlock(), (a, b) -> a, LinkedHashMap::new));
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
        private final Set<BasicBlock> targets = new HashSet<>();
        private final BasicBlock finallyEntry;
        
        FinallyJumpContext(JumpContext parent, BasicBlock finallyEntry) {
            super(parent);
            this.finallyEntry = finallyEntry;
        }
        
        private void replacePass(BasicBlock block) {
            if(block.passTarget == null)
                throw new IllegalStateException("Passtarget is null for "+block);
            targets.add(block.passTarget);
            block.passTarget = finallyEntry;
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
            block.addTarget(EdgeType.FAIL, finallyEntry);
            targets.addAll(newTargets);
        }
    }
}