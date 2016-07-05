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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.FieldContext;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.FieldVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.AccessLevel;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;

/**
 * @author shustkost
 *
 */
@WarningDefinition(category="Multithreading", name="StaticNotThreadSafeField", maxScore=60)
@WarningDefinition(category="Multithreading", name="StaticNotThreadSafeFieldInvoke", maxScore=60)
public class StaticFieldNonThreadSafe {
    private static final Set<String> DANGEROUS_METHODS = new HashSet<>(Arrays.asList("format", "add", "clear", "parse", "applyPattern"));
    
    @FieldVisitor
    public void visitField(FieldDefinition fd, FieldContext fc, TypeDefinition td) {
        if((fd.isPublic() || fd.isProtected()) && (td.isPublic() || td.isProtected()) &&
                fd.isStatic() && !fd.isEnumConstant()) {
            TypeReference fieldType = fd.getFieldType();
            if(!isNotThreadSafe(fieldType))
                return;
            fc.report("StaticNotThreadSafeField", AccessLevel.of(fd).select(0, 20, 100, 100),
                Roles.FIELD_TYPE.create(fieldType));
        }
    }

    /**
     * @param fieldType
     * @return
     */
    private boolean isNotThreadSafe(TypeReference fieldType) {
        return Types.isInstance(fieldType, "java/util/Calendar") || Types.isInstance(fieldType, "java/text/DateFormat");
    }
    
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visitCall(MethodContext mc, Expression expr, MethodDefinition md, NodeChain nc, TypeDefinition td) {
        if(expr.getCode() == AstCode.InvokeVirtual) {
            Expression target = expr.getArguments().get(0);
            if(target.getCode() != AstCode.GetStatic) {
                target = Exprs.getChild(expr, 0);
            }
            if(target.getCode() == AstCode.GetStatic) {
                FieldReference fr = (FieldReference) target.getOperand();
                if(md.isTypeInitializer() && td.isEquivalentTo(fr.getDeclaringType()))
                    return;
                MethodReference mr = (MethodReference) expr.getOperand();
                String methodName = mr.getName();
                if(methodName.startsWith("get"))
                    return;
                if(nc.isSynchronized() || Flags.testAny(md.getFlags(), Flags.SYNCHRONIZED))
                    return;
                if(!isNotThreadSafe(mr.getDeclaringType()))
                    return;
                int priority = 0;
                if(Methods.isMain(md))
                    priority += 30;
                if(!methodName.startsWith("set") && !DANGEROUS_METHODS.contains(methodName))
                    priority += 20;
                mc.report("StaticNotThreadSafeFieldInvoke", priority, expr, Roles.FIELD.create(fr));
            }
        }
    }
}
