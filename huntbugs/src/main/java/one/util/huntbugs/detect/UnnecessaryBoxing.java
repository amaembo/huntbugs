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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.Location;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Performance", name = "BoxedForToString", maxScore = 30)
@WarningDefinition(category = "Performance", name = "BoxedForUnboxing", maxScore = 30)
@WarningDefinition(category = "Performance", name = "UnboxedForBoxing", maxScore = 45)
public class UnnecessaryBoxing {
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if (Nodes.isUnboxing(expr)) {
            Expression arg = expr.getArguments().get(0);
            List<Expression> usages = ValuesFlow.findTransitiveUsages(expr, false).collect(Collectors.toList());
            if (!usages.isEmpty()) {
                TypeReference type = ((MethodReference) expr.getOperand()).getDeclaringType();
                List<WarningAnnotation<?>> annotations = usages.stream().filter(
                    e -> isBoxing(e) && e.getInferredType().isEquivalentTo(arg.getInferredType())).map(
                    e -> WarningAnnotation.forLocation("BOXED_AT", mc.getLocation(e))).collect(Collectors.toList());
                if (annotations.size() == usages.size()) {
                    annotations.add(WarningAnnotation.forType("BOXED_TYPE", type));
                    JvmType simpleType = expr.getInferredType().getSimpleType();
                    mc.report("UnboxedForBoxing", simpleType == JvmType.Boolean || simpleType == JvmType.Byte ? 15 : 0,
                        arg, annotations.toArray(new WarningAnnotation<?>[0]));
                }
            }
        } else if (isBoxing(expr)) {
            Expression arg = expr.getArguments().get(0);
            List<Expression> usages = ValuesFlow.findTransitiveUsages(expr, false).collect(Collectors.toList());
            if (!usages.isEmpty()) {
                if (usages.stream().allMatch(ex -> Nodes.isUnboxing(ex) || isBoxedToString(ex))) {
                    TypeReference type = ((MethodReference) expr.getOperand()).getDeclaringType();
                    Map<Boolean, List<Expression>> map = usages.stream().collect(
                        Collectors.partitioningBy(Nodes::isUnboxing));
                    if (!map.get(true).isEmpty()) {
                        List<WarningAnnotation<?>> annotations = getUsedLocations(arg, mc, map.get(true));
                        annotations.add(WarningAnnotation.forType("BOXED_TYPE", type));
                        mc.report("BoxedForUnboxing", 0, arg, annotations.toArray(new WarningAnnotation<?>[0]));
                    }
                    if (!map.get(false).isEmpty()) {
                        List<WarningAnnotation<?>> annotations = getUsedLocations(arg, mc, map.get(false));
                        annotations.add(WarningAnnotation.forType("BOXED_TYPE", type));
                        annotations.add(WarningAnnotation.forMember("REPLACEMENT", "java/lang/String", "valueOf", "("
                            + arg.getInferredType().getInternalName() + ")Ljava/lang/String;"));
                        mc.report("BoxedForToString", 0, arg, annotations.toArray(new WarningAnnotation<?>[0]));
                    }
                }
            }
        }
    }

    private List<WarningAnnotation<?>> getUsedLocations(Expression arg, MethodContext mc, List<Expression> list) {
        Location curLoc = mc.getLocation(arg);
        List<WarningAnnotation<?>> annotations = list.stream().map(mc::getLocation).filter(
            loc -> loc.getSourceLine() != curLoc.getSourceLine() && loc.getSourceLine() != -1).map(
            loc -> WarningAnnotation.forLocation("USED_AT", loc)).collect(Collectors.toList());
        return annotations;
    }

    private boolean isBoxedToString(Expression expr) {
        if (expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if (mr.getName().equals("toString") && mr.getSignature().equals("()Ljava/lang/String;")) {
                TypeReference type = mr.getDeclaringType();
                if (Types.isBoxed(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isBoxing(Expression expr) {
        if (Nodes.isBoxing(expr))
            return true;
        if (expr.getCode() == AstCode.InitObject) {
            MethodReference ctor = (MethodReference) expr.getOperand();
            // like "(I)V"
            return Types.isBoxed(ctor.getDeclaringType()) && ctor.getSignature().length() == 4;
        }
        return false;
    }
}
