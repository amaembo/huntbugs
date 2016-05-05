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

import java.util.stream.StreamSupport;

import com.strobel.assembler.ir.ConstantPool.NameAndTypeDescriptorEntry;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;



import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.db.Hierarchy.TypeHierarchy;
import one.util.huntbugs.registry.ClassContext;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.VisitOrder;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

/**
 * @author lan
 *
 */
@WarningDefinition(category="BadPractice", name="CloneableDoesNotImplementClone", maxScore=50)
@WarningDefinition(category="BadPractice", name="CloneableNoSuperCall", maxScore=60)
@WarningDefinition(category="BadPractice", name="NotCloneableHasClone", maxScore=55)
public class CloneContract {
    private boolean hasClone;
    private boolean implementsCloneable;
    private boolean isCloneable;
    
    @ClassVisitor(order=VisitOrder.BEFORE)
    public boolean beforeClass(TypeDefinition td) {
        if(td.isInterface())
            return false;
        implementsCloneable = td.getExplicitInterfaces().stream().map(TypeReference::getInternalName).anyMatch(
            "java/lang/Cloneable"::equals);
        isCloneable = implementsCloneable || Types.isInstance(td, "java/lang/Cloneable");
        return true;
    }

    @AstVisitor(nodes=AstNodes.ROOT, methodName="clone")
    public void visitBody(MethodDefinition md, Block body, TypeDefinition td, MethodContext mc, TypeHierarchy th) {
        if(!md.isSynthetic() && md.getErasedSignature().startsWith("()")) {
            hasClone = true;
            boolean onlyThrows = body.getBody().size() == 1 && Nodes.isOp(body.getBody().get(0), AstCode.AThrow);
            boolean deprecated = md.isDeprecated();
            if(!isCloneable && !deprecated && !onlyThrows) {
                int priority = referencesClone(td) ? 0 : 10;
                if(td.isNonPublic())
                    priority += 20;
                mc.report("NotCloneableHasClone", priority, body);
            }
            if(isCloneable && !md.isFinal()) {
                boolean invokesSuperClone = Nodes.find(body, n -> {
                    if(Nodes.isOp(n, AstCode.InvokeSpecial)) {
                        MethodReference mr = (MethodReference) ((Expression)n).getOperand();
                        if(mr.getName().equals("clone") && mr.getErasedSignature().startsWith("()"))
                            return true;
                    }
                    return false;
                }) != null;
                if(!invokesSuperClone) {
                    int priority = 0;
                    if(!th.hasSubClasses()) {
                        priority += 10;
                        if(td.isNonPublic())
                            priority += 10;
                    }
                    if(td.getDeclaredFields().isEmpty()) {
                        priority += 10;
                    }
                    mc.report("CloneableNoSuperCall", priority, body);
                }
            }
        }
    }
    
    @ClassVisitor(order=VisitOrder.AFTER)
    public void afterClass(TypeDefinition td, ClassContext cc) {
        if(implementsCloneable && !hasClone && !referencesClone(td)) {
            cc.report("CloneableDoesNotImplementClone", td.getDeclaredFields().isEmpty() ? 10 : 0);
        }
    }

    private static boolean referencesClone(TypeDefinition td) {
        return StreamSupport.stream(td.getConstantPool().spliterator(), false)
            .anyMatch(entry -> entry instanceof NameAndTypeDescriptorEntry &&
                ((NameAndTypeDescriptorEntry)entry).getName().equals("clone") &&
                ((NameAndTypeDescriptorEntry)entry).getType().startsWith("()"));
    }
}
