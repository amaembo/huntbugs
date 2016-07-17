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

import java.util.HashSet;
import java.util.Set;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.Location;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Multithreading", name = "MutableServletField", maxScore = 40)
public class MutableServletField {
    static class MethodLocation {
        MethodDefinition md;
        Location loc;

        public MethodLocation(MethodDefinition md, Location loc) {
            this.md = md;
            this.loc = loc;
        }
        
        public WarningAnnotation<?>[] getAnnotations() {
            WarningAnnotation<?>[] anno = {Roles.METHOD.create(md), Roles.LOCATION.create(loc)};
            return anno;
        }
    }
    
    private final Set<FieldDefinition> reportedFields = new HashSet<>();
    
    @ClassVisitor
    public boolean checkClass(TypeDefinition td) {
        return Types.isInstance(td, "javax/servlet/GenericServlet") && !Types.isInstance(td, "javax/servlet/SingleThreadModel");
    }
    
    @MethodVisitor
    public boolean checkMethod(MethodDefinition md) {
        return !md.isConstructor() && !md.getName().equals("init") && !md.getName().equals("destroy") && !Flags.testAny(md.getFlags(), Flags.SYNCHRONIZED);
    }
    
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visitCode(Expression expr, NodeChain nc, MethodContext mc, MethodDefinition md, TypeDefinition td) {
        if(expr.getCode() == AstCode.PutField) {
            FieldDefinition fd = ((FieldReference) expr.getOperand()).resolve();
            if (fd.getDeclaringType().isEquivalentTo(td) && !nc.isSynchronized() && !Flags.testAny(fd.getFlags(),
                Flags.VOLATILE) && reportedFields.add(fd)) {
                mc.report("MutableServletField", 0, expr);
            }
        }
    }
}
