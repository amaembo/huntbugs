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

import java.util.stream.StreamSupport;

import com.strobel.assembler.ir.ConstantPool.TypeInfoEntry;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "RedundantCode", name = "UselessEasyMockCall", maxScore = 50)
public class EasyMockProblems {
    @ClassVisitor
    public boolean check(TypeDefinition td) {
        return StreamSupport.stream(td.getConstantPool().spliterator(), false).anyMatch(
            entry -> entry instanceof TypeInfoEntry && ((TypeInfoEntry) entry).getName().startsWith("org/easymock/"));
    }

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if (expr.getCode() == AstCode.InvokeStatic) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if (mr.getDeclaringType().getInternalName().equals("org/easymock/EasyMock")
                && (mr.getName().equals("replay") || mr.getName().equals("verify") || mr.getName().startsWith("reset"))
                && mr.getErasedSignature().equals("([Ljava/lang/Object;)V")) {
                Expression child = Nodes.getChild(expr, 0);
                if (child.getCode() == AstCode.InitArray && child.getArguments().isEmpty()
                    || child.getCode() == AstCode.NewArray
                    && Integer.valueOf(0).equals(Nodes.getConstant(child.getArguments().get(0)))) {
                    mc.report("UselessEasyMockCall", 0, expr);
                }
            }
        }
    }
}
