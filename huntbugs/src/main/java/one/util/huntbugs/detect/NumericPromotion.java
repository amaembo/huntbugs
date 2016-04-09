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

import java.math.BigInteger;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Correctness", name = "IntegerMultiplicationPromotedToLong", maxScore = 65)
public class NumericPromotion {
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc, MethodDefinition md) {
        if (expr.getCode() == AstCode.I2L) {
            Expression arg = expr.getArguments().get(0);
            if (arg.getCode() == AstCode.Mul) {
                BigInteger res = getMultiplicationConstant(arg);
                int priority = 0;
                if(md.getName().equals("hashCode"))
                    priority += 30;
                try {
                    int factor = Math.abs(res.intValueExact());
                    if (factor > 1) {
                        if (factor <= 4) {
                            priority += 50;
                        } else if (factor <= 100) {
                            priority += 40;
                        } else if (factor <= 10000) {
                            priority += 30;
                        } else if (factor <= 60 * 60 * 1000) {
                            priority += 20;
                        } else
                            priority += 10;
                        mc.report("IntegerMultiplicationPromotedToLong", priority, expr, WarningAnnotation
                                .forNumber(res));
                    }
                } catch (ArithmeticException e) {
                    mc.report("IntegerMultiplicationPromotedToLong", priority, expr, WarningAnnotation.forNumber(res));
                }
            }
        }
    }

    private static BigInteger getMultiplicationConstant(Expression arg) {
        Expression left = arg.getArguments().get(0);
        Expression right = arg.getArguments().get(1);
        Object leftConst = Nodes.getConstant(left);
        Object rightConst = Nodes.getConstant(right);
        BigInteger leftOperand = BigInteger.ONE;
        BigInteger rightOperand = BigInteger.ONE;
        if (leftConst instanceof Number) {
            leftOperand = BigInteger.valueOf(((Number) leftConst).longValue());
        } else if (left.getCode() == AstCode.Mul) {
            leftOperand = getMultiplicationConstant(left);
        }
        if (rightConst instanceof Number) {
            rightOperand = BigInteger.valueOf(((Number) rightConst).longValue());
        } else if (right.getCode() == AstCode.Mul) {
            rightOperand = getMultiplicationConstant(right);
        }
        return leftOperand.multiply(rightOperand);
    }
}
