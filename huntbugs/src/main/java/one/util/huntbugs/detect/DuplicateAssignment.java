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

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Role.LocationRole;

/**
 * @author lan
 *
 */
@WarningDefinition(category="RedundantCode", name="FieldDoubleAssignment", maxScore=60)
public class DuplicateAssignment {
    private static final LocationRole DUPLICATE_ASSIGNMENT_AT = LocationRole.forName("DUPLICATE_ASSIGNMENT_AT");
    
    @AstVisitor
    public void visit(Node node, NodeChain nc, MethodDefinition md, MethodContext mc) {
        if(node instanceof Block) {
            List<Node> body = ((Block) node).getBody();
            for(int i=0; i<body.size()-1; i++) {
                Node n = body.get(i);
                if(!(n instanceof Expression))
                    continue;
                Expression e = (Expression) n;
                if(e.getCode() == AstCode.PutField) {
                    Expression receiver = Nodes.getChildNoSpecial(e, 0);
                    FieldReference fr = (FieldReference) e.getOperand();
                    for(int j=i+1; j<body.size(); j++) {
                        Node n2 = body.get(j);
                        if(!(n2 instanceof Expression))
                            break;
                        Expression e2 = (Expression) n2;
                        if(e2.getCode() == AstCode.Store || e2.getCode() == AstCode.StoreElement) {
                            if(Nodes.findExpression(e2, e1 -> !Nodes.isSideEffectFree(e1)) != null)
                                break;
                            continue;
                        }
                        if(e2.getCode() != AstCode.PutField)
                            break;
                        if(!Nodes.isSideEffectFree(e2.getArguments().get(1)))
                            break;
                        if (Nodes.findExpression(Nodes.getChild(e2, 1),
                            ex -> ex.getCode() == AstCode.GetField
                                && fr.isEquivalentTo((FieldReference) ex.getOperand())
                                && Nodes.isEquivalent(Nodes.getChildNoSpecial(ex, 0), receiver)) != null)
                            break;
                        Expression receiver2 = Nodes.getChildNoSpecial(e2, 0);
                        FieldReference fr2 = (FieldReference) e2.getOperand();
                        if(fr.isEquivalentTo(fr2) && Nodes.isEquivalent(receiver, receiver2)) {
                            int priority = 0;
                            if(md.isConstructor() && nc == null && Nodes.isThis(receiver2))
                                continue;
                            mc.report("FieldDoubleAssignment", priority, e, DUPLICATE_ASSIGNMENT_AT.create(mc
                                    .getLocation(e2)));
                        }
                    }
                }
            }
        }
    }
}
