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

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodeVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category="BadPractice", name="FinalizeNullifiesSuper", baseRank = 50)
@WarningDefinition(category="BadPractice", name="FinalizeEmpty", baseRank = 35)
@WarningDefinition(category="BadPractice", name="FinalizeUselessSuper", baseRank = 40)
@WarningDefinition(category="BadPractice", name="FinalizeInvocation", baseRank = 50)
@WarningDefinition(category="MaliciousCode", name="FinalizePublic", baseRank = 60)
public class FinalizerContract {
    @AstNodeVisitor
    public boolean visitFinalizer(Node node, MethodContext mc, MethodDefinition md) {
        if(!isFinalizer(md))
            return false;
        MethodDefinition superfinalizer = getSuperfinalizer(md.getDeclaringType());
        if(md.isPublic()) {
            mc.report("FinalizePublic", 0, node);
        }
        if(node instanceof Block) {
            if(superfinalizer != null) {
                if(node.getChildren().isEmpty())
                    mc.report("FinalizeNullifiesSuper", 0, node);
                else if(node.getChildren().size() == 1) {
                    Node child = node.getChildren().get(0);
                    if(Nodes.isOp(child, AstCode.InvokeSpecial) && isFinalizer((MethodReference)(((Expression)child).getOperand()))) {
                        mc.report("FinalizeUselessSuper", 0, child);
                    }
                }
            } else {
                if(node.getChildren().isEmpty() && !md.isFinal()) {
                    mc.report("FinalizeEmpty", 0, node);
                }
            }
        }
        return false;
    }
    
    @AstNodeVisitor
    public void visit(Node node, MethodContext mc, MethodDefinition md) {
        if(Nodes.isOp(node, AstCode.InvokeVirtual) && isFinalizer((MethodReference) ((Expression)node).getOperand())) {
            mc.report("FinalizeInvocation", isFinalizer(md) ? 0 : 10, node);
        }
    }

    private static boolean isFinalizer(MethodReference mr) {
        return mr.getName().equals("finalize") && mr.getSignature().equals("()V");
    }

    private static MethodDefinition getSuperfinalizer(TypeDefinition type) {
        TypeDefinition superType = type.getBaseType().resolve();
        if(superType == null || superType.getInternalName().equals("java/lang/Object"))
            return null;
        for(MethodDefinition child : superType.getDeclaredMethods()) {
            if(isFinalizer(child))
                return child;
        }
        return getSuperfinalizer(type);
    }
}
