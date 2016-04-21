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
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.CaseBlock;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Switch;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Correctness", name = "ComparisonWithOutOfRangeValue", maxScore = 65)
@WarningDefinition(category = "RedundantCode", name = "SwitchBranchUnreachable", maxScore = 65)
public class NumericComparison {
    class LongRange {
        final long minValue, maxValue;
        final boolean invert;

        public LongRange(AstCode code, long constant) {
            boolean invert = false;
            switch (code) {
            case CmpNe:
                invert = true;
                // passthru
            case CmpEq:
                minValue = maxValue = constant;
                break;
            case CmpLt:
                minValue = Long.MIN_VALUE;
                maxValue = constant - 1;
                break;
            case CmpLe:
                minValue = Long.MIN_VALUE;
                maxValue = constant;
                break;
            case CmpGt:
                minValue = constant + 1;
                maxValue = Long.MAX_VALUE;
                break;
            case CmpGe:
                minValue = constant;
                maxValue = Long.MAX_VALUE;
                break;
            default:
                throw new InternalError("Unexpected code: " + code);
            }
            this.invert = invert;
        }

        LongRange(long min, long max) {
            minValue = min;
            maxValue = max;
            invert = false;
        }

        boolean isTrueEmpty(LongRange realRange) {
            if (invert)
                return new LongRange(minValue, maxValue).isFalseEmpty(realRange);
            return realRange.minValue > maxValue || realRange.maxValue < minValue;
        }

        boolean isFalseEmpty(LongRange realRange) {
            if (invert)
                return new LongRange(minValue, maxValue).isTrueEmpty(realRange);
            return realRange.minValue >= minValue && realRange.maxValue <= maxValue;
        }
    }

    @AstVisitor(nodes = AstNodes.ALL)
    public void visit(Node node, MethodContext mc) {
        if (Nodes.isComparison(node)) {
            Expression expr = (Expression) node;
            TypeReference type = expr.getArguments().get(0).getInferredType();
            if (type == null)
                return;
            if (!type.getSimpleType().isIntegral())
                return;
            AstCode code = expr.getCode();
            Expression arg;
            Object left = Nodes.getConstant(expr.getArguments().get(0));
            Object right = Nodes.getConstant(expr.getArguments().get(1));
            long constant;
            if (left instanceof Number) {
                if (right instanceof Number) {
                    return; // Will be covered by KnownComparison
                }
                constant = ((Number) left).longValue();
                arg = Nodes.getChild(expr, 1);
                switch (code) {
                case CmpGe:
                    code = AstCode.CmpLe;
                    break;
                case CmpGt:
                    code = AstCode.CmpLt;
                    break;
                case CmpLe:
                    code = AstCode.CmpGe;
                    break;
                case CmpLt:
                    code = AstCode.CmpGt;
                    break;
                default:
                }
            } else {
                if (!(right instanceof Number))
                    return;
                constant = ((Number) right).longValue();
                arg = Nodes.getChild(expr, 0);
            }
            LongRange cmpRange = new LongRange(code, constant);
            LongRange realRange = getExpressionRange(type.getSimpleType(), arg);
            if(realRange == null)
                return;
            Boolean result = null;
            if (cmpRange.isTrueEmpty(realRange)) {
                result = false;
            } else if (cmpRange.isFalseEmpty(realRange)) {
                result = true;
            }
            if (result == null || ValuesFlow.isAssertion(expr))
                return;
            int priority = 0;
            if (realRange.minValue == constant || realRange.maxValue == constant)
                priority += 15;
            mc.report("ComparisonWithOutOfRangeValue", priority, expr, WarningAnnotation.forOperation(code),
                WarningAnnotation.forNumber(constant), new WarningAnnotation<>("MIN_VALUE", realRange.minValue),
                new WarningAnnotation<>("MAX_VALUE", realRange.maxValue), new WarningAnnotation<>("RESULT", result));
        }
        else if(node instanceof Switch) {
            Switch switchNode = (Switch)node;
            Expression condition = switchNode.getCondition();
            JvmType type = condition.getInferredType() == null ? JvmType.Integer : condition.getInferredType().getSimpleType();
            LongRange realRange = getExpressionRange(type, condition);
            if(realRange == null || realRange.minValue <= Integer.MIN_VALUE &&
                    realRange.maxValue >= Integer.MAX_VALUE)
                return;
            for(CaseBlock block : switchNode.getCaseBlocks()) {
                block.getValues().stream().filter(val -> new LongRange(AstCode.CmpEq, val).isTrueEmpty(realRange)).findFirst()
                    .ifPresent(val -> {
                        mc.report("SwitchBranchUnreachable", 0, block, WarningAnnotation.forNumber(val), new WarningAnnotation<>("MIN_VALUE", realRange.minValue),
                            new WarningAnnotation<>("MAX_VALUE", realRange.maxValue));
                    });
            }
        }
    }

    private LongRange getExpressionRange(JvmType type, Expression arg) {
        switch (type) {
        case Integer:
            return intRange(arg);
        case Byte:
            return new LongRange(Byte.MIN_VALUE, Byte.MAX_VALUE);
        case Long:
            if (arg.getCode() == AstCode.I2L)
                return intRange(Nodes.getChild(arg, 0));
            return new LongRange(Long.MIN_VALUE, Long.MAX_VALUE);
        case Character:
            return new LongRange(Character.MIN_VALUE, Character.MAX_VALUE);
        case Short:
            return new LongRange(Short.MIN_VALUE, Short.MAX_VALUE);
        default:
            return null;
        }
    }

    private LongRange intRange(Expression arg) {
        switch (arg.getCode()) {
        case ArrayLength:
            return new LongRange(0, Integer.MAX_VALUE);
        case And: {
            Object constant = Nodes.getConstant(arg.getArguments().get(1));
            if (constant instanceof Integer) {
                int andOp = (int) constant;
                if (andOp > 0) {
                    return new LongRange(0, (Integer.highestOneBit(andOp) << 1) - 1);
                }
            }
            break;
        }
        case Rem: {
            Object constant = Nodes.getConstant(arg.getArguments().get(1));
            if (constant instanceof Integer) {
                int remOp = (int) constant;
                if (remOp != 0 && remOp != Integer.MIN_VALUE) {
                    return new LongRange(1 - Math.abs(remOp), Math.abs(remOp) - 1);
                }
            }
            break;
        }
        case Shr: {
            Object constant = Nodes.getConstant(arg.getArguments().get(1));
            if (constant instanceof Integer) {
                int shrOp = ((int) constant) & 0x1F;
                int bits = 31 - shrOp;
                int max = (1 << bits) - 1;
                return new LongRange(-max - 1, max);
            }
            break;
        }
        case UShr: {
            Object constant = Nodes.getConstant(arg.getArguments().get(1));
            if (constant instanceof Integer) {
                int shrOp = ((int) constant) & 0x1F;
                if (shrOp > 0) {
                    int bits = 32 - shrOp;
                    int max = (1 << bits);
                    return new LongRange(0, max - 1);
                }
            }
            break;
        }
        case InvokeVirtual:
            MethodReference mr = (MethodReference) arg.getOperand();
            if (mr.getName().equals("size") && mr.getSignature().equals("()I")) {
                if (Types.isInstance(mr.getDeclaringType(), "java/util/Collection")
                    || Types.isInstance(mr.getDeclaringType(), "java/util/Map")) {
                    return new LongRange(0, Integer.MAX_VALUE);
                }
            }
            if (mr.getDeclaringType().getInternalName().equals("java/lang/Byte") && mr.getName().endsWith("Value")) {
                return new LongRange(Byte.MIN_VALUE, Byte.MAX_VALUE);
            }
            if (mr.getDeclaringType().getInternalName().equals("java/lang/Short") && mr.getName().endsWith("Value")) {
                return new LongRange(Short.MIN_VALUE, Short.MAX_VALUE);
            }
            break;
        default:
        }
        return new LongRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}
