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
import java.util.ArrayList;
import java.util.List;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.Location;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Correctness", name = "IntegerMultiplicationPromotedToLong", maxScore = 65)
@WarningDefinition(category = "Correctness", name = "IntegerDivisionPromotedToFloat", maxScore = 65)
@WarningDefinition(category = "Correctness", name = "IntegerPromotionInCeilOrRound", maxScore = 65)
public class NumericPromotion {
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc, MethodDefinition md) {
        if (expr.getCode() == AstCode.I2L) {
            Expression arg = expr.getArguments().get(0);
            if (arg.getCode() == AstCode.Mul) {
                BigInteger res = getMultiplicationConstant(arg);
                int priority = 0;
                if (md.getName().equals("hashCode"))
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
        if (expr.getCode() == AstCode.I2F || expr.getCode() == AstCode.I2D || expr.getCode() == AstCode.L2F
            || expr.getCode() == AstCode.L2D) {
            if (Nodes.isOp(nc.getNode(), AstCode.InvokeStatic)) {
                MethodReference mr = (MethodReference) ((Expression) nc.getNode()).getOperand();
                if (mr.getDeclaringType().getInternalName().equals("java/lang/Math")
                    && (mr.getName().equals("ceil") || mr.getName().equals("round"))) {
                    mc.report("IntegerPromotionInCeilOrRound", 0, nc.getNode(), new WarningAnnotation<>("SOURCE_TYPE",
                            getSourceType(expr)), new WarningAnnotation<>("TARGET_TYPE", getTargetType(expr)));
                    return;
                }
            }
            Expression arg = ValuesFlow.getSource(expr.getArguments().get(0));
            if (arg.getCode() == AstCode.Div) {
                Object constant = Nodes.getConstant(arg.getArguments().get(1));
                int priority = 0;
                if (constant instanceof Number) {
                    long val = Math.abs(((Number) constant).longValue());
                    if (val >= 2 && val <= 4)
                        priority += 10;
                }
                Expression divident = arg.getArguments().get(0);
                if (divident.getCode() == AstCode.Mul) {
                    BigInteger multiplier = getMultiplicationConstant(divident);
                    if (Nodes.isOp(nc.getNode(), AstCode.Div)) {
                        Expression parent = (Expression) nc.getNode();
                        if (parent.getArguments().get(0) == expr) {
                            // Lower priority for scenarios like
                            // ((a*1000)/b)/10.0
                            Object divisor = Nodes.getConstant(parent.getArguments().get(1));
                            if (divisor instanceof Number
                                && (multiplier.equals(BigInteger.valueOf(((Number) divisor).longValue() * 100)) || multiplier
                                        .equals(BigInteger.valueOf(((Number) divisor).longValue()))))
                                priority += 100;
                        }
                    }
                }
                List<WarningAnnotation<?>> anno = new ArrayList<>();
                anno.add(new WarningAnnotation<>("SOURCE_TYPE", getSourceType(expr)));
                anno.add(new WarningAnnotation<>("TARGET_TYPE", getTargetType(expr)));
                Location divLoc = mc.getLocation(arg);
                if(divLoc.getSourceLine() != mc.getLocation(expr).getSourceLine())
                    anno.add(WarningAnnotation.forLocation("DIVISION_AT", divLoc));
                Object op = expr.getArguments().get(0).getOperand();
                if(op instanceof Variable) {
                    anno.add(new WarningAnnotation<>("VARIABLE", ((Variable)op).getName()));
                }
                mc.report("IntegerDivisionPromotedToFloat", priority, expr, anno.toArray(new WarningAnnotation[0]));
            }
        }
    }

    private static String getSourceType(Expression expr) {
        switch(expr.getCode()) {
        case I2F:
        case I2D:
            return "int";
        case L2F:
        case L2D:
            return "long";
        default:
            throw new InternalError(expr.getCode().toString());
        }
    }

    private static String getTargetType(Expression expr) {
        switch(expr.getCode()) {
        case I2F:
        case L2F:
            return "float";
        case I2D:
        case L2D:
            return "double";
        default:
            throw new InternalError(expr.getCode().toString());
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
