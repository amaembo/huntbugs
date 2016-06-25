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
package one.util.huntbugs.detect;

import java.util.List;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Role.LocationRole;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "RedundantCode", name = "SameConditions", maxScore = 50)
@WarningDefinition(category = "RedundantCode", name = "SameConditionsExcluding", maxScore = 75)
public class ConditionChain {
    private static final LocationRole SAME_CONDITION = LocationRole.forName("SAME_CONDITION");

    @AstVisitor
    public void visit(Node node, MethodContext mc, MethodDefinition md) {
        if (node instanceof Condition) {
            Condition cond = (Condition) node;
            Expression expr = cond.getCondition();
            check(expr, cond.getTrueBlock(), mc, false);
            check(expr, cond.getFalseBlock(), mc, true);
        }
        if (node instanceof Expression) {
            Expression expr = (Expression) node;
            if (expr.getCode() == AstCode.LogicalAnd || expr.getCode() == AstCode.LogicalOr) {
                check(expr.getArguments().get(0), expr.getArguments().get(1), expr.getCode(), mc);
            }
        }
    }

    private void check(Expression left, Expression right, AstCode condCode, MethodContext mc) {
        if (Nodes.isEquivalent(left, right)) {
            mc.report("SameConditions", 0, left, SAME_CONDITION.create(mc, right), Roles.EXPRESSION.create(left));
        } else if (left.getCode() == condCode && Nodes.isSideEffectFree(right)) {
            if (Nodes.isSideEffectFree(left))
                check(left.getArguments().get(0), right, condCode, mc);
            check(left.getArguments().get(1), right, condCode, mc);
        }
    }

    private void check(Expression expr, Block block, MethodContext mc, boolean excluding) {
        List<Node> body = block.getBody();
        if (body.isEmpty())
            return;
        Node node = body.get(0);
        if (node instanceof Condition) {
            Condition condNode = (Condition) node;
            Expression condition = condNode.getCondition();
            if (Nodes.isEquivalent(expr, condition)) {
                int priority = 0;
                if (Nodes.isEmptyOrBreak(condNode.getTrueBlock())) {
                    excluding = !excluding;
                    priority = 10;
                }
                mc.report(excluding ? "SameConditionsExcluding" : "SameConditions", priority, expr, Roles.EXPRESSION
                        .create(condition), SAME_CONDITION.create(mc, condition));
                return;
            }
            if (expr.getCode() == AstCode.LogicalAnd && !excluding && Nodes.isSideEffectFree(expr)) {
                check(expr.getArguments().get(0), block, mc, excluding);
                check(expr.getArguments().get(1), block, mc, excluding);
            }
            if (expr.getCode() == AstCode.LogicalOr && excluding && Nodes.isSideEffectFree(expr)) {
                check(expr.getArguments().get(0), block, mc, excluding);
                check(expr.getArguments().get(1), block, mc, excluding);
            }
            if (Nodes.isSideEffectFree(condition)) {
                check(expr, condNode.getTrueBlock(), mc, excluding);
                check(expr, condNode.getFalseBlock(), mc, excluding);
            }
        }
    }
}
