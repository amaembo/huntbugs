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

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;

import one.util.huntbugs.registry.ClassContext;
import one.util.huntbugs.registry.FieldContext;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.FieldVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "CodeStyle", name = "BadNameOfMethod", maxScore = 30)
@WarningDefinition(category = "CodeStyle", name = "BadNameOfMethodSameAsConstructor", maxScore = 70)
@WarningDefinition(category = "CodeStyle", name = "BadNameOfField", maxScore = 30)
@WarningDefinition(category = "CodeStyle", name = "BadNameOfClass", maxScore = 30)
@WarningDefinition(category = "CodeStyle", name = "BadNameOfClassException", maxScore = 40)
@WarningDefinition(category = "CodeStyle", name = "BadNameOfClassSameAsSuperclass", maxScore = 45)
@WarningDefinition(category = "CodeStyle", name = "BadNameOfClassSameAsInterface", maxScore = 45)
@WarningDefinition(category = "Correctness", name = "BadNameOfMethodMistake", maxScore = 60)
public class Naming {
    @ClassVisitor
    public void visitClass(TypeDefinition td, ClassContext cc) {
        if (td.isAnonymous() || td.isSynthetic())
            return;
        String name = td.getSimpleName();
        if (Character.isLetter(name.charAt(0)) && !Character.isUpperCase(name.charAt(0)) && name.indexOf('_') == -1) {
            cc.report("BadNameOfClass", td.isPublic() ? 0 : 15);
        }
        if (name.endsWith("Exception") && !Types.isInstance(td, "java/lang/Throwable")) {
            cc.report("BadNameOfClassException", td.isPublic() ? 0 : 15);
        }
        TypeReference superClass = td.getBaseType();
        if (superClass != null && superClass.getSimpleName().equals(name)) {
            cc.report("BadNameOfClassSameAsSuperclass", td.isPublic() ? 0 : 15, Roles.SUPERCLASS.create(superClass));
        }
        for (TypeReference iface : td.getExplicitInterfaces()) {
            if (iface.getSimpleName().equals(name)) {
                cc.report("BadNameOfClassSameAsInterface", td.isPublic() ? 0 : 15, Roles.INTERFACE.create(iface));
            }
        }
    }

    @MethodVisitor
    public void visitMethod(MethodDefinition md, TypeDefinition td, MethodContext mc) {
        if (badMethodName(md.getName())) {
            if (Types.isInstance(td, "org/eclipse/osgi/util/NLS"))
                return;
            // javacc generated methods
            if (td.getName().equals("SimpleCharStream")
                && (md.getName().equals("ReInit") || md.getName().equals("BeginToken") || md.getName().equals("Done")
                    || md.getName().equals("GetSuffix") || md.getName().equals("GetImage")))
                return;
            if (td.getName().endsWith("TokenManager") && md.getName().equals("ReInit"))
                return;
            int priority = 0;
            if (!td.isPublic())
                priority += 20;
            else {
                if (td.isFinal())
                    priority += 3;
                if (md.isProtected())
                    priority += 3;
                else if (md.isPackagePrivate())
                    priority += 6;
                else if (md.isPrivate())
                    priority += 10;
            }
            mc.report("BadNameOfMethod", priority);
        }
        if (!md.isStatic() && md.isPublic()) {
            MemberInfo mi = getMistakeFix(md);
            if (mi != null) {
                mc.report("BadNameOfMethodMistake", md.isDeprecated() ? 20 : 0, Roles.REPLACEMENT_METHOD.create(mi));
            }
        }
    }

    @AstVisitor(nodes = AstNodes.ROOT)
    public void checkSameAsConstructor(Block root, MethodDefinition md, TypeDefinition td, MethodContext mc) {
        if (md.getName().equals(td.getSimpleName()) && md.getReturnType().isVoid() && !md.isDeprecated()) {
            int priority = 0;
            if (root.getBody().isEmpty()) {
                priority += 20;
            } else if (root.getBody().size() == 1 && Nodes.isOp(root.getBody().get(0), AstCode.AThrow)) {
                priority += 40;
            }
            if (td.getDeclaredMethods().stream().anyMatch(
                m -> m.isConstructor() && m.getErasedSignature().equals(md.getErasedSignature()))) {
                priority += 10;
            }
            mc.report("BadNameOfMethodSameAsConstructor", priority);
        }
    }

    @FieldVisitor
    public void visitField(FieldDefinition fd, FieldContext fc) {
        if (badFieldName(fd)) {
            int priority = 0;
            if (fd.isPrivate())
                priority += 20;
            else if (fd.isPackagePrivate())
                priority += 15;
            else if (fd.isProtected())
                priority += 10;
            fc.report("BadNameOfField", priority);
        }
    }

    private MemberInfo getMistakeFix(MethodDefinition md) {
        if (md.getName().equals("hashcode") && md.getSignature().equals("()I")) {
            return new MemberInfo("java/lang/Object", "hashCode", md.getSignature());
        }
        if (md.getName().equals("tostring") && md.getSignature().equals("()Ljava/lang/String;")) {
            return new MemberInfo("java/lang/Object", "toString", md.getSignature());
        }
        if (md.getName().equals("equal") && md.getSignature().equals("(Ljava/lang/Object;)Z")) {
            return new MemberInfo("java/lang/Object", "equals", md.getSignature());
        }
        return null;
    }

    private boolean badMethodName(String mName) {
        return mName.length() >= 2 && Character.isLetter(mName.charAt(0)) && !Character.isLowerCase(mName.charAt(0))
            && Character.isLetter(mName.charAt(1)) && Character.isLowerCase(mName.charAt(1))
            && mName.indexOf('_') == -1;
    }

    private boolean badFieldName(FieldDefinition fd) {
        String fieldName = fd.getName();
        return !fd.isFinal() && fieldName.length() > 1 && Character.isLetter(fieldName.charAt(0))
            && !Character.isLowerCase(fieldName.charAt(0)) && fieldName.indexOf('_') == -1
            && Character.isLetter(fieldName.charAt(1)) && Character.isLowerCase(fieldName.charAt(1));
    }

}
