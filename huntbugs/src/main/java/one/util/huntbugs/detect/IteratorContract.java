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

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

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
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "BadPractice", name = "IteratorHasNextCallsNext", maxScore = 70)
@WarningDefinition(category = "BadPractice", name = "IteratorNoThrow", maxScore = 60)
public class IteratorContract {
    @MethodVisitor
    public boolean check(TypeDefinition td) {
        return Types.isInstance(td, "java/util/Iterator");
    }

    @AstVisitor(nodes = AstNodes.EXPRESSIONS, methodName = "hasNext", methodSignature = "()Z")
    public void visitHasNext(Expression expr, MethodContext mc) {
        if (expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if (mr.getName().equals("next") && mr.getParameters().isEmpty() && Nodes.isThis(Nodes.getChild(expr, 0))) {
                mc.report("IteratorHasNextCallsNext", 0, expr);
            }
        }
    }

    @AstVisitor(nodes = AstNodes.ROOT, methodName = "next")
    public void visitNext(Block body, MethodContext mc, MethodDefinition md) {
        if (md.getErasedSignature().startsWith("()")) {
            AtomicBoolean sawCall = new AtomicBoolean();
            Node found = Nodes.find(body, n -> {
                if (Nodes.isOp(n, AstCode.AThrow)) {
                    Expression exc = (Expression) Nodes.getChild(n, 0);
                    if (Types.is(exc.getInferredType(), NoSuchElementException.class))
                        return true;
                }
                if (n instanceof Expression) {
                    Expression expr = (Expression) n;
                    if (expr.getCode() == AstCode.InvokeSpecial || expr.getCode() == AstCode.InvokeInterface
                        || expr.getCode() == AstCode.InvokeVirtual) {
                        MethodReference mr = (MethodReference) expr.getOperand();
                        if (Nodes.isThis(Nodes.getChild(expr, 0)) || mr.getName().contains("next") || mr.getName().contains("previous"))
                            return true;
                        if (!sawCall.get() && !Nodes.isSideEffectFreeMethod(expr)) {
                            sawCall.set(true);
                        }
                    }
                }
                return false;
            });
            if (found != null)
                return;
            mc.report("IteratorNoThrow", sawCall.get() ? 30 : 0, body);
        }
    }
}
