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
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.db.Mutability;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "MaliciousCode", name = "ExposeMutableFieldViaParameter", maxScore = 35)
@WarningDefinition(category = "MaliciousCode", name = "ExposeMutableStaticFieldViaParameter", maxScore = 50)
public class ExposeRepresentation {
    @ClassVisitor
    public boolean checkClass(TypeDefinition td) {
        return td.isPublic();
    }

    @MethodVisitor
    public boolean checkMethod(MethodDefinition md) {
        return (md.isPublic() || md.isProtected()) && !md.getParameters().isEmpty();
    }

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc, MethodDefinition md, Mutability m) {
        FieldDefinition fd = getField(expr, md);
        if (fd == null)
            return;
        Expression value = Exprs.getChild(expr, expr.getArguments().size() - 1);
        ParameterDefinition pd = getParameter(value);
        if (pd == null)
            return;
        if (!Types.isMutable(fd.getFieldType()) && !m.isKnownMutable(fd.getFieldType()))
            return;
        int priority = 0;
        if (md.isProtected() || fd.isProtected())
            priority += 10;
        if (md.isVarArgs() && pd.getPosition() == md.getParameters().size() - 1)
            priority += 10;
        if (nc.getParent() == null && nc.getRoot().getBody().size() == 1)
            priority += 15;
        else if (!fd.isFinal())
            priority += 3;
        String type = fd.isStatic() ? "ExposeMutableStaticFieldViaParameter" : "ExposeMutableFieldViaParameter";
        mc.report(type, priority, expr, Roles.FIELD_TYPE.create(fd.getFieldType()));
    }

    private FieldDefinition getField(Expression expr, MethodDefinition md) {
        if (!md.isStatic() && expr.getCode() == AstCode.PutField) {
            FieldDefinition fd = ((FieldReference) expr.getOperand()).resolve();
            if (fd != null && !fd.isSynthetic() && (fd.isPrivate() || fd.isPackagePrivate() || fd.isProtected())) {
                if (md.isProtected() && fd.isProtected())
                    return null;
                Expression self = Exprs.getChild(expr, 0);
                if (!Exprs.isThis(self))
                    return null;
                return fd;
            }
        }
        if (expr.getCode() == AstCode.PutStatic) {
            FieldDefinition fd = ((FieldReference) expr.getOperand()).resolve();
            if (fd != null && !fd.isSynthetic() && (fd.isPrivate() || fd.isPackagePrivate())) {
                return fd;
            }
        }
        return null;
    }

    private ParameterDefinition getParameter(Expression value) {
        if (value.getOperand() instanceof ParameterDefinition)
            return (ParameterDefinition) value.getOperand();
        if (value.getOperand() instanceof Variable)
            return ((Variable) value.getOperand()).getOriginalParameter();
        return null;
    }
}
