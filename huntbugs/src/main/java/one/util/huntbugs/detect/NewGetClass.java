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
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodeVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Performance", name="NewForGetClass", baseRank=50)
public class NewGetClass {
    @AstNodeVisitor
    public void visit(Node node, MethodContext ctx) {
        if(Nodes.isOp(node, AstCode.InvokeVirtual)) {
            MethodReference ref = (MethodReference) ((Expression)node).getOperand();
            if(ref.getFullName().equals("java/lang/Object.getClass") && ref.getSignature().equals("()Ljava/lang/Class;")
                    && ((Expression)node).getArguments().get(0).getCode() == AstCode.InitObject) {
                ctx.report("NewForGetClass", 0, node);
            }
        }
    }
}
