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

import java.util.List;

import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.TryCatchBlock;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Multithreading", name="EmptySynchronizeBlock", maxScore=50)
public class EmptySync {
    @AstVisitor
    public void visit(Node node, MethodContext mc) {
        if(node instanceof Block) {
            List<Node> body = ((Block) node).getBody();
            for(int i=0; i<body.size()-1; i++) {
                if(Nodes.isOp(body.get(i), AstCode.MonitorEnter)) {
                    Node next = body.get(i+1);
                    if(Nodes.isOp(next, AstCode.MonitorExit))
                        // ECJ scenario: simply monitorenter/monitorexit
                        mc.report("EmptySynchronizeBlock", 0, body.get(i));
                    else if(next instanceof TryCatchBlock) {
                        TryCatchBlock tryCatch = (TryCatchBlock)next;
                        if(tryCatch.getCatchBlocks().isEmpty() && tryCatch.getTryBlock().getBody().isEmpty()) {
                            Block finallyBlock = tryCatch.getFinallyBlock();
                            if(finallyBlock.getBody().size() == 1 && Nodes.isOp(finallyBlock.getBody().get(0), AstCode.MonitorExit)) {
                                // JAVAC scenario: try-catch added
                                mc.report("EmptySynchronizeBlock", 0, body.get(i));
                            }
                        }
                    }
                }
            }
        }
    }
}
