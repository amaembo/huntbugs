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
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.CodeBlock;
import one.util.huntbugs.flow.Inf;
import one.util.huntbugs.flow.Nullness;
import one.util.huntbugs.flow.CFG.EdgeType;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Role.ExpressionRole;

/**
 * @author shustkost
 *
 */
@WarningDefinition(category = "Correctness", name = "NullDereferenceGuaranteed", maxScore = 90)
@WarningDefinition(category = "Correctness", name = "NullDereferenceExceptional", maxScore = 50)
//@WarningDefinition(category = "Correctness", name = "NullDereferencePossible", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "ImpossibleInstanceOfNull", maxScore = 50)
@WarningDefinition(category = "RedundantCode", name = "RedundantNullCheck", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "RedundantNullCheckNull", maxScore = 50)
@WarningDefinition(category = "RedundantCode", name = "RedundantNullCheckDeref", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "RedundantNullCheckChecked", maxScore = 60)
public class NullCheck {
    private static final ExpressionRole NONNULL_EXPRESSION = ExpressionRole.forName("NONNULL_EXPRESSION");
    private static final ExpressionRole NULL_EXPRESSION = ExpressionRole.forName("NULL_EXPRESSION");

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        switch (expr.getCode()) {
        case MonitorEnter:
        case MonitorExit:
        case PutField:
        case GetField:
        case LoadElement:
        case StoreElement:
        case InvokeInterface:
        case InvokeSpecial:
        case InvokeVirtual: {
            Nullness nullability = Inf.NULL.resolve(expr.getArguments().get(0));
            switch (nullability) {
            case NULL:
                mc.report("NullDereferenceGuaranteed", 0, expr, Roles.EXPRESSION.create(expr), NULL_EXPRESSION.create(
                    expr.getArguments().get(0)));
                return;
            case NULL_EXCEPTIONAL:
                mc.report("NullDereferenceExceptional", 0, expr, Roles.EXPRESSION.create(expr), NULL_EXPRESSION.create(
                    expr.getArguments().get(0)));
                return;
/*            case NULLABLE:
                mc.report("NullDereferencePossible", 0, expr, Roles.EXPRESSION.create(expr), NULL_EXPRESSION.create(expr
                        .getArguments().get(0)));
                return;*/
            default:
            }
            break;
        }
        case InstanceOf: {
            Nullness nullability = Inf.NULL.resolve(expr.getArguments().get(0));
            if (nullability == Nullness.NULL) {
                CodeBlock deadCode = mc.findDeadCode(expr, EdgeType.TRUE);
                if (deadCode != null) {
                    mc.report("ImpossibleInstanceOfNull", deadCode.isExceptional ? 45 : 0, expr.getArguments().get(0),
                        Roles.DEAD_CODE_LOCATION.create(mc, deadCode.startExpr), Roles.EXPRESSION.create(expr),
                        NULL_EXPRESSION.create(expr.getArguments().get(0)));
                } else {
                    mc.report("ImpossibleInstanceOfNull", 20, expr.getArguments().get(0), Roles.EXPRESSION.create(expr),
                        NULL_EXPRESSION.create(expr.getArguments().get(0)));
                }
            }
            break;
        }
        case CmpNe:
        case CmpEq: {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            Nullness leftNull = Inf.NULL.resolve(left);
            Nullness rightNull = Inf.NULL.resolve(right);
            Expression nullExpr = null;
            Expression nonNullExpr = null;
            Nullness nonNull = null;
            if (leftNull == Nullness.NULL && rightNull.isNonNull()) {
                nullExpr = left;
                nonNullExpr = right;
                nonNull = rightNull;
            } else if (rightNull == Nullness.NULL && leftNull.isNonNull()) {
                nullExpr = right;
                nonNullExpr = left;
                nonNull = leftNull;
            }
            if (nullExpr != null) {
                CodeBlock deadCode = mc.findDeadCode(expr, expr.getCode() == AstCode.CmpEq ? EdgeType.TRUE
                        : EdgeType.FALSE);
                String type = "RedundantNullCheck";
                if (nonNull == Nullness.NONNULL_CHECKED)
                    type = "RedundantNullCheckChecked";
                else if (nonNull == Nullness.NONNULL_DEREF)
                    type = "RedundantNullCheckDeref";
                if (deadCode != null) {
                    mc.report(type, deadCode.isExceptional ? 45 : 0, expr, Roles.DEAD_CODE_LOCATION.create(mc,
                        deadCode.startExpr), Roles.EXPRESSION.create(expr), NONNULL_EXPRESSION.create(nonNullExpr));
                } else {
                    mc.report(type, 30, expr, Roles.EXPRESSION.create(expr), NONNULL_EXPRESSION.create(nonNullExpr));
                }
            }
            if (leftNull == Nullness.NULL && rightNull == Nullness.NULL) {
                nullExpr = right.getCode() == AstCode.AConstNull ? left : right;
                CodeBlock deadCode = mc.findDeadCode(expr, expr.getCode() == AstCode.CmpEq ? EdgeType.FALSE
                        : EdgeType.TRUE);
                if (deadCode != null) {
                    mc.report("RedundantNullCheckNull", deadCode.isExceptional ? 45 : 0, expr, Roles.DEAD_CODE_LOCATION.create(mc,
                        deadCode.startExpr), Roles.EXPRESSION.create(expr), NULL_EXPRESSION.create(nullExpr));
                } else {
                    mc.report("RedundantNullCheckNull", 20, expr, Roles.EXPRESSION.create(expr), NULL_EXPRESSION.create(nullExpr));
                }
            }
            break;
        }
        default:
            break;
        }
    }
}
