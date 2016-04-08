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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.CaseBlock;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Switch;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.AstNodeVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Equi;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.Location;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "RedundantCode", name = "SameBranchesIf", baseScore = 70)
@WarningDefinition(category = "RedundantCode", name = "SameBranchesTernary", baseScore = 70)
@WarningDefinition(category = "RedundantCode", name = "SameBranchesSwitch", baseScore = 70)
@WarningDefinition(category = "RedundantCode", name = "SameBranchesSwitchDefault", baseScore = 70)
@WarningDefinition(category = "RedundantCode", name = "EmptyBranch", baseScore = 25)
public class SameBranches {
    @AstNodeVisitor
    public void visit(Node node, MethodContext mc, MethodDefinition md) {
        if (node instanceof Condition) {
            Condition cond = (Condition) node;
            if (Equi.equiBlocks(cond.getTrueBlock(), cond.getFalseBlock())) {
                if (cond.getTrueBlock() == null || cond.getTrueBlock().getBody().isEmpty()) {
                    mc.report("EmptyBranch", 0, cond.getCondition());

                } else {
                    mc.report("SameBranchesIf", computeScore(cond.getTrueBlock(), 2), cond.getTrueBlock(), new WarningAnnotation<>("SAME_BRANCH", mc
                            .getLocation(cond.getFalseBlock())));
                }
            }
        }
        if (node instanceof Switch) {
            Switch sw = (Switch) node;
            List<CaseBlock> blocks = sw.getCaseBlocks().stream().filter(cb -> nonFallThrough(cb.getBody())).collect(
                Collectors.toList());
            BitSet marked = new BitSet();
            boolean hasDefault = false;
            List<WarningAnnotation<Location>> eqLocations = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++) {
                if (marked.get(i))
                    continue;
                for (int j = i + 1; j < blocks.size(); j++) {
                    if (Equi.equiBlocks(blocks.get(i), blocks.get(j))) {
                        marked.set(j);
                        String role = "SAME_BRANCH";
                        if(blocks.get(j).isDefault()) {
                            role = "DEFAULT_BRANCH";
                            hasDefault = true;
                        }
                        eqLocations.add(new WarningAnnotation<>(role, mc.getLocation(blocks.get(j))));
                    }
                }
                if (!eqLocations.isEmpty()) {
                    int n = eqLocations.size() + (hasDefault ? 0 : 1);
                    if (n > 3)
                        n = (n - 3) / 2 + 3;
                    CaseBlock block = blocks.get(i);
                    mc.report(hasDefault ? "SameBranchesSwitchDefault" : "SameBranchesSwitch", computeScore(block, n), block,
                        eqLocations.toArray(new WarningAnnotation[0]));
                    eqLocations.clear();
                    hasDefault = false;
                }
            }
        }
    }

    @AstExpressionVisitor
    public void visitExpr(Expression expr, MethodContext mc) {
        if (expr.getCode() == AstCode.TernaryOp
            && Equi.equiExpressions(expr.getArguments().get(1), expr.getArguments().get(2))) {
            mc.report("SameBranchesTernary", computeScore(expr.getArguments().get(1), 30), expr);
        }
    }

    private int computeScore(Node block, int n) {
        int codeSize = block.getChildrenAndSelfRecursive().size();
        return Math.min(0, ((int) (Math.sqrt(n * codeSize) * 5) - 55));
    }

    // This check is not complete: it's still possible that it will return false for nonFallThrough
    private boolean nonFallThrough(List<Node> body) {
        if (body.isEmpty())
            return false;
        Node last = body.get(body.size() - 1);
        return Nodes.isOp(last, AstCode.LoopOrSwitchBreak) || Nodes.isOp(last, AstCode.Return)
            || Nodes.isOp(last, AstCode.LoopContinue) || Nodes.isOp(last, AstCode.AThrow);
    }
}
