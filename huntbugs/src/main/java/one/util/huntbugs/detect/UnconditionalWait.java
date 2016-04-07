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
import com.strobel.decompiler.ast.Loop;
import com.strobel.decompiler.ast.LoopType;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Multithreading", name = "WaitUnconditional", baseScore = 65)
@WarningDefinition(category = "Multithreading", name = "WaitNotInLoop", baseScore = 65)
public class UnconditionalWait {
    @AstExpressionVisitor
    public void visit(Expression expr, NodeChain parents, MethodContext mc) {
        if (expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if (mr.getName().equals("wait") && mr.getDeclaringType().getInternalName().equals("java/lang/Object")) {
                NodeChain cur = parents;
                boolean sawCondition = false;
                boolean sawLoop = false;
                boolean sawSynchronizedBlock = false;
                while (cur != null) {
                    if (!sawSynchronizedBlock && cur.getNode() instanceof Condition) {
                        sawCondition = true;
                    }
                    if (cur.getNode() instanceof Loop) {
                        sawLoop = true;
                        if (!sawSynchronizedBlock && ((Loop) cur.getNode()).getLoopType() == LoopType.PreCondition) {
                            sawCondition = true;
                        }
                    }
                    if (Nodes.isSynchorizedBlock(cur.getNode())) {
                        sawSynchronizedBlock = true;
                    }
                    cur = cur.getParent();
                }
                if (!sawLoop || !sawCondition) {
                    mc.report(sawCondition ? "WaitNotInLoop" : "WaitUnconditional", mr.getSignature().equals("()V") ? 0
                            : -15, expr.getArguments().get(0));
                }
            }
        }
    }
}
