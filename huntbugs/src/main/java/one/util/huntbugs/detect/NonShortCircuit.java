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
import java.util.stream.Collectors;

import com.strobel.assembler.metadata.JvmType;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Equi;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation;

@WarningDefinition(category = "CodeStyle", name = "NonShortCircuit", maxScore = 50)
@WarningDefinition(category = "Correctness", name = "NonShortCircuitDangerous", maxScore = 80)
public class NonShortCircuit {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visitNode(Expression node, NodeChain nc, MethodContext ctx) {
        if(node.getCode() == AstCode.And || node.getCode() == AstCode.Or) {
            if (Nodes.isOp(nc.getNode(), AstCode.Store) || Nodes.isOp(nc.getNode(), AstCode.StoreElement)
                || Nodes.isOp(nc.getNode(), AstCode.CompoundAssignment) || Nodes.isOp(nc.getNode(), AstCode.PutField)
                || Nodes.isOp(nc.getNode(), AstCode.PutStatic))
                return;
            Expression left = node.getArguments().get(0);
            Expression right = node.getArguments().get(1);
            WarningAnnotation<String> op = Roles.OPERATION.create(node);
            WarningAnnotation<String> repl = Roles.REPLACEMENT_STRING.create(op.getValue()+op.getValue());
            if(left.getInferredType().getSimpleType() == JvmType.Boolean &&
                    right.getInferredType().getSimpleType() == JvmType.Boolean) {
                if(left.getCode() == AstCode.InstanceOf || Nodes.isNullCheck(left)) {
                    Expression target = left.getArguments().get(
                        left.getCode() == AstCode.InstanceOf
                            || left.getArguments().get(1).getCode() == AstCode.AConstNull ? 0 : 1);
                    List<Expression> list = Nodes.stream(right).filter(e -> Equi.equiExpressions(e, target)).collect(Collectors.toList());
                    if(!list.isEmpty()) {
                        if(list.stream().flatMap(e -> ValuesFlow.findUsages(e).stream()).anyMatch(e -> Nodes.isInvoke(e) || e.getCode() == AstCode.GetField || e.getCode() == AstCode.CheckCast)) {
                            ctx.report("NonShortCircuitDangerous", 0, node, op, repl);
                            return;
                        }
                    }
                }
                if (Nodes.find(left, n -> Nodes.isInvoke(n) && !Nodes.isSideEffectFreeMethod(n)) != null)
                    ctx.report("NonShortCircuitDangerous", 20, node, op, repl);
                else {
                    int priority = 0;
                    if(Nodes.estimateCodeSize(node) < 4)
                        priority = 10;
                    ctx.report("NonShortCircuit", priority, node, op, repl);
                }
            }
        }
    }
}
