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

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.decompiler.ast.AstCode;
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
@WarningDefinition(category="Multithreading", name="SynchronizationOnUpdatedField", maxScore=65)
public class SyncOnUpdatedField {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc) {
        if(expr.getCode() == AstCode.PutField || expr.getCode() == AstCode.PutStatic) {
            FieldReference fr = (FieldReference) expr.getOperand();
            while(nc != null) {
                Expression syncObject = nc.getSyncObject();
                if(syncObject != null) {
                    if(syncObject.getCode() == AstCode.GetField && expr.getCode() == AstCode.PutField) {
                        FieldReference sfr = (FieldReference) syncObject.getOperand();
                        if(sfr.isEquivalentTo(fr) && Nodes.isEquivalent(Nodes.getChild(expr, 0), Nodes.getChild(syncObject, 0))) {
                            mc.report("SynchronizationOnUpdatedField", 0, expr);
                        }
                    } else if(syncObject.getCode() == AstCode.GetStatic && expr.getCode() == AstCode.PutStatic) {
                        FieldReference sfr = (FieldReference) syncObject.getOperand();
                        if(sfr.isEquivalentTo(fr)) {
                            mc.report("SynchronizationOnUpdatedField", 0, expr);
                        }
                    }
                }
                nc = nc.getParent();
            }
        }
    }
}
