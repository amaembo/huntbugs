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

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Role.NumberRole;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Correctness", name = "ArrayIndexNegative", maxScore = 85)
@WarningDefinition(category = "Correctness", name = "ArrayIndexOutOfRange", maxScore = 85)
@WarningDefinition(category = "Correctness", name = "ArrayOffsetOutOfRange", maxScore = 85)
@WarningDefinition(category = "Correctness", name = "ArrayLengthOutOfRange", maxScore = 85)
public class ArrayRangeCheck {
    private static final NumberRole MAX_LENGTH = NumberRole.forName("MAX_LENGTH");  
    private static final long IMPOSSIBLE_ARRAY_LENGTH = Integer.MAX_VALUE + 1L;

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(MethodContext mc, NodeChain nc, Expression expr) {
        if ((expr.getCode() == AstCode.LoadElement || expr.getCode() == AstCode.StoreElement)
                && !nc.isInTry("java/lang/ArrayIndexOutOfBoundsException", "java/lang/IndexOutOfBoundsException")) {
            Integer idx = checkNegative(mc, expr, Nodes.getConstant(expr.getArguments().get(1)));
            if (idx != null) {
                long maxLength = getMaxLength(expr.getArguments().get(0));
                if (idx >= maxLength) {
                    mc.report("ArrayIndexOutOfRange", 0, expr.getArguments().get(0), Roles.NUMBER.create(idx),
                        MAX_LENGTH.create(maxLength));
                }
            }
        } else if (expr.getCode() == AstCode.InvokeStatic) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if (Types.is(mr.getDeclaringType(), System.class) && mr.getName().equals("arraycopy") && expr.getArguments()
                    .size() == 5 && !nc.isInTry("java/lang/IndexOutOfBoundsException")) {
                Integer srcPos = checkNegative(mc, expr, Nodes.getConstant(expr.getArguments().get(1)));
                Integer destPos = checkNegative(mc, expr, Nodes.getConstant(expr.getArguments().get(3)));
                Integer length = checkNegative(mc, expr, Nodes.getConstant(expr.getArguments().get(4)));
                long srcLen = getMaxLength(expr.getArguments().get(0));
                long destLen = getMaxLength(expr.getArguments().get(2));
                if(srcPos != null) {
                    if(srcPos > srcLen) {
                        mc.report("ArrayOffsetOutOfRange", 0, expr.getArguments().get(0), Roles.NUMBER.create(srcPos),
                            MAX_LENGTH.create(srcLen));
                    } else {
                        srcLen -= srcPos;
                    }
                }
                if(destPos != null) {
                    if(destPos > destLen) {
                        mc.report("ArrayOffsetOutOfRange", 0, expr.getArguments().get(2), Roles.NUMBER.create(destPos),
                            MAX_LENGTH.create(destLen));
                    } else {
                        destLen -= destPos;
                    }
                }
                long maxLen = Math.min(srcLen, destLen);
                if(length != null && length > maxLen) {
                    mc.report("ArrayLengthOutOfRange", 0, expr.getArguments().get(2), Roles.NUMBER.create(length),
                        MAX_LENGTH.create(maxLen));
                }
            }
        }
    }

    private static Integer checkNegative(MethodContext mc, Expression expr, Object idxObj) {
        if (idxObj instanceof Integer) {
            int idx = (int) idxObj;
            if (idx >= 0)
                return idx;
            mc.report("ArrayIndexNegative", 0, expr.getArguments().get(0), Roles.NUMBER.create(idx));
        }
        return null;
    }

    // Returns max possible length of the expression producing an array
    private static long getMaxLength(Expression expression) {
        return ValuesFlow.<Long>reduce(expression, expr -> {
            switch (expr.getCode()) {
            case InitArray:
                return (long) expr.getArguments().size();
            case NewArray: {
                Object length = Nodes.getConstant(expr.getArguments().get(0));
                if (length instanceof Integer)
                    return (long) (Integer) length;
                break;
            }
            case InvokeVirtual: {
                MethodReference mr = (MethodReference) expr.getOperand();
                if (mr.getName().equals("clone") && mr.getErasedSignature().startsWith("()")) {
                    return getMaxLength(Exprs.getChild(expr, 0));
                }
                break;
            }
            case CheckCast:
                return getMaxLength(Exprs.getChild(expr, 0));
            default:
                break;
            }
            return IMPOSSIBLE_ARRAY_LENGTH;
        }, Math::max, len -> len == IMPOSSIBLE_ARRAY_LENGTH);
    }
}
