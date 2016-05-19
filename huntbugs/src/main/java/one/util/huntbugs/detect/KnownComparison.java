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

import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Role.LocationRole;
import one.util.huntbugs.warning.Role.NumberRole;
import one.util.huntbugs.warning.Role.StringRole;
import one.util.huntbugs.warning.Roles;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "RedundantCode", name = "ResultOfComparisonIsStaticallyKnown", maxScore = 50)
@WarningDefinition(category = "RedundantCode", name = "ResultOfComparisonIsStaticallyKnownDeadCode", maxScore = 70)
public class KnownComparison {
    private static final LocationRole DEAD_CODE_LOCATION = LocationRole.forName("DEAD_CODE_LOCATION");
    private static final StringRole RESULT = StringRole.forName("RESULT");
    private static final NumberRole LEFT_OPERAND = NumberRole.forName("LEFT_OPERAND");
    private static final NumberRole RIGHT_OPERAND = NumberRole.forName("RIGHT_OPERAND");

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc) {
        if (expr.getCode().isComparison()) {
            Object result = Nodes.getConstant(expr);
            if (result instanceof Boolean && !ValuesFlow.isAssertion(expr)) {
                Object left = Nodes.getConstant(expr.getArguments().get(0));
                Object right = Nodes.getConstant(expr.getArguments().get(1));
                if (left instanceof Number && right instanceof Number) {
                    Node deadCode = getDeadCode(expr, nc, (boolean) result);
                    if (deadCode == null) {
                        mc.report("ResultOfComparisonIsStaticallyKnown", 0, expr, LEFT_OPERAND.create((Number) left),
                            RIGHT_OPERAND.create((Number) right), Roles.OPERATION.create(expr), RESULT.create(result
                                    .toString()));
                    } else if(!Nodes.isThrow(deadCode)) {
                        mc.report("ResultOfComparisonIsStaticallyKnownDeadCode", 0, expr, LEFT_OPERAND.create(
                            (Number) left), RIGHT_OPERAND.create((Number) right), Roles.OPERATION.create(expr),
                            DEAD_CODE_LOCATION.create(mc, deadCode), RESULT.create(result.toString()));
                    }
                }
            }
        }
    }

    private Node getDeadCode(Expression expr, NodeChain nc, boolean result) {
        Node parent = nc.getNode();
        if (parent instanceof Condition) {
            Block block = result ? ((Condition) parent).getFalseBlock() : ((Condition) parent).getTrueBlock();
            return Nodes.isEmptyOrBreak(block) ? null : block;
        }
        if (parent instanceof Expression) {
            Expression parentExpr = (Expression) parent;
            if (parentExpr.getCode() == AstCode.LogicalNot) {
                return getDeadCode(parentExpr, nc.getParent(), !result);
            }
            if (parentExpr.getCode() == AstCode.LogicalOr) {
                if (parentExpr.getArguments().get(0) == expr) {
                    return result ? parentExpr.getArguments().get(1) : null;
                }
                if (parentExpr.getArguments().get(1) == expr) {
                    return result ? getDeadCode(parentExpr, nc.getParent(), true) : null;
                }
            }
            if (parentExpr.getCode() == AstCode.LogicalAnd) {
                if (parentExpr.getArguments().get(0) == expr) {
                    return result ? null : parentExpr.getArguments().get(1);
                }
                if (parentExpr.getArguments().get(1) == expr) {
                    return result ? null : getDeadCode(parentExpr, nc.getParent(), false);
                }
            }
            if (parentExpr.getCode() == AstCode.TernaryOp) {
                if (parentExpr.getArguments().get(0) == expr) {
                    return result ? parentExpr.getArguments().get(2) : parentExpr.getArguments().get(1);
                }
            }
        }
        return null;
    }
}
