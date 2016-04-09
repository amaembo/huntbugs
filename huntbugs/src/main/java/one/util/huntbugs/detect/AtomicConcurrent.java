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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Multithreading", name = "NonAtomicOperationOnConcurrentMap", maxScore = 70)
public class AtomicConcurrent {
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc) {
        if (expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            String typeName = mr.getDeclaringType().getInternalName();
            if (mr.getName().equals("put")
                && (typeName.equals("java/util/concurrent/ConcurrentHashMap") || typeName
                        .equals("java/util/concurrent/ConcurrentSkipListMap"))) {
                Expression self = expr.getArguments().get(0);
                Expression key = expr.getArguments().get(1);
                while (nc != null) {
                    if (nc.getNode() instanceof Condition) {
                        Expression cond = ((Condition) nc.getNode()).getCondition();
                        if (Nodes.findExpression(cond, child -> isGetOrContains(self, key, child)) != null) {
                            mc.report("NonAtomicOperationOnConcurrentMap", 0, self);
                            return;
                        } else if (Nodes.findExpressionWithSources(cond, child -> isGetOrContains(self, key, child)) != null) {
                            mc.report("NonAtomicOperationOnConcurrentMap", 10, self);
                            return;
                        }
                    }
                    nc = nc.getParent();
                }
                if (Nodes.findExpression(expr.getArguments().get(2), child -> isGetOrContains(self, key, child)) != null) {
                    mc.report("NonAtomicOperationOnConcurrentMap", 0, self);
                    return;
                } else if (Nodes.findExpressionWithSources(expr.getArguments().get(2), child -> isGetOrContains(self,
                    key, child)) != null) {
                    mc.report("NonAtomicOperationOnConcurrentMap", 10, self);
                    return;
                }
            }
        }
    }

    private boolean isGetOrContains(Expression self, Expression key, Expression call) {
        if (call.getCode() != AstCode.InvokeVirtual)
            return false;
        MethodReference mr = (MethodReference) call.getOperand();
        if (!mr.getName().equals("containsKey") && !mr.getName().equals("get"))
            return false;
        if (!Nodes.isEquivalent(self, call.getArguments().get(0)))
            return false;
        if (!Nodes.isEquivalent(key, call.getArguments().get(1)))
            return false;
        return true;
    }
}
