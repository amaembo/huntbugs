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

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.TryCatchBlock;

import one.util.huntbugs.db.Hierarchy.TypeHierarchy;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Multithreading", name="SyncOnGetClass", maxScore=65)
public class SyncGetClass {
    @MethodVisitor
    public boolean checkMethod(MethodDefinition md, TypeDefinition td) {
        return !td.isFinal() && !md.isStatic();
    }
    
    @AstVisitor
    public void visit(Node node, MethodContext mc, TypeHierarchy th, TypeDefinition td) {
        if(node instanceof TryCatchBlock) {
            Expression syncObject = Nodes.getSyncObject((TryCatchBlock) node);
            if(syncObject != null) {
                if(syncObject.getCode() == AstCode.InvokeVirtual && Methods.isGetClass((MethodReference) syncObject.getOperand())
                        && Nodes.isThis(Nodes.getChild(syncObject, 0))) {
                    int priority = 0;
                    if(th != null && !th.hasSubClasses())
                        priority += 10;
                    if(Nodes.find(node, n -> isStaticFieldAccess(n, td)) == null)
                        priority += 15;
                    mc.report("SyncOnGetClass", priority, syncObject);
                }
            }
        }
    }

    private boolean isStaticFieldAccess(Node n, TypeDefinition td) {
        if(!Nodes.isOp(n, AstCode.GetStatic) && !Nodes.isOp(n, AstCode.PutStatic))
            return false;
        FieldReference fr = (FieldReference)((Expression)n).getOperand();
        return fr.getDeclaringType().isEquivalentTo(td);
    }
}
