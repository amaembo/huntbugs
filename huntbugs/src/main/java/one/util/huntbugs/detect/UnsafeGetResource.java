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
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.db.Hierarchy.TypeHierarchy;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category="BadPractice", name="UnsafeGetResource", maxScore=60)
public class UnsafeGetResource {
    @MethodVisitor
    public boolean checkMethod(MethodDefinition md, TypeDefinition td) {
        return td.isPublic() && !td.isFinal() && !md.isStatic();
    }
    
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc, TypeHierarchy th) {
        if(expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference getResourceCall = (MethodReference) expr.getOperand();
            if((getResourceCall.getName().equals("getResource") || getResourceCall.getName().equals("getResourceAsStream")) &&
                    getResourceCall.getDeclaringType().getInternalName().equals("java/lang/Class")) {
                Expression classObj = Nodes.getChild(expr, 0);
                if(classObj.getCode() == AstCode.InvokeVirtual) {
                    MethodReference getClassCall = (MethodReference) classObj.getOperand();
                    if(getClassCall.getName().equals("getClass") && getClassCall.getErasedSignature().equals("()Ljava/lang/Class;")) {
                        if(Nodes.isThis(Nodes.getChild(classObj, 0))) {
                            Object resource = Nodes.getConstant(expr.getArguments().get(1));
                            int priority = 0;
                            if (resource instanceof String && ((String)resource).startsWith("/"))
                                priority += 30;
                            else {
                                if (th != null) {
                                    if (!th.hasSubClasses())
                                        priority += 20;
                                    else if (!th.hasSubClassesOutOfPackage())
                                        priority += 10;
                                }
                            }
                            mc.report("UnsafeGetResource", priority, expr);
                        }
                    }
                }
            }
        }
    }
}
