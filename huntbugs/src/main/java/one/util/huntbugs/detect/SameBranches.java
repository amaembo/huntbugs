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
package one.util.huntbugs.detect;

import java.util.List;

import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.CatchBlock;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Loop;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.TryCatchBlock;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.AstNodeVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "RedundantCode", name = "SameBranchesIf", baseScore = 60)
@WarningDefinition(category = "RedundantCode", name = "SameBranchesTernary", baseScore = 60)
@WarningDefinition(category = "RedundantCode", name = "EmptyCondition", baseScore = 25)
public class SameBranches {
    @AstNodeVisitor
    public void visit(Node node, MethodContext mc) {
        if (node instanceof Condition) {
            Condition cond = (Condition) node;
            if (sameBlocks(cond.getTrueBlock(), cond.getFalseBlock())) {
                mc.report(cond.getTrueBlock() == null || cond.getTrueBlock().getBody().isEmpty() ? "EmptyCondition"
                        : "SameBranchesIf", 0, cond.getCondition());
            }
        }
    }

    @AstExpressionVisitor
    public void visitExpr(Expression expr, MethodContext mc) {
        if (expr.getCode() == AstCode.TernaryOp && expr.getArguments().get(1).isEquivalentTo(expr.getArguments().get(2))) {
            mc.report("SameBranchesTernary", 0, expr);
        }
    }

    private boolean sameBlocks(Block left, Block right) {
        if (left == null)
            return right == null;
        if (right == null)
            return false;
        List<Node> leftBody = left.getBody();
        List<Node> rightBody = right.getBody();
        if (leftBody.size() != rightBody.size())
            return false;
        for (int i = 0; i < leftBody.size(); i++) {
            Node leftNode = leftBody.get(i);
            Node rightNode = rightBody.get(i);
            if (leftNode.getClass() != rightNode.getClass())
                return false;
        }
        int start = left instanceof CatchBlock ? 1 : 0;
        for (int i = start; i < leftBody.size(); i++) {
            Node leftNode = leftBody.get(i);
            Node rightNode = rightBody.get(i);
            if (leftNode instanceof Expression) { // TODO: support lambdas;
                if (!((Expression) leftNode).isEquivalentTo(((Expression) rightNode)))
                    return false;
            } else if (leftNode instanceof Condition) {
                Condition leftCond = (Condition) leftNode;
                Condition rightCond = (Condition) rightNode;
                if (!leftCond.getCondition().isEquivalentTo(rightCond.getCondition()))
                    return false;
                if (!sameBlocks(leftCond.getTrueBlock(), rightCond.getTrueBlock()))
                    return false;
                if (!sameBlocks(leftCond.getFalseBlock(), rightCond.getFalseBlock()))
                    return false;
            } else if (leftNode instanceof Loop) {
                Loop leftLoop = (Loop) leftNode;
                Loop rightLoop = (Loop) rightNode;
                if (leftLoop.getLoopType() != rightLoop.getLoopType())
                    return false;
                if (!leftLoop.getCondition().isEquivalentTo(rightLoop.getCondition()))
                    return false;
                if (!sameBlocks(leftLoop.getBody(), rightLoop.getBody()))
                    return false;
            } else if (leftNode instanceof TryCatchBlock) {
                TryCatchBlock leftTry = (TryCatchBlock) leftNode;
                TryCatchBlock rightTry = (TryCatchBlock) rightNode;
                List<CatchBlock> leftCatches = leftTry.getCatchBlocks();
                List<CatchBlock> rightCatches = rightTry.getCatchBlocks();
                if (leftCatches.size() != rightCatches.size())
                    return false;
                for (int j = 0; j < leftCatches.size(); j++) {
                    CatchBlock leftCatch = leftCatches.get(j);
                    CatchBlock rightCatch = rightCatches.get(j);
                    if (!sameType(leftCatch.getExceptionType(), rightCatch.getExceptionType()))
                        return false;
                    List<TypeReference> leftTypes = leftCatch.getCaughtTypes();
                    List<TypeReference> rightTypes = rightCatch.getCaughtTypes();
                    if (leftTypes.size() != rightTypes.size())
                        return false;
                    for (int k = 0; k < leftTypes.size(); k++) {
                        if (!sameType(leftTypes.get(k), rightTypes.get(k)))
                            return false;
                    }
                    if (!sameBlocks(leftCatch, rightCatch))
                        return false;
                }
                if (!sameBlocks(leftTry.getTryBlock(), rightTry.getTryBlock()))
                    return false;
                if (!sameBlocks(leftTry.getFinallyBlock(), rightTry.getFinallyBlock()))
                    return false;
            } else
                // TODO: support switch
                return false;
        }
        return true;
    }

    private boolean sameType(TypeReference ref1, TypeReference ref2) {
        if (ref1 == null)
            return ref2 == null;
        if (ref2 == null)
            return false;
        return ref1.getInternalName().equals(ref2.getInternalName());
    }
}
