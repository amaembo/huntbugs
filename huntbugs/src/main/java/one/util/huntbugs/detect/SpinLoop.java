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

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.Flags;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Loop;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "Multithreading", name = "SpinLoopOnField", maxScore = 70)
public class SpinLoop {
    @AstVisitor
    public void visit(Node node, MethodContext mc) {
        if (!(node instanceof Loop))
            return;
        Loop loop = (Loop) node;
        if (!loop.getBody().getBody().isEmpty())
            return;
        Expression cond = loop.getCondition();
        if (cond == null || !Nodes.isSideEffectFree(cond))
            return;
        checkCondition(cond, mc);
    }

    private void checkCondition(Expression cond, MethodContext mc) {
        if (cond.getCode() == AstCode.LogicalAnd || cond.getCode() == AstCode.LogicalOr
            || cond.getCode() == AstCode.LogicalNot) {
            cond.getArguments().forEach(child -> checkCondition(child, mc));
        } else if (cond.getCode().isComparison()) {
            cond.getArguments().forEach(child -> checkFieldRead(child, mc));
        } else
            checkFieldRead(cond, mc);
    }

    private void checkFieldRead(Expression expr, MethodContext mc) {
        if (Nodes.isFieldRead(expr)) {
            FieldDefinition fd = ((FieldReference) expr.getOperand()).resolve();
            if (fd != null && !Flags.testAny(fd.getFlags(), Flags.VOLATILE)) {
                mc.report("SpinLoopOnField", 0, expr, WarningAnnotation.forField(fd));
            }
        }
    }
}
