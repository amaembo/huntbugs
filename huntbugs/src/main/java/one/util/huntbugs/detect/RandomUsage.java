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
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.flow.Inf;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.Role.TypeRole;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "Performance", name = "RandomNextIntViaNextDouble", maxScore = 50)
@WarningDefinition(category = "Correctness", name = "RandomDoubleToInt", maxScore = 80)
@WarningDefinition(category = "Correctness", name = "RandomUsedOnlyOnce", maxScore = 70)
public class RandomUsage {
    private static final TypeRole RANDOM_TYPE = TypeRole.forName("RANDOM_TYPE");

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression node, MethodContext ctx) {
        if (node.getCode() == AstCode.D2I) {
            Expression child = Exprs.getChild(node, 0);
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
                        ctx.report("RandomNextIntViaNextDouble", priority, node, Roles.CALLED_METHOD.create(mr),
                            getReplacement(type));
                    });
            }
        }
        checkOnlyOnce(node, ctx);
    }

    void checkOnlyOnce(Expression node, MethodContext ctx) {
        if (node.getCode() != AstCode.InvokeVirtual || node.getArguments().get(0).getCode() != AstCode.InitObject)
            return;
        MethodReference ctor = (MethodReference) node.getArguments().get(0).getOperand();
        TypeReference type = ctor.getDeclaringType();
        if (!Types.isRandomClass(type) || type.getInternalName().equals("java/security/SecureRandom"))
            return;
        MethodReference mr = (MethodReference) node.getOperand();
        if(mr.getReturnType().getPackageName().equals("java.util.stream"))
            return;
        if(Inf.BACKLINK.findTransitiveUsages(node, true).allMatch(this::isRandomInit))
            return;
        ctx.report("RandomUsedOnlyOnce", 0, node, RANDOM_TYPE.create(type));
    }
    
    private boolean isRandomInit(Expression expr) {
        if(expr.getCode() == AstCode.InitObject) {
            MethodReference ctor = (MethodReference) expr.getOperand();
            // It seems ok to initialize cern.jet.random generators with new Random().nextInt()
            if(ctor.getDeclaringType().getPackageName().equals("cern.jet.random.engine"))
                return true;
        }
        return false;
    }

    private WarningAnnotation<MemberInfo> getReplacement(String type) {
        return Roles.REPLACEMENT_METHOD.create(type.equals("java/lang/Math") ? "java/util/Random" : type,
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
