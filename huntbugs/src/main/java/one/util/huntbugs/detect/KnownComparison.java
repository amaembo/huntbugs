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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import one.util.huntbugs.flow.CFG.EdgeType;
import one.util.huntbugs.flow.CodeBlock;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Role.StringRole;
import one.util.huntbugs.warning.Roles;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "RedundantCode", name = "ResultOfComparisonIsStaticallyKnown", maxScore = 50)
@WarningDefinition(category = "RedundantCode", name = "ResultOfComparisonIsStaticallyKnownDeadCode", maxScore = 70)
public class KnownComparison {
    private static final StringRole RESULT = StringRole.forName("RESULT");
    private static final StringRole LEFT_OPERAND = StringRole.forName("LEFT_OPERAND");
    private static final StringRole RIGHT_OPERAND = StringRole.forName("RIGHT_OPERAND");

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if (expr.getCode().isComparison() || (expr.getCode() == AstCode.InvokeVirtual && Methods.isEqualsMethod(
            (MethodReference) expr.getOperand()))) {
            Object result = Nodes.getConstant(expr);
            if (result instanceof Boolean && !Exprs.isAssertion(expr)) {
                Object left = Nodes.getConstant(expr.getArguments().get(0));
                Object right = Nodes.getConstant(expr.getArguments().get(1));
                if (left != null && right != null) {
                    CodeBlock deadCode = mc.findDeadCode(expr, (boolean) result ? EdgeType.FALSE : EdgeType.TRUE);
                    if (deadCode == null) {
                        mc.report("ResultOfComparisonIsStaticallyKnown", 0, expr, Roles.EXPRESSION.create(expr),
                            LEFT_OPERAND.createFromConst(left), RIGHT_OPERAND.createFromConst(right), Roles.OPERATION
                                    .create(expr), RESULT.create(result.toString()));
                    } else if (!deadCode.isExceptional) {
                        mc.report("ResultOfComparisonIsStaticallyKnownDeadCode", 0, expr,
                            Roles.EXPRESSION.create(expr), LEFT_OPERAND.createFromConst(left), RIGHT_OPERAND
                                    .createFromConst(right), Roles.OPERATION.create(expr), Roles.DEAD_CODE_LOCATION.create(
                                mc, deadCode.startExpr), RESULT.create(result.toString()));
                    }
                }
            }
        }
    }
}
