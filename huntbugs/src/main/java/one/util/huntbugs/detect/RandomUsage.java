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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Performance", name = "RandomNextIntViaNextDouble", maxScore = 50)
@WarningDefinition(category = "Correctness", name = "RandomDoubleToInt", maxScore = 80)
@WarningDefinition(category = "Correctness", name = "RandomUsedOnlyOnce", maxScore = 80)
public class RandomUsage {
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression node, MethodContext ctx) {
        if (node.getCode() == AstCode.D2I) {
            Expression child = Nodes.getChild(node, 0);
            if (isRandomDouble(child)) {
                ctx.report("RandomDoubleToInt", 0, child, getReplacement(((MethodReference) child.getOperand())
                        .getDeclaringType().getInternalName()));
            }
            Expression mul = node.getArguments().get(0);
            if (mul.getCode() == AstCode.Mul) {
                mul.getArguments().stream().filter(this::isRandomDouble).findFirst().ifPresent(
                    expr -> {
                        int priority = 0;
                        MethodReference mr = (MethodReference) expr.getOperand();
                        String type = mr.getDeclaringType().getInternalName();
                        if (type.equals("java/lang/Math")) {
                            priority = 20;
                        }
                        ctx.report("RandomNextIntViaNextDouble", priority, node, WarningAnnotation.forMember(
                            "CALLED_METHOD", mr), getReplacement(type));
                    });
            }
        }
        if (node.getCode() == AstCode.InvokeVirtual && node.getArguments().get(0).getCode() == AstCode.InitObject) {
            MethodReference mr = (MethodReference) node.getArguments().get(0).getOperand();
            TypeReference type = mr.getDeclaringType();
            if (Types.isRandomClass(type) && !type.getInternalName().equals("java/security/SecureRandom")) {
                ctx.report("RandomUsedOnlyOnce", 0, node, WarningAnnotation.forType("RANDOM_TYPE", type));
            }
        }
    }

    private WarningAnnotation<MemberInfo> getReplacement(String type) {
        return WarningAnnotation.forMember("REPLACEMENT", type.equals("java/lang/Math") ? "java/util/Random" : type,
            "nextInt", "(I)I");
    }

    private boolean isRandomDouble(Node node) {
        if (Nodes.isInvoke(node)) {
            MethodReference mr = (MethodReference) ((Expression) node).getOperand();
            if (mr.getSignature().equals("()D")
                && (Types.isRandomClass(mr.getDeclaringType()) && mr.getName().equals("nextDouble") || mr
                        .getDeclaringType().getInternalName().equals("java/lang/Math")
                    && mr.getName().equals("random"))) {
                return true;
            }
        }
        return false;
    }
}
