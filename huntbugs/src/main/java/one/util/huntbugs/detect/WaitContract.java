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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Loop;
import com.strobel.decompiler.ast.LoopType;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "Multithreading", name = "WaitUnconditional", maxScore = 65)
@WarningDefinition(category = "Multithreading", name = "WaitNotInLoop", maxScore = 65)
@WarningDefinition(category = "Multithreading", name = "NotifyNaked", maxScore = 50)
public class WaitContract {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain parents, MethodContext mc) {
        if (expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if (mr.getName().equals("wait") && Types.isObject(mr.getDeclaringType())) {
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
                            : 15, expr.getArguments().get(0), WarningAnnotation.forMember("CALLED_METHOD", mr));
                }
            }
            if((mr.getName().equals("notify") || mr.getName().equals("notifyAll")) && mr.getSignature().equals("()V")) {
                if(parents.getNode() instanceof Block) {
                    List<Node> body = ((Block) parents.getNode()).getBody();
                    if (!body.isEmpty() && body.get(0) == expr
                        && (body.size() == 1 || body.size() == 2 && Nodes.isOp(body.get(1), AstCode.MonitorExit))
                        && parents.getParent() != null
                        && Nodes.isSynchorizedBlock(parents.getParent().getNode())
                        && !parents.getParent().getParent().isSynchronized()) {
                        mc.report("NotifyNaked", 0, expr.getArguments().get(0), WarningAnnotation.forMember("CALLED_METHOD", mr));
                    }
                }
            }
        }
    }
}
