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

import java.util.ArrayList;
import java.util.List;

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.CodeBlock;
import one.util.huntbugs.flow.Inf;
import one.util.huntbugs.flow.CFG.EdgeType;
import one.util.huntbugs.flow.Nullness.NullState;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.Role.ExpressionRole;

/**
 * @author shustkost
 *
 */
@WarningDefinition(category = "Correctness", name = "NullDereferenceGuaranteed", maxScore = 90)
@WarningDefinition(category = "Correctness", name = "NullDereferenceExceptional", maxScore = 50)
@WarningDefinition(category = "Correctness", name = "NullDereferencePossible", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "ImpossibleInstanceOfNull", maxScore = 50)
@WarningDefinition(category = "RedundantCode", name = "RedundantNullCheck", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "RedundantNullCheckNull", maxScore = 50)
@WarningDefinition(category = "RedundantCode", name = "RedundantNullCheckDeref", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "RedundantNullCheckChecked", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "RedundantComparisonNull", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "RedundantComparisonNullNonNull", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "RedundantEqualsNullCheck", maxScore = 60)
public class NullCheck {
    private static final ExpressionRole NONNULL_EXPRESSION = ExpressionRole.forName("NONNULL_EXPRESSION");
    private static final ExpressionRole NULL_EXPRESSION = ExpressionRole.forName("NULL_EXPRESSION");

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc) {
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
            NullState nullness = Inf.NULL.resolve(expr.getArguments().get(0)).stateAt(mc.getCFG(), expr);
            String type = null;
            if(nullness == NullState.NULL) {
                type = "NullDereferenceGuaranteed";
            } else if(nullness == NullState.NULL_EXCEPTIONAL) {
                type = "NullDereferenceExceptional";
            } else if(nullness == NullState.NULLABLE) {
                type = "NullDereferencePossible";
            }
            if(type != null) {
                int priority = 0;
                if (nc.isInTry("java/lang/NullPointerException", "java/lang/RuntimeException", "java/lang/Exception",
                    "java/lang/Throwable"))
                    priority += 30;
                mc.report(type, priority, expr, Roles.EXPRESSION.create(expr), NULL_EXPRESSION.create(
                    expr.getArguments().get(0)));
                return;
            }
            if(expr.getCode() == AstCode.InvokeVirtual && Methods.isEqualsMethod((MethodReference) expr.getOperand())) {
                if(Inf.NULL.resolve(expr.getArguments().get(1)).isNull()) {
                    type = "RedundantEqualsNullCheck";
                    List<WarningAnnotation<?>> anno = new ArrayList<>();
                    anno.add(Roles.EXPRESSION.create(expr));
                    anno.add(NULL_EXPRESSION.create(expr.getArguments().get(1)));
                    CodeBlock deadCode = mc.findDeadCode(expr, EdgeType.TRUE);
                    int priority;
                    if (deadCode != null) {
                        priority = deadCode.isExceptional ? 45 : deadCode.length < 4 ? 5 : 0;
                        anno.add(Roles.DEAD_CODE_LOCATION.create(mc, deadCode.startExpr));
                    } else
                        priority = 30;
                    mc.report(type, priority, expr, anno);
                }
            }
            break;
        }
        case InstanceOf: {
            if (Inf.NULL.resolve(expr.getArguments().get(0)).isNull()) {
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
            NullState leftNull = Inf.NULL.resolve(left).stateAt(null, left);
            NullState rightNull = Inf.NULL.resolve(right).stateAt(null, right);
            Expression nullExpr = null;
            Expression nonNullExpr = null;
            NullState nonNull = null;
            if (leftNull.isNull() && rightNull.isNonNull()) {
                nullExpr = left;
                nonNullExpr = right;
                nonNull = rightNull;
            } else if (rightNull.isNull() && leftNull.isNonNull()) {
                nullExpr = right;
                nonNullExpr = left;
                nonNull = leftNull;
            }
            if (nullExpr != null) {
                String type = "RedundantNullCheck";
                if (nonNull == NullState.NONNULL_CHECKED)
                    type = "RedundantNullCheckChecked";
                else if (nonNull == NullState.NONNULL_DEREF)
                    type = "RedundantNullCheckDeref";
                List<WarningAnnotation<?>> anno = new ArrayList<>();
                anno.add(Roles.EXPRESSION.create(expr));
                anno.add(NONNULL_EXPRESSION.create(nonNullExpr));
                if (nullExpr.getCode() != AstCode.AConstNull) {
                    anno.add(NULL_EXPRESSION.create(nullExpr));
                    type = "RedundantComparisonNullNonNull";
                }
                CodeBlock deadCode = mc.findDeadCode(expr, expr.getCode() == AstCode.CmpEq ? EdgeType.TRUE
                        : EdgeType.FALSE);
                int priority;
                if (deadCode != null) {
                    priority = deadCode.isExceptional ? 45 : deadCode.length < 4 ? 5 : 0;
                    anno.add(Roles.DEAD_CODE_LOCATION.create(mc, deadCode.startExpr));
                } else
                    priority = 30;
                mc.report(type, priority, expr, anno);
            }
            if (leftNull.isNull() && rightNull.isNull()) {
                String type = "RedundantNullCheckNull";
                List<WarningAnnotation<?>> anno = new ArrayList<>();
                anno.add(Roles.EXPRESSION.create(expr));
                if (right.getCode() == AstCode.AConstNull) {
                    anno.add(NULL_EXPRESSION.create(left));
                } else if (left.getCode() == AstCode.AConstNull) {
                    anno.add(NULL_EXPRESSION.create(right));
                } else {
                    type = "RedundantComparisonNull";
                }
                int priority;
                CodeBlock deadCode = mc.findDeadCode(expr, expr.getCode() == AstCode.CmpEq ? EdgeType.FALSE
                        : EdgeType.TRUE);
                if (deadCode != null) {
                    priority = deadCode.isExceptional ? 45 : deadCode.length < 4 ? 5 : 0;
                    anno.add(Roles.DEAD_CODE_LOCATION.create(mc, deadCode.startExpr));
                } else
                    priority = 20;
                mc.report(type, priority, expr, anno);
            }
            break;
        }
        default:
            break;
        }
    }
}
