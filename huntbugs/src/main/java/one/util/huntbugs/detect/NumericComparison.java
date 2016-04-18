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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

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
            if(invert)
                return new LongRange(minValue, maxValue).isFalseEmpty(realRange);
            return realRange.minValue > maxValue || realRange.maxValue < minValue;
        }

        boolean isFalseEmpty(LongRange realRange) {
            if(invert)
                return new LongRange(minValue, maxValue).isTrueEmpty(realRange);
            return realRange.minValue >= minValue && realRange.maxValue <= maxValue;
        }
    }

    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if (Nodes.isComparison(expr)) {
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
            LongRange realRange;
            switch (type.getSimpleType()) {
            case Integer:
                realRange = intRange(arg);
                break;
            case Byte:
                realRange = new LongRange(Byte.MIN_VALUE, Byte.MAX_VALUE);
                break;
            case Long:
                if(arg.getCode() == AstCode.I2L)
                    realRange = intRange(Nodes.getChild(arg, 0));
                else
                    realRange = new LongRange(Long.MIN_VALUE, Long.MAX_VALUE);
                break;
            case Character:
                realRange = new LongRange(Character.MIN_VALUE, Character.MAX_VALUE);
                break;
            case Short:
                realRange = new LongRange(Short.MIN_VALUE, Short.MAX_VALUE);
                break;
            default:
                return;
            }
            Boolean result = null;
            if(cmpRange.isTrueEmpty(realRange)) {
                result = false;
            } else if(cmpRange.isFalseEmpty(realRange)) {
                result = true;
            }
            if(result == null)
                return;
            mc.report("ComparisonWithOutOfRangeValue", 0, expr, WarningAnnotation.forOperation(code), WarningAnnotation.forNumber(constant),
                new WarningAnnotation<>("MIN_VALUE", realRange.minValue), new WarningAnnotation<>("MAX_VALUE", realRange.maxValue),
                new WarningAnnotation<>("RESULT", result));
        }
    }

    private LongRange intRange(Expression arg) {
        if(arg.getCode() == AstCode.ArrayLength)
            return new LongRange(0, Integer.MAX_VALUE);
        if(arg.getCode() == AstCode.And) {
            Object constant = Nodes.getConstant(arg.getArguments().get(1));
            if(constant instanceof Integer) {
                int andOp = (int) constant;
                if(andOp > 0) {
                    return new LongRange(0, (Integer.highestOneBit(andOp) << 1) - 1);
                }
            }
        }
        if(arg.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) arg.getOperand();
            if(mr.getName().equals("size") && mr.getSignature().equals("()I")) {
                if(Types.isInstance(mr.getDeclaringType(), "java/util/Collection") ||
                        Types.isInstance(mr.getDeclaringType(), "java/util/Map")) {
                    return new LongRange(0, Integer.MAX_VALUE);
                }
            }
            if(mr.getDeclaringType().getInternalName().equals("java/lang/Byte") && mr.getName().endsWith("Value")) {
                return new LongRange(Byte.MIN_VALUE, Byte.MAX_VALUE);
            }
            if(mr.getDeclaringType().getInternalName().equals("java/lang/Short") && mr.getName().endsWith("Value")) {
                return new LongRange(Short.MIN_VALUE, Short.MAX_VALUE);
            }
        }
        return new LongRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}
