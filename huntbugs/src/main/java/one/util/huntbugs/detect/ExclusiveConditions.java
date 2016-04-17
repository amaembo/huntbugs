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

import java.util.Objects;



import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;



import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Equi;
import one.util.huntbugs.util.Nodes;import one.util.huntbugs.warning.WarningAnnotation;


/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="AndEqualsAlwaysFalse", maxScore=70)
public class ExclusiveConditions {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.LogicalAnd) {
            if(Nodes.isSideEffectFree(expr)) {
                Expression left = expr.getArguments().get(0);
                Expression right = expr.getArguments().get(1);
                if(isEquality(left)) {
                    check(left, right, mc);
                } else if(isEquality(right)) {
                    check(right, left, mc);
                }
            }
        }
    }
    
    private boolean isEquality(Expression expr) {
        if(expr.getCode() == AstCode.CmpEq)
            return true;
        if(expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if(mr.getName().equals("equals") && mr.getSignature().equals("(Ljava/lang/Object;)Z"))
                return true;
        }
        return false;
    }

    private void check(Expression equality, Expression other, MethodContext mc) {
        Nodes.ifBinaryWithConst(equality, (arg, constant) -> {
            if(isEquality(other)) {
                Nodes.ifBinaryWithConst(other, (arg2, constant2) -> {
                    if(Equi.equiExpressions(arg, arg2) && 
                            !Objects.equals(constant, constant2)) {
                        mc.report("AndEqualsAlwaysFalse", 0, arg, new WarningAnnotation<>("CONST1", constant),
                            new WarningAnnotation<>("CONST2", constant2));
                    }
                });
            }
            if(other.getCode() == AstCode.LogicalAnd) {
                check(equality, other.getArguments().get(0), mc);
                check(equality, other.getArguments().get(1), mc);
            }
        });
    }
}
