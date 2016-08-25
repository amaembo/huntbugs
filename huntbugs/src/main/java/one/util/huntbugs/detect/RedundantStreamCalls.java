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
import java.util.Collections;
import java.util.function.Predicate;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.Inf;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.ExpressionFormatter;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;

/**
 * @author lan
 *
 */
@WarningDefinition(category="RedundantCode", name="RedundantStreamForEach", maxScore=50)
@WarningDefinition(category="RedundantCode", name="RedundantStreamFind", maxScore=48)
@WarningDefinition(category="RedundantCode", name="RedundantCollectionStream", maxScore=48)
@WarningDefinition(category="Performance", name="StreamCountFromCollection", maxScore=60)
public class RedundantStreamCalls {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS, minVersion=8)
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.InvokeInterface || expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if(isStreamForEach(mr)) {
                Expression stream = Exprs.getChild(expr, 0);
                if(stream.getCode() == AstCode.InvokeInterface || stream.getCode() == AstCode.InvokeVirtual) {
                    MethodReference mr2 = (MethodReference) stream.getOperand();
                    if(isCollectionStream(mr2)) {
                        mc.report("RedundantStreamForEach", 0, expr, Roles.REPLACEMENT_METHOD.create(mr2
                                .getDeclaringType().getInternalName(), "forEach", "(Ljava/util/function/Consumer;)V"));
                    }
                }
            }
            if(isStreamCount(mr)) {
                Expression stream = Exprs.getChild(expr, 0);
                if(stream.getCode() == AstCode.InvokeInterface || stream.getCode() == AstCode.InvokeVirtual) {
                    MethodReference mr2 = (MethodReference) stream.getOperand();
                    if(isCollectionStream(mr2)) {
                        mc.report("StreamCountFromCollection", 0, expr, Roles.REPLACEMENT_METHOD.create(mr2
                                .getDeclaringType().getInternalName(), "size", "()I"));
                    }
                }
            }
            if(isCollectionStream(mr)) {
                Expression collection = Exprs.getChild(expr, 0);
                if(!Inf.BACKLINK.findTransitiveUsages(collection, true).allMatch(Predicate.isEqual(expr))) {
                    return;
                }
                if(collection.getCode() == AstCode.InvokeStatic) {
                    MethodReference mr2 = (MethodReference)collection.getOperand();
                    String replacement = null;
                    if(Types.is(mr2.getDeclaringType(), Collections.class)) {
                        if(mr2.getName().startsWith("singleton"))
                            replacement = "Stream.of("+ExpressionFormatter.formatExpression(collection.getArguments().get(0))+")";
                        else if(mr2.getName().startsWith("empty"))
                            replacement = "Stream.empty()";
                    } else if(Types.is(mr2.getDeclaringType(), Arrays.class)) {
                        if(mr2.getName().equals("asList"))
                            replacement = "Stream.of(...)";
                    }
                    if(replacement != null) {
                        mc.report("RedundantCollectionStream", 0, expr, Roles.REPLACEMENT_STRING.create(replacement));
                    }
                }
            }
            if(isOptionalIsPresent(mr)) {
                Expression opt = expr.getArguments().get(0);
                if(opt.getCode() == AstCode.Load && mc.isAnnotated()) {
                    opt = Exprs.getChild(expr, 0);
                    if(!Inf.BACKLINK.findTransitiveUsages(opt, true).allMatch(Predicate.isEqual(expr))) {
                        return;
                    }
                }
                if(opt.getCode() == AstCode.InvokeInterface || opt.getCode() == AstCode.InvokeVirtual) {
                    MethodReference mr2 = (MethodReference) opt.getOperand();
                    if(isStreamFind(mr2)) {
                        Expression stream = Exprs.getChild(opt, 0);
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
    
    private boolean isStreamCount(MethodReference mr) {
        return mr.getName().equals("count") && Types.isBaseStream(mr.getDeclaringType());
    }
    
    private boolean isStreamFilter(MethodReference mr) {
        if(!mr.getName().equals("filter") || mr.getParameters().size() != 1)
            return false;
        TypeReference type = mr.getParameters().get(0).getParameterType();
        return type.getSimpleName().endsWith("Predicate") && type.getPackageName().equals("java.util.function")
                && Types.isBaseStream(mr.getDeclaringType());
    }
}
