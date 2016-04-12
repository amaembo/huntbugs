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
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="ImpossibleToArrayDowncast", maxScore=65)
public class ToArrayDowncast {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.CheckCast) {
            Expression arg = Nodes.getChild(expr, 0);
            if(arg.getCode() == AstCode.InvokeVirtual || arg.getCode() == AstCode.InvokeInterface) {
                MethodReference mr = (MethodReference) arg.getOperand();
                if(mr.getName().equals("toArray") && mr.getSignature().equals("()[Ljava/lang/Object;")) {
                    Expression target = Nodes.getChild(arg, 0);
                    if(Types.isInstance(target.getInferredType(), "java/util/Collection")) {
                        mc.report("ImpossibleToArrayDowncast", 0, target);
                    }
                }
            }
        }
    }
}
