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

import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.Inf;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Role.NumberRole;
import one.util.huntbugs.warning.Roles;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "Correctness", name = "RemOne", maxScore = 80)
@WarningDefinition(category = "Correctness", name = "CompareBitAndIncompatible", maxScore = 75)
@WarningDefinition(category = "Correctness", name = "CompareBitOrIncompatible", maxScore = 75)
@WarningDefinition(category = "RedundantCode", name = "UselessOrWithZero", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "UselessAndWithMinusOne", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "UselessAndWithZero", maxScore = 70)
@WarningDefinition(category = "Correctness", name = "BitCheckGreaterNegative", maxScore = 80)
@WarningDefinition(category = "Correctness", name = "BitShiftInvalidAmount", maxScore = 75)
@WarningDefinition(category = "Correctness", name = "BitShiftWrongPriority", maxScore = 70)
@WarningDefinition(category = "BadPractice", name = "BitCheckGreater", maxScore = 35)
@WarningDefinition(category = "Correctness", name = "BitOrSignedByte", maxScore = 50)
@WarningDefinition(category = "Correctness", name = "BitAddSignedByte", maxScore = 35)
public class BadMath {
    private static final NumberRole COMPARED_TO = NumberRole.forName("COMPARED_TO");
    private static final NumberRole AND_OPERAND = NumberRole.forName("AND_OPERAND");
    private static final NumberRole OR_OPERAND = NumberRole.forName("OR_OPERAND");
    
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void checkWrongPriority(Expression expr, MethodContext mc, MethodDefinition md) {
        if(expr.getCode() == AstCode.Shl) {
            Expression leftOp = expr.getArguments().get(0);
            Expression rightOp = expr.getArguments().get(1);
            if(rightOp.getCode() == AstCode.Add) {
                Expression leftAddend = rightOp.getArguments().get(0);
                Object leftConst = Nodes.getConstant(leftAddend);
                Expression rightAddend = rightOp.getArguments().get(1);
                if(leftConst instanceof Integer && !Integer.valueOf(1).equals(Nodes.getConstant(leftOp))) {
                    int priority = 0;
                    int c = (Integer)leftConst;
                    if(c < 32 || (c < 64 && leftOp.getExpectedType().getSimpleType() == JvmType.Long)) {
                        if(!Methods.isHashCodeMethod(md) || !Inf.BACKLINK.findTransitiveUsages(expr, false).allMatch(e -> e.getCode() == AstCode.Return)) {
                            priority += 10;
                            if(c == 16) {
                                priority += 5;
                            } else if(c != 8) {
                                priority += 10;
                            }
                            if(rightAddend.getCode() != AstCode.And) {
                                priority += 10;
                            }
                        }
                        mc.report("BitShiftWrongPriority", priority, expr, Roles.NUMBER.create(c));
                    }
                }
            }
        }
    }
    
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc) {
        TypeReference inferredType = expr.getInferredType();
        if (inferredType == null)
            return;
        JvmType exprType = inferredType.getSimpleType();
        switch (expr.getCode()) {
        case Rem:
            if (isConst(expr.getArguments().get(1), 1)) {
                mc.report("RemOne", 0, expr.getArguments().get(0));
            }
            break;
        case Add:
            checkSignedByte(expr, mc);
            break;
        case Or:
            checkSignedByte(expr, mc);
            // passthru
        case Xor:
            if (exprType == JvmType.Long || exprType == JvmType.Integer) {
                Nodes.ifBinaryWithConst(expr, (child, constant) -> {
                    if (constant instanceof Number && ((Number) constant).longValue() == 0
                        && !Nodes.isCompoundAssignment(nc.getNode())
                        && (nc.getParent() == null || !Nodes.isCompoundAssignment(nc.getParent().getNode()))) {
                        mc.report("UselessOrWithZero", 0, child, Roles.OPERATION.create(expr));
                    }
                });
            }
            break;
        case And:
            Nodes.ifBinaryWithConst(expr, (child, constant) -> {
                if (constant instanceof Number) {
                    long val = ((Number) constant).longValue();
                    if (val == -1 && !Nodes.isCompoundAssignment(nc.getNode()))
                        mc.report("UselessAndWithMinusOne", 0, child, Roles.NUMBER.create((Number) constant));
                    else if (val == 0)
                        mc.report("UselessAndWithZero", 0, child);
                }
            });
            break;
        case CmpGt:
        case CmpLt: {
            Expression bitAnd = Exprs.getChild(expr, expr.getCode() == AstCode.CmpGt ? 0 : 1);
            Object zero = Nodes.getConstant(expr.getArguments().get(expr.getCode() == AstCode.CmpGt ? 1 : 0));
            if (isIntegral(zero) && ((Number) zero).longValue() == 0 && bitAnd.getCode() == AstCode.And) {
                Nodes.ifBinaryWithConst(bitAnd, (flags, mask) -> {
                    if (isIntegral(mask)) {
                        if (mask instanceof Integer && ((Integer) mask) < 0 || mask instanceof Long
                            && ((Long) mask) < 0) {
                            mc.report("BitCheckGreaterNegative", 0, expr, Roles.NUMBER.create((Number) mask));
                        } else {
                            mc.report("BitCheckGreater", 0, expr, Roles.NUMBER.create((Number) mask));
                        }
                    }
                });
            }
            break;
        }
        case CmpEq:
        case CmpNe:
            Nodes.ifBinaryWithConst(expr, (child, outerConst) -> {
                if (!isIntegral(outerConst))
                    return;
                if (child.getCode() != AstCode.And && child.getCode() != AstCode.Or)
                    return;
                long outerVal = ((Number) outerConst).longValue();
                Nodes.ifBinaryWithConst(child, (grandChild, innerConst) -> {
                    if (!isIntegral(innerConst)) 
                        return;
                    long innerVal = ((Number) innerConst).longValue();
                    if (child.getCode() == AstCode.And) {
                        if ((outerVal & ~innerVal) != 0) {
                            mc.report("CompareBitAndIncompatible", 0, expr, AND_OPERAND.create(innerVal),
                                COMPARED_TO.create(outerVal));
                        }
                    } else {
                        if ((~outerVal & innerVal) != 0) {
                            mc.report("CompareBitOrIncompatible", 0, expr, OR_OPERAND.create(innerVal),
                                COMPARED_TO.create(outerVal));
                        }
                    }
                });
            });
            break;
        case Shl:
        case Shr:
        case UShr: {
            Object constant = Nodes.getConstant(expr.getArguments().get(1));
            if (constant instanceof Integer) {
                int bits = (int) constant;
                if (bits < 0 || bits > 63 || (bits > 31 && exprType == JvmType.Integer)) {
                    mc.report("BitShiftInvalidAmount", 0, expr, Roles.NUMBER.create(bits), Roles.OPERATION.create(expr),
                        Roles.MAX_VALUE.create(exprType == JvmType.Integer ? 31 : 63));
                }
            }
        }
        default:
        }
    }

    private void checkSignedByte(Expression expr, MethodContext mc) {
        JvmType type = expr.getInferredType().getSimpleType();
        if (type != JvmType.Integer && type != JvmType.Long)
            return;
        if (Inf.BACKLINK.findUsages(expr).stream().allMatch(e -> e.getCode() == AstCode.I2B))
            return;
        if (Exprs.bothChildrenMatch(expr, BadMath::isByte, BadMath::isLow8BitsClear)) {
            mc.report(expr.getCode() == AstCode.Add ? "BitAddSignedByte" : "BitOrSignedByte", 0, expr);
        }
    }

    private static boolean isByte(Expression expr) {
        if (expr.getCode() == AstCode.I2L)
            return isByte(Exprs.getChild(expr, 0));
        TypeReference type = expr.getInferredType();
        return type != null && type.getInternalName().equals("B");
    }

    private static boolean isLow8BitsClear(Expression arg) {
        Object value = Nodes.getConstant(arg);
        if (value instanceof Number) {
            long num = ((Number) value).longValue();
            return num != 0x100 && (num & 0xFF) == 0;
        }
        if (arg.getCode() == AstCode.Shl) {
            Object shiftAmount = Nodes.getConstant(arg.getArguments().get(1));
            return shiftAmount instanceof Number && (((Number) shiftAmount).intValue() & 0x1F) >= 8;
        }
        if (arg.getCode() == AstCode.And) {
            Object leftOp = Nodes.getConstant(arg.getArguments().get(0));
            Object rightOp = Nodes.getConstant(arg.getArguments().get(1));
            if (leftOp instanceof Number && (((Number) leftOp).longValue() & 0xFF) == 0)
                return true;
            if (rightOp instanceof Number && (((Number) rightOp).longValue() & 0xFF) == 0)
                return true;
        }
        return false;
    }

    private static boolean isConst(Expression expr, long wantedValue) {
        Object constant = Nodes.getConstant(expr);
        return isIntegral(constant) && ((Number) constant).longValue() == wantedValue;
    }

    private static boolean isIntegral(Object constant) {
        return constant instanceof Integer || constant instanceof Long;
    }
}
