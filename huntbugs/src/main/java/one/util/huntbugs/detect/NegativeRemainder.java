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
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category="Correctness", name="HashCodeRemainder", maxScore=80)
@WarningDefinition(category="Correctness", name="RandomIntRemainder", maxScore=80)
public class NegativeRemainder {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        switch(expr.getCode()) {
        case StoreElement:
        case LoadElement:
            check(Nodes.getChild(expr, 1), mc);
            break;
        case InvokeInterface:
        case InvokeVirtual:
            MethodReference mr = (MethodReference) expr.getOperand();
            if(Types.isInstance(mr.getDeclaringType(), "java/util/List")) {
                if((mr.getName().equals("add") || mr.getName().equals("set")) && mr.getSignature().startsWith("(I") ||
                        (mr.getName().equals("get") || mr.getName().equals("remove")) && mr.getSignature().startsWith("(I)"))
                    check(Nodes.getChild(expr, 1), mc);
            }
            break;
        default:
        }
    }

    private void check(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.Rem) {
            Expression target = Nodes.getChild(expr, 0);
            if(target.getCode() == AstCode.InvokeVirtual) {
                MethodReference mr = (MethodReference) target.getOperand();
                if(Methods.isHashCodeMethod(mr)) {
                    mc.report("HashCodeRemainder", 0, target);
                } else if(Types.isRandomClass(mr.getDeclaringType()) && mr.getName().equals("nextInt") && mr.getSignature().equals("()I")) {
                    mc.report("RandomIntRemainder", 0, target);
                }
            }
        }
    }
}
