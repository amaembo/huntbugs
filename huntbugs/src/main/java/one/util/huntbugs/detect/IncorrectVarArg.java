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
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="PrimitiveArrayPassedAsVarArg", maxScore=60)
public class IncorrectVarArg {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getOperand() instanceof MethodReference) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if(mr.getErasedSignature().contains("[Ljava/lang/Object;)")) {
                Expression lastArg = expr.getArguments().get(expr.getArguments().size()-1);
                if(lastArg.getCode() == AstCode.InitArray && lastArg.getArguments().size() == 1) {
                    TypeReference nested = lastArg.getArguments().get(0).getInferredType();
                    if(nested != null && nested.isArray() && nested.getElementType().isPrimitive()) {
                        int priority = 0;
                        if(!highPriority(mr)) {
                            priority = 10;
                            MethodDefinition md = mr.resolve();
                            if(md == null) {
                                priority = 20;
                            } else if(!md.isVarArgs())
                                return;
                        }
                        mc.report("PrimitiveArrayPassedAsVarArg", priority, lastArg, WarningAnnotation.forMember("CALLED_METHOD", mr),
                            WarningAnnotation.forType("ARRAY_TYPE", nested));
                    }
                }
            }
        }
    }

    private static boolean highPriority(MethodReference mr) {
        return mr.getName().equals("asList") && mr.getDeclaringType().getInternalName().equals("java/util/Arrays") ||
                mr.getName().equals("of") && mr.getDeclaringType().getInternalName().equals("java/util/stream/Stream");
    }
}
