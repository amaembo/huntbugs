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
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Correctness", name = "RemOne", maxScore = 70)
@WarningDefinition(category = "Correctness", name = "CompareBitAndIncompatible", maxScore = 70)
@WarningDefinition(category = "Correctness", name = "CompareBitOrIncompatible", maxScore = 70)
@WarningDefinition(category = "RedundantCode", name = "UselessOrWithZero", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "UselessAndWithMinusOne", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "UselessAndWithZero", maxScore = 70)
@WarningDefinition(category = "Correctness", name = "BitCheckGreaterNegative", maxScore = 80)
@WarningDefinition(category = "Correctness", name = "BitShiftInvalidAmount", maxScore = 75)
@WarningDefinition(category = "BadPractice", name = "BitCheckGreater", maxScore = 35)
// TODO: procyon optimizes too hard to detect "UselessAndWithZero"
public class BadMath {
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
        case Or:
        case Xor:
            if (exprType == JvmType.Long || exprType == JvmType.Integer) {
                Nodes.ifBinaryWithConst(expr, (child, constant) -> {
                    if (constant instanceof Number && ((Number) constant).longValue() == 0
                        && !Nodes.isCompoundAssignment(nc.getNode())) {
                        mc.report("UselessOrWithZero", 0, child, WarningAnnotation.forOperation(expr));
                    }
                });
            }
            break;
        case And:
            Nodes.ifBinaryWithConst(expr, (child, constant) -> {
                if (constant instanceof Number) {
                    long val = ((Number) constant).longValue();
                    if (val == -1 && !Nodes.isCompoundAssignment(nc.getNode()))
                        mc.report("UselessAndWithMinusOne", 0, child, new WarningAnnotation<>("NUMBER",
                                constant instanceof Integer ? "0xFFFF_FFFF" : "0xFFFF_FFFF_FFFF_FFFF"));
                    else if (val == 0)
                        mc.report("UselessAndWithZero", 0, child);
                }
            });
            break;
        case CmpGt:
        case CmpLt: {
            Expression bitAnd = Nodes.getChild(expr, expr.getCode() == AstCode.CmpGt ? 0 : 1);
            Object zero = Nodes.getConstant(expr.getArguments().get(expr.getCode() == AstCode.CmpGt ? 1 : 0));
            if (isIntegral(zero) && ((Number) zero).longValue() == 0 && bitAnd.getCode() == AstCode.And) {
                Nodes.ifBinaryWithConst(bitAnd, (flags, mask) -> {
                    if (isIntegral(mask)) {
                        if (mask instanceof Integer && ((Integer) mask) < 0 || mask instanceof Long
                            && ((Long) mask) < 0) {
                            mc.report("BitCheckGreaterNegative", 0, flags, WarningAnnotation.forNumber((Number) mask));
                        } else {
                            mc.report("BitCheckGreater", 0, flags, WarningAnnotation.forNumber((Number) mask));
                        }
                    }
                });
            }
            break;
        }
        case CmpEq:
        case CmpNe:
            Nodes.ifBinaryWithConst(expr, (child, outerConst) -> {
                if (isIntegral(outerConst) && (child.getCode() == AstCode.And || child.getCode() == AstCode.Or)) {
                    long outerVal = ((Number) outerConst).longValue();
                    Nodes.ifBinaryWithConst(child,
                        (grandChild, innerConst) -> {
                            if (isIntegral(innerConst)) {
                                long innerVal = ((Number) innerConst).longValue();
                                if (child.getCode() == AstCode.And) {
                                    if ((outerVal & ~innerVal) != 0) {
                                        mc.report("CompareBitAndIncompatible", 0, expr, new WarningAnnotation<>(
                                                "AND_OPERAND", innerVal), new WarningAnnotation<>("COMPARED_TO",
                                                outerVal));
                                    }
                                } else {
                                    if ((~outerVal & innerVal) != 0) {
                                        mc.report("CompareBitOrIncompatible", 0, expr, new WarningAnnotation<>(
                                                "OR_OPERAND", innerVal), new WarningAnnotation<>("COMPARED_TO",
                                                outerVal));
                                    }
                                }
                            }
                        });
                }
            });
            break;
        case Shl:
        case Shr:
        case UShr: {
            Object constant = Nodes.getConstant(expr.getArguments().get(1));
            if (constant instanceof Integer) {
                int bits = (int) constant;
                if (bits < 0 || bits > 63 || (bits > 31 && exprType == JvmType.Integer)) {
                    mc.report("BitShiftInvalidAmount", 0, expr, WarningAnnotation.forNumber(bits), WarningAnnotation
                            .forOperation(expr), new WarningAnnotation<>("MAX_VALUE", exprType == JvmType.Integer ? 31
                            : 63));
                }
            }
        }
        default:
        }
    }

    private static boolean isConst(Expression expr, long wantedValue) {
        Object constant = Nodes.getConstant(expr);
        return isIntegral(constant) && ((Number) constant).longValue() == wantedValue;
    }

    private static boolean isIntegral(Object constant) {
        return constant instanceof Integer || constant instanceof Long;
    }
}
