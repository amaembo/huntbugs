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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;

/**
 * @author lan
 *
 */
@WarningDefinition(category="RedundantCode", name="RedundantStreamForEach", maxScore=50)
@WarningDefinition(category="RedundantCode", name="RedundantStreamFind", maxScore=48)
public class RedundantStreamCalls {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS, minVersion=8)
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.InvokeInterface || expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if(isStreamForEach(mr)) {
                Expression stream = Nodes.getChild(expr, 0);
                if(stream.getCode() == AstCode.InvokeInterface || stream.getCode() == AstCode.InvokeVirtual) {
                    MethodReference mr2 = (MethodReference) stream.getOperand();
                    if(isCollectionStream(mr2)) {
                        mc.report("RedundantStreamForEach", 0, expr, Roles.REPLACEMENT_METHOD.create(mr2
                                .getDeclaringType().getInternalName(), "forEach", "(Ljava/util/function/Consumer;)V"));
                    }
                }
            }
            if(isOptionalIsPresent(mr)) {
                Expression opt = expr.getArguments().get(0);
                if(opt.getCode() == AstCode.InvokeInterface || opt.getCode() == AstCode.InvokeVirtual) {
                    MethodReference mr2 = (MethodReference) opt.getOperand();
                    if(isStreamFind(mr2)) {
                        Expression stream = Nodes.getChild(opt, 0);
                        MethodReference mr3 = (MethodReference) stream.getOperand();
                        if(isStreamFilter(mr3)) {
                            mc.report("RedundantStreamFind", 0, expr, Roles.REPLACEMENT_METHOD.create(mr3.getDeclaringType().getInternalName(), "anyMatch",
                                "(L"+mr3.getParameters().get(0).getParameterType().getInternalName()+";)Z"));
                        }
                    }
                }
            }
        }
    }

    private boolean isCollectionStream(MethodReference mr) {
        return mr.getName().equals("stream") && mr.getParameters().isEmpty()
            && Types.isCollection(mr.getDeclaringType());
    }

    private boolean isStreamForEach(MethodReference mr) {
        return (mr.getName().equals("forEach") || mr.getName().equals("forEachOrdered"))
                && mr.getErasedSignature().equals("(Ljava/util/function/Consumer;)V")
                && Types.isStream(mr.getDeclaringType());
    }

    private boolean isOptionalIsPresent(MethodReference mr) {
        return mr.getName().equals("isPresent") && mr.getDeclaringType().getInternalName().startsWith("java/util/Optional");
    }
    
    private boolean isStreamFind(MethodReference mr) {
        return (mr.getName().equals("findFirst") || mr.getName().equals("findAny")) &&
                mr.getErasedSignature().startsWith("()Ljava/util/Optional") && Types.isBaseStream(mr.getDeclaringType());
    }
    
    private boolean isStreamFilter(MethodReference mr) {
        if(!mr.getName().equals("filter") || mr.getParameters().size() != 1)
            return false;
        TypeReference type = mr.getParameters().get(0).getParameterType();
        return type.getSimpleName().endsWith("Predicate") && type.getPackageName().equals("java.util.function")
                && Types.isBaseStream(mr.getDeclaringType());
    }
}
