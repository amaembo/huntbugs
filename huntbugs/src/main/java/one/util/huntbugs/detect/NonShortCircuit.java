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

import com.strobel.assembler.metadata.JvmType;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;

@WarningDefinition(category = "CodeStyle", name = "NonShortCircuit", maxScore = 50)
@WarningDefinition(category = "Correctness", name = "NonShortCircuitDangerous", maxScore = 80)
public class NonShortCircuit {
    @AstExpressionVisitor
    public void visitNode(Expression node, NodeChain nc, MethodContext ctx) {
        if(node.getCode() == AstCode.And || node.getCode() == AstCode.Or) {
            if (Nodes.isOp(nc.getNode(), AstCode.Store) || Nodes.isOp(nc.getNode(), AstCode.StoreElement)
                || Nodes.isOp(nc.getNode(), AstCode.CompoundAssignment) || Nodes.isOp(nc.getNode(), AstCode.PutField)
                || Nodes.isOp(nc.getNode(), AstCode.PutStatic))
                return;
            Expression left = node.getArguments().get(0);
            Expression right = node.getArguments().get(1);
            if(left.getInferredType().getSimpleType() == JvmType.Boolean &&
                    right.getInferredType().getSimpleType() == JvmType.Boolean) {
                if(left.getCode() == AstCode.InstanceOf || Nodes.isNullCheck(left))
                    ctx.report("NonShortCircuitDangerous", 0, node);
                else if (left.getChildrenAndSelfRecursive().stream().anyMatch(
                    n -> Nodes.isInvoke(n) && !Nodes.isBoxing(n) && !Nodes.isUnboxing(n)))
                    ctx.report("NonShortCircuitDangerous", 10, node);
                else
                    ctx.report("NonShortCircuit", 0, node);
            }
        }
    }
}
