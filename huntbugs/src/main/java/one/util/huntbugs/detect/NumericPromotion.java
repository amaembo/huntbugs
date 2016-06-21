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
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Role.LocationRole;
import one.util.huntbugs.warning.Role.StringRole;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.Location;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "Correctness", name = "IntegerMultiplicationPromotedToLong", maxScore = 65)
@WarningDefinition(category = "Correctness", name = "IntegerDivisionPromotedToFloat", maxScore = 65)
@WarningDefinition(category = "Correctness", name = "IntegerPromotionInCeilOrRound", maxScore = 65)
public class NumericPromotion {
    private static final StringRole SOURCE_TYPE = StringRole.forName("SOURCE_TYPE");
    private static final StringRole TARGET_TYPE = StringRole.forName("TARGET_TYPE");
    private static final LocationRole DIVISION_AT = LocationRole.forName("DIVISION_AT");
    
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc, MethodDefinition md) {
        if (expr.getCode() == AstCode.I2L) {
            Expression arg = expr.getArguments().get(0);
            if (arg.getCode() == AstCode.Mul) {
                BigInteger res = getMultiplicationConstant(arg);
                int priority = 0;
                if (Methods.isHashCodeMethod(md))
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
                        mc.report("IntegerMultiplicationPromotedToLong", priority, expr, Roles.NUMBER.create(res));
                    }
                } catch (ArithmeticException e) {
                    mc.report("IntegerMultiplicationPromotedToLong", priority, expr, Roles.NUMBER.create(res));
                }
            }
        }
        if (Nodes.isToFloatingPointConversion(expr)) {
            if (Nodes.isOp(nc.getNode(), AstCode.InvokeStatic)) {
                MethodReference mr = (MethodReference) ((Expression) nc.getNode()).getOperand();
                if (mr.getDeclaringType().getInternalName().equals("java/lang/Math")
                    && (mr.getName().equals("ceil") || mr.getName().equals("round"))) {
                    mc.report("IntegerPromotionInCeilOrRound", 0, nc.getNode(), SOURCE_TYPE.create(getSourceType(expr)), 
                        TARGET_TYPE.create(getTargetType(expr)));
                    return;
                }
            }
            Expression arg = ValuesFlow.getSource(expr.getArguments().get(0));
            if (arg.getCode() == AstCode.Div) {
                if(!ValuesFlow.findTransitiveUsages(arg, true).allMatch(NumericPromotion::isToFloatingPointConversion))
                    return;
                Object constant = Nodes.getConstant(arg.getArguments().get(1));
                int priority = 0;
                boolean isPowerOfTen = false;
                if (constant instanceof Number) {
                    long val = Math.abs(((Number) constant).longValue());
                    if (val >= 2 && val <= 4)
                        priority += 10;
                    else if(isPowerOfTen(val)) {
                        isPowerOfTen = true;
                        priority += 15;
                    }
                }
                if (Nodes.isOp(nc.getNode(), AstCode.Div)) {
                    Expression parent = (Expression) nc.getNode();
                    if (parent.getArguments().get(0) == expr) {
                        Object divisor = Nodes.getConstant(parent.getArguments().get(1));
                        if (divisor instanceof Number) {
                            long divisorVal = ((Number) divisor).longValue();
                            if(isPowerOfTen(divisorVal)) {
                                // some rounding like ((double)a/10)/10;
                                priority += isPowerOfTen ? 50 : 10;
                            }
                            // Lower priority for scenarios like
                            // ((a*1000)/b)/10.0
                            Expression divident = arg.getArguments().get(0);
                            if (divident.getCode() == AstCode.Mul) {
                                BigInteger multiplier = getMultiplicationConstant(divident);
                                if ((multiplier.equals(BigInteger.valueOf(divisorVal * 100)) || multiplier
                                        .equals(BigInteger.valueOf(divisorVal))))
                                priority += 100;
                            }
                        }
                    }
                }
                if (ValuesFlow.findTransitiveUsages(expr, true).allMatch(Nodes::isComparison)) {
                    priority += 15;
                }
                List<WarningAnnotation<?>> anno = new ArrayList<>();
                anno.add(SOURCE_TYPE.create(getSourceType(expr)));
                anno.add(TARGET_TYPE.create(getTargetType(expr)));
                Location divLoc = mc.getLocation(arg);
                if(divLoc.getSourceLine() != mc.getLocation(expr).getSourceLine())
                    anno.add(DIVISION_AT.create(divLoc));
                Object op = expr.getArguments().get(0).getOperand();
                if(op instanceof Variable) {
                    anno.add(WarningAnnotation.forVariable((Variable)op));
                }
                mc.report("IntegerDivisionPromotedToFloat", priority, expr, anno.toArray(new WarningAnnotation[0]));
            }
        }
    }

    private boolean isPowerOfTen(long divisorVal) {
        return divisorVal == 10 || divisorVal == 100 || divisorVal == 1000 || divisorVal == 10000
            || divisorVal == 100000 || divisorVal == 1000000 || divisorVal == 10000000 || divisorVal == 100000000
            || divisorVal == 1000000000;
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
    
    private static boolean isToFloatingPointConversion(Expression expr) {
        if(Nodes.isToFloatingPointConversion(expr))
            return true;
        if(expr.getCode() == AstCode.Neg)
            return ValuesFlow.findTransitiveUsages(expr, true).allMatch(NumericPromotion::isToFloatingPointConversion);
        return false;
    }
}
