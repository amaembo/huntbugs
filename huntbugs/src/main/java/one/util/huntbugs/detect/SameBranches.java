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

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.AstNodeVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Equi;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "RedundantCode", name = "SameBranchesIf", baseScore = 60)
@WarningDefinition(category = "RedundantCode", name = "SameBranchesTernary", baseScore = 60)
@WarningDefinition(category = "RedundantCode", name = "EmptyBranch", baseScore = 25)
public class SameBranches {
    @AstNodeVisitor
    public void visit(Node node, MethodContext mc, MethodDefinition md) {
        if (node instanceof Condition) {
            Condition cond = (Condition) node;
            if (Equi.equiBlocks(cond.getTrueBlock(), cond.getFalseBlock())) {
                mc.report(cond.getTrueBlock() == null || cond.getTrueBlock().getBody().isEmpty() ? "EmptyBranch"
                        : "SameBranchesIf", 0, cond.getCondition());
            }
        }
    }

    @AstExpressionVisitor
    public void visitExpr(Expression expr, MethodContext mc) {
        if (expr.getCode() == AstCode.TernaryOp && Equi.equiExpressions(expr.getArguments().get(1), expr.getArguments().get(2))) {
            mc.report("SameBranchesTernary", 0, expr);
        }
    }
}
