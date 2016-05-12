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

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.NodeChain;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category="BadPractice", name="SwingMethodNotInSwingThread", maxScore=40)
public class SwingProblems {
    @MethodVisitor
    public boolean check(MethodDefinition md) {
        return Methods.isMain(md);
    }
    
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc) {
        if(expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if(mr.getDeclaringType().getInternalName().startsWith("javax/swing/") &&
                    (mr.getName().equals("show") && mr.getSignature().equals("()V") ||
                            mr.getName().equals("pack") && mr.getSignature().equals("()V") ||
                            mr.getName().equals("setVisible") && mr.getSignature().equals("(Z)V"))) {
                if(nc.getLambdaMethod() != null) {
                    return;
                }
                mc.report("SwingMethodNotInSwingThread", 0, expr);
            }
        }
    }
}
