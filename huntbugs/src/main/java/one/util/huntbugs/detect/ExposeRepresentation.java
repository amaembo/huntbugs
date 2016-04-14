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

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "MaliciousCode", name = "ExposeMutableFieldViaParameter", maxScore = 45)
@WarningDefinition(category = "MaliciousCode", name = "ExposeMutableStaticFieldViaParameter", maxScore = 55)
public class ExposeRepresentation {
    @MethodVisitor
    public boolean checkMethod(MethodDefinition md, TypeDefinition td) {
        return td.isPublic() && (md.isPublic() || md.isProtected()) && !md.getParameters().isEmpty();
    }

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc, MethodDefinition md) {
        if (!md.isStatic() && expr.getCode() == AstCode.PutField) {
            FieldDefinition fd = ((FieldReference) expr.getOperand()).resolve();
            if (fd != null && (fd.isPrivate() || fd.isPackagePrivate() || fd.isProtected())) {
                if (md.isProtected() && fd.isProtected())
                    return;
                Expression self = Nodes.getChild(expr, 0);
                if (!Nodes.isThis(self))
                    return;
                Expression value = Nodes.getChild(expr, 1);
                report(expr, mc, md, fd, value, "ExposeMutableFieldViaParameter");
            }
        }
        if (expr.getCode() == AstCode.PutStatic) {
            FieldDefinition fd = ((FieldReference) expr.getOperand()).resolve();
            if (fd != null && (fd.isPrivate() || fd.isPackagePrivate())) {
                Expression value = Nodes.getChild(expr, 0);
                report(expr, mc, md, fd, value, "ExposeMutableStaticFieldViaParameter");
            }
        }
    }
    
    private ParameterDefinition getParameter(Expression value) {
        if(value.getOperand() instanceof ParameterDefinition)
            return (ParameterDefinition)value.getOperand();
        if(value.getOperand() instanceof Variable)
            return ((Variable)value.getOperand()).getOriginalParameter();
        return null;
    }

    private void report(Expression expr, MethodContext mc, MethodDefinition md, FieldDefinition fd, Expression value,
            String type) {
        ParameterDefinition pd = getParameter(value);
        if (pd == null)
            return;
        if (!Types.isMutable(fd.getFieldType()))
            return;
        int priority = 0;
        if (md.isProtected() || fd.isProtected())
            priority += 10;
        if (md.isVarArgs() && pd.getPosition() == md.getParameters().size() - 1)
            priority += 10;
        mc.report(type, priority, expr, WarningAnnotation.forType("FIELD_TYPE", fd.getFieldType()));
    }
}
