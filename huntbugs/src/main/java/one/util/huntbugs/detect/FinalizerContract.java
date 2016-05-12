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

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "BadPractice", name = "FinalizeNullifiesSuper", maxScore = 50)
@WarningDefinition(category = "BadPractice", name = "FinalizeNoSuperCall", maxScore = 45)
@WarningDefinition(category = "RedundantCode", name = "FinalizeEmpty", maxScore = 35)
@WarningDefinition(category = "RedundantCode", name = "FinalizeUselessSuper", maxScore = 40)
@WarningDefinition(category = "BadPractice", name = "FinalizeInvocation", maxScore = 60)
@WarningDefinition(category = "BadPractice", name = "FinalizeNullsFields", maxScore = 50)
@WarningDefinition(category = "BadPractice", name = "FinalizeOnlyNullsFields", maxScore = 65)
@WarningDefinition(category = "MaliciousCode", name = "FinalizePublic", maxScore = 60)
public class FinalizerContract {
    @AstVisitor(nodes = AstNodes.ROOT, methodName = "finalize", methodSignature = "()V")
    public void visitFinalizer(Block body, MethodContext mc, MethodDefinition md) {
        MethodDefinition superfinalizer = getSuperfinalizer(md.getDeclaringType());
        if (md.isPublic()) {
            mc.report("FinalizePublic", 0, body);
        }
        if (superfinalizer != null) {
            if (body.getBody().isEmpty())
                mc.report("FinalizeNullifiesSuper", 0, body, WarningAnnotation.forType("SUPER_TYPE", superfinalizer
                        .getDeclaringType()));
            else {
                if (body.getBody().size() == 1) {
                    Node child = body.getBody().get(0);
                    if (isSuperCall(child)) {
                        if (!md.isFinal()) {
                            mc.report("FinalizeUselessSuper", 0, child, WarningAnnotation.forType("SUPER_TYPE",
                                superfinalizer.getDeclaringType()));
                            return;
                        }
                    }
                }
                if(Nodes.find(body, this::isSuperCall) == null) {
                    mc.report("FinalizeNoSuperCall", 0, WarningAnnotation.forType("SUPER_TYPE", superfinalizer
                            .getDeclaringType()));
                }
            }
        } else {
            if (body.getBody().isEmpty() && !md.isFinal()) {
                mc.report("FinalizeEmpty", 0, body);
            }
        }
        boolean hasNullField = false, hasSomethingElse = false;
        for (Node node : body.getBody()) {
            if (Nodes.isOp(node, AstCode.PutField) && Nodes.isOp(Nodes.getChild(node, 1), AstCode.AConstNull))
                hasNullField = true;
            else
                hasSomethingElse = true;
        }
        if (hasNullField) {
            mc.report(hasSomethingElse ? "FinalizeNullsFields" : "FinalizeOnlyNullsFields", 0, body);
        }
    }

    private boolean isSuperCall(Node child) {
        return Nodes.isOp(child, AstCode.InvokeSpecial)
            && isFinalizer((MethodReference) (((Expression) child).getOperand()));
    }

    @AstVisitor
    public void visit(Node node, MethodContext mc, MethodDefinition md) {
        if (Nodes.isOp(node, AstCode.InvokeVirtual) && isFinalizer((MethodReference) ((Expression) node).getOperand())) {
            mc.report("FinalizeInvocation", isFinalizer(md) ? 10 : 0, node);
        }
    }

    private static boolean isFinalizer(MethodReference mr) {
        return mr.getName().equals("finalize") && mr.getSignature().equals("()V");
    }

    private static MethodDefinition getSuperfinalizer(TypeDefinition type) {
        TypeDefinition superType = type.getBaseType().resolve();
        if (superType == null || Types.isObject(superType))
            return null;
        for (MethodDefinition child : superType.getDeclaredMethods()) {
            if (isFinalizer(child))
                return child;
        }
        return getSuperfinalizer(superType);
    }
}
