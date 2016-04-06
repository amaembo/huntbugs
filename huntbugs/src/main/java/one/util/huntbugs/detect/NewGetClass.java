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
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Performance", name="NewForGetClass", baseScore=50)
public class NewGetClass {
    @AstExpressionVisitor
    public void visit(Expression node, MethodContext ctx) {
        if(node.getCode() == AstCode.InvokeVirtual) {
            MethodReference ref = (MethodReference) node.getOperand();
            if(ref.getName().equals("getClass") && ref.getErasedSignature().equals("()Ljava/lang/Class;")
                    && node.getArguments().get(0).getCode() == AstCode.InitObject) {
                ctx.report("NewForGetClass", 0, node);
            }
        }
    }
}
