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

import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Role.MemberRole;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "Multithreading", name = "NonAtomicOperationOnConcurrentMap", maxScore = 70)
public class AtomicConcurrent {
    private static final MemberRole FIRST_METHOD = MemberRole.forName("FIRST_METHOD"); 
    private static final MemberRole SECOND_METHOD = MemberRole.forName("SECOND_METHOD"); 
    
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc, MethodDefinition md) {
        if (expr.getCode() == AstCode.InvokeVirtual || expr.getCode() == AstCode.InvokeInterface) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if (mr.getName().equals("put")) {
                TypeReference tr = ValuesFlow.reduceType(Exprs.getChild(expr, 0));
                String typeName = tr == null ? mr.getDeclaringType().getInternalName() : tr.getInternalName();
                if (typeName.equals("java/util/concurrent/ConcurrentHashMap") || typeName
                            .equals("java/util/concurrent/ConcurrentSkipListMap")) {
                    Expression self = expr.getArguments().get(0);
                    Expression key = expr.getArguments().get(1);
                    Expression value = expr.getArguments().get(2);
                    Expression prevCall = null;
                    int priority = 0;
                    while (prevCall == null && nc != null) {
                        if (nc.getNode() instanceof Condition) {
                            Expression cond = ((Condition) nc.getNode()).getCondition();
                            prevCall = Exprs.findExpression(cond, child -> isGetOrContains(self, key, child));
                            if (prevCall == null) {
                                prevCall = Exprs
                                        .findExpressionWithSources(cond, child -> isGetOrContains(self, key, child));
                                priority = 10;
                            }
                        }
                        nc = nc.getParent();
                    }
                    if (prevCall == null) {
                        priority = 0;
                        prevCall = Exprs.findExpression(expr.getArguments().get(2), child -> isGetOrContains(self, key,
                            child));
                        if (prevCall == null) {
                            prevCall = Exprs.findExpressionWithSources(expr.getArguments().get(2),
                                child -> isGetOrContains(self, key, child));
                            priority = 10;
                        }
                    }
                    if(nc != null && nc.isSynchronized() || Flags.testAny(md.getFlags(), Flags.SYNCHRONIZED)) {
                        priority += 40;
                    }
                    if(Types.isImmutable(value.getInferredType())) {
                        priority += 30;
                    }
                    if (prevCall != null) {
                        mc.report("NonAtomicOperationOnConcurrentMap", priority, self, FIRST_METHOD.create(
                            (MemberReference) prevCall.getOperand()), SECOND_METHOD.create(mr));
                    }
                }
            }
        }
    }

    private boolean isGetOrContains(Expression self, Expression key, Expression call) {
        if (call.getCode() != AstCode.InvokeVirtual && call.getCode() != AstCode.InvokeInterface)
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
