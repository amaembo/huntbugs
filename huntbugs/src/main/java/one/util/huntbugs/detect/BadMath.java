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

import com.strobel.assembler.metadata.JvmType;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Correctness", name = "RemOne", baseScore = 70)
@WarningDefinition(category = "RedundantCode", name = "UselessOrWithZero", baseScore = 60)
@WarningDefinition(category = "RedundantCode", name = "UselessAndWithMinusOne", baseScore = 60)
public class BadMath {
    @AstExpressionVisitor
    public void visit(Expression expr, NodeChain nc, MethodContext mc) {
        if (expr.getCode() == AstCode.Rem) {
            if (isConst(expr.getArguments().get(1), 1)) {
                mc.report("RemOne", 0, expr.getArguments().get(0));
            }
        }
        if (expr.getCode() == AstCode.Or || expr.getCode() == AstCode.Xor) {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            if (left.getInferredType().getSimpleType() != JvmType.Boolean && isConst(right, 0)) {
                mc.report("UselessOrWithZero", 0, left);
            } else if (right.getInferredType().getSimpleType() != JvmType.Boolean && isConst(left, 0)
                && !isCompoundAssignment(nc, left))
                mc.report("UselessOrWithZero", 0, right);
        }
        if (expr.getCode() == AstCode.And) {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            if (isConst(right, -1)) {
                mc.report("UselessAndWithMinusOne", 0, left);
            } else if (isConst(left, -1) && !isCompoundAssignment(nc, left)) {
                mc.report("UselessAndWithMinusOne", 0, right);
            }
        }
    }

    private static boolean isConst(Expression expr, long wantedValue) {
        Object constant = Nodes.getConstant(expr);
        return (constant instanceof Integer || constant instanceof Long)
            && ((Number) constant).longValue() == wantedValue;
    }

    private static boolean isCompoundAssignment(NodeChain nc, Expression left) {
        return left.getCode() == AstCode.Load && Nodes.isOp(nc.getNode(), AstCode.Store)
            && ((Expression) nc.getNode()).getOperand() == left.getOperand();
    }
}
