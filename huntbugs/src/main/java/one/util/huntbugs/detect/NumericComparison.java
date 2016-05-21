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

import java.util.HashSet;
import java.util.Set;

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
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Role.StringRole;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "Correctness", name = "ComparisonWithOutOfRangeValue", maxScore = 65)
@WarningDefinition(category = "RedundantCode", name = "SwitchBranchUnreachable", maxScore = 65)
@WarningDefinition(category = "BadPractice", name = "CheckForOddnessFailsForNegative", maxScore = 40)
public class NumericComparison {
    private static final LongRange SHORT_RANGE = new LongRange(Short.MIN_VALUE, Short.MAX_VALUE);
    private static final LongRange CHAR_RANGE = new LongRange(Character.MIN_VALUE, Character.MAX_VALUE);
    private static final LongRange LONG_RANGE = new LongRange(Long.MIN_VALUE, Long.MAX_VALUE);
    private static final LongRange BYTE_RANGE = new LongRange(Byte.MIN_VALUE, Byte.MAX_VALUE);
    private static final LongRange INT_RANGE = new LongRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
    
    private static final StringRole RESULT = StringRole.forName("RESULT");
    
    static class LongRange {
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
        
        LongRange absInt() {
            if(minValue <= Integer.MIN_VALUE || minValue >= 0)
                return this;
            if(maxValue <= 0)
                return new LongRange(-maxValue, -minValue);
            return new LongRange(0, Math.max(-minValue, maxValue));
        }
        
        LongRange union(LongRange other) {
            if(invert || other.invert)
                throw new IllegalStateException();
            return new LongRange(Math.min(minValue, other.minValue), Math.max(maxValue, other.maxValue));
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
            JvmType jvmType;
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
                jvmType = getIntegralType(expr.getArguments().get(1));
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
                jvmType = getIntegralType(expr.getArguments().get(0));
            }
            if(jvmType == null)
                return;
            
            checkRem2Eq1(mc, jvmType, code, arg, constant);
            
            LongRange cmpRange = new LongRange(code, constant);
            LongRange realRange = getExpressionRange(jvmType, arg);
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
            mc.report("ComparisonWithOutOfRangeValue", priority, expr, Roles.OPERATION.create(code),
                Roles.NUMBER.create(constant), Roles.MIN_VALUE.create(realRange.minValue), Roles.MAX_VALUE.create(
                    realRange.maxValue), RESULT.create(result.toString()));
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
                block.getValues().stream().filter(val -> new LongRange(AstCode.CmpEq, val).isTrueEmpty(realRange))
                        .findFirst().ifPresent(val -> {
                            mc.report("SwitchBranchUnreachable", 0, block, Roles.NUMBER.create(val), Roles.MIN_VALUE
                                    .create(realRange.minValue), Roles.MAX_VALUE.create(realRange.maxValue));
                        });
            }
        }
    }

    private JvmType getIntegralType(Expression expression) {
        TypeReference type = expression.getInferredType();
        if(type == null)
            return null;
        JvmType jvmType = type.getSimpleType();
        if(!jvmType.isIntegral())
            return null;
        if(jvmType == JvmType.Integer || jvmType == JvmType.Long)
            return jvmType;
        // Fix procyon type inference
        switch(expression.getCode()) {
        case Add:
        case Sub:
        case Div:
        case Rem:
        case Mul:
        case Shr:
        case Shl:
        case UShr:
        case Neg:
            return JvmType.Integer;
        default:
            return jvmType;
        }
    }

    private void checkRem2Eq1(MethodContext mc, JvmType jvmType, AstCode code, Expression arg, long constant) {
        if(constant == 1 && (code == AstCode.CmpEq || code == AstCode.CmpNe) && arg.getCode() == AstCode.Rem && Integer.valueOf(2).equals(Nodes.getConstant(arg.getArguments().get(1)))) {
            Expression remInput = Nodes.getChild(arg, 0);
            if(remInput.getCode() == AstCode.InvokeStatic) {
                MethodReference mr = (MethodReference) remInput.getOperand();
                if(mr.getName().equals("abs") && mr.getDeclaringType().getInternalName().equals("java/lang/Math")) {
                    return;
                }
            }
            if(getExpressionRange(jvmType, remInput).minValue < 0) {
                mc.report("CheckForOddnessFailsForNegative", 0, arg, Roles.OPERATION.create(code),
                    Roles.REPLACEMENT_STRING.create(code == AstCode.CmpEq ? "!=" : "=="));
            }
        }
    }
    
    private static LongRange getExpressionRange(JvmType type, Expression arg) {
        return getExpressionRange(type, arg, new HashSet<>());
    }

    private static LongRange getExpressionRange(JvmType type, Expression arg, Set<Expression> visited) {
        return ValuesFlow.reduce(arg, e -> {
            if (!visited.add(e))
                return getTypeRange(type);
            Object constant = Nodes.getConstant(e);
            if (constant instanceof Integer || constant instanceof Long) {
                long val = ((Number) constant).longValue();
                return new LongRange(val, val);
            }
            if (type == JvmType.Integer)
                return intRange(e, visited);
            if (e.getCode() == AstCode.I2L)
                return intRange(Nodes.getChild(e, 0), visited);
            return getTypeRange(type);
        }, (r1, r2) -> r1 == null || r2 == null ? null : r1.union(r2), r -> r == getTypeRange(type));
    }
    
    private static LongRange getTypeRange(JvmType type) {
        switch (type) {
        case Integer:
            return INT_RANGE;
        case Byte:
            return BYTE_RANGE;
        case Long:
            return LONG_RANGE;
        case Character:
            return CHAR_RANGE;
        case Short:
            return SHORT_RANGE;
        default:
            return null;
        }
    }

    private static LongRange intRange(Expression arg, Set<Expression> visited) {
        switch (arg.getCode()) {
        case ArrayLength:
            return new LongRange(0, Integer.MAX_VALUE);
        case And: {
            LongRange r1 = getExpressionRange(JvmType.Integer, Nodes.getChild(arg, 0), visited);
            LongRange r2 = getExpressionRange(JvmType.Integer, Nodes.getChild(arg, 1), visited);
            int maxBit1 = r1.minValue < 0 ? 0x80000000 : Integer.highestOneBit((int) r1.maxValue);
            int maxBit2 = r2.minValue < 0 ? 0x80000000 : Integer.highestOneBit((int) r2.maxValue);
            int totalMax = ((maxBit1 << 1) - 1) & ((maxBit2 << 1) - 1);
            if(totalMax >= 0)
                return new LongRange(0, totalMax);
            break;
        }
        case Rem: {
            LongRange remRange = getExpressionRange(JvmType.Integer, Nodes.getChild(arg, 1), visited).absInt();
            if(remRange.minValue < 0 || remRange.maxValue == 0)
                break;
            LongRange divRange = getExpressionRange(JvmType.Integer, Nodes.getChild(arg, 0), visited);
            if(divRange.minValue >= 0)
                return new LongRange(0, remRange.maxValue-1);
            return new LongRange(1 - remRange.maxValue, remRange.maxValue - 1);
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
        case InvokeInterface:
            MethodReference mr = (MethodReference) arg.getOperand();
            if (mr.getName().equals("size") && mr.getSignature().equals("()I")) {
                if (Types.isInstance(mr.getDeclaringType(), "java/util/Collection")
                    || Types.isInstance(mr.getDeclaringType(), "java/util/Map")) {
                    return new LongRange(0, Integer.MAX_VALUE);
                }
            }
            if (mr.getDeclaringType().getInternalName().equals("java/lang/Byte") && mr.getName().endsWith("Value")) {
                return BYTE_RANGE;
            }
            if (mr.getDeclaringType().getInternalName().equals("java/lang/Short") && mr.getName().endsWith("Value")) {
                return SHORT_RANGE;
            }
            break;
        default:
        }
        return INT_RANGE;
    }
}
