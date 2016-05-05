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

import java.lang.annotation.RetentionPolicy;

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.db.DeclaredAnnotations;
import one.util.huntbugs.db.DeclaredAnnotations.DeclaredAnnotation;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="AnnotationNoRuntimeRetention", maxScore=75)
public class NoRuntimeRetention {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc, DeclaredAnnotations da) {
        if(expr.getCode() == AstCode.InvokeVirtual && expr.getArguments().size() == 2) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if ((mr.getDeclaringType().getInternalName().startsWith("java/lang/reflect/") || mr.getDeclaringType()
                    .getInternalName().equals("java/lang/Class"))
                && mr.getName().contains("Annotation")) {
                Object constant = Nodes.getConstant(expr.getArguments().get(1));
                if(constant instanceof TypeReference) {
                    TypeReference tr = (TypeReference)constant;
                    DeclaredAnnotation annot = da.get(tr);
                    if(annot != null && annot.getPolicy() != RetentionPolicy.RUNTIME) {
                        mc.report("AnnotationNoRuntimeRetention", 0, expr, WarningAnnotation.forType("ANNOTATION", tr));
                    }
                }
            }
        }
    }
}
