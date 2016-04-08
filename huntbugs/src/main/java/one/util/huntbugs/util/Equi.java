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
package one.util.huntbugs.util;

import java.util.List;
import java.util.Objects;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.core.StringUtilities;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.CatchBlock;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Lambda;
import com.strobel.decompiler.ast.Loop;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.TryCatchBlock;
import com.strobel.decompiler.ast.Variable;

/**
 * @author lan
 *
 */
public class Equi {
    static class Counter {
        private static final int MAX_COUNT = 1000;
        
        int count;
        
        boolean addAndCheck() {
            if(++count > MAX_COUNT) {
                return false;
            }
            return true;
        }
    }
    
    public static boolean equiBlocks(Block left, Block right) {
        return equiBlocks(left, right, new Counter());
    }
    
    public static boolean equiBlocks(Block left, Block right, Counter count) {
        if (left == null)
            return right == null;
        if (!count.addAndCheck())
            return false;
        if (right == null)
            return false;
        List<Node> leftBody = left.getBody();
        List<Node> rightBody = right.getBody();
        if (leftBody.size() != rightBody.size())
            return false;
        for (int i = 0; i < leftBody.size(); i++) {
            Node leftNode = leftBody.get(i);
            Node rightNode = rightBody.get(i);
            if (leftNode.getClass() != rightNode.getClass())
                return false;
        }
        int start = left instanceof CatchBlock ? 1 : 0;
        for (int i = start; i < leftBody.size(); i++) {
            Node leftNode = leftBody.get(i);
            Node rightNode = rightBody.get(i);
            if (leftNode instanceof Expression) {
                if (!equiExpressions((Expression) leftNode, (Expression) rightNode, count))
                    return false;
            } else if (leftNode instanceof Condition) {
                Condition leftCond = (Condition) leftNode;
                Condition rightCond = (Condition) rightNode;
                if (!equiExpressions(leftCond.getCondition(), rightCond.getCondition(), count))
                    return false;
                if (!equiBlocks(leftCond.getTrueBlock(), rightCond.getTrueBlock(), count))
                    return false;
                if (!equiBlocks(leftCond.getFalseBlock(), rightCond.getFalseBlock(), count))
                    return false;
            } else if (leftNode instanceof Loop) {
                Loop leftLoop = (Loop) leftNode;
                Loop rightLoop = (Loop) rightNode;
                if (leftLoop.getLoopType() != rightLoop.getLoopType())
                    return false;
                if (!equiExpressions(leftLoop.getCondition(), rightLoop.getCondition(), count))
                    return false;
                if (!equiBlocks(leftLoop.getBody(), rightLoop.getBody(), count))
                    return false;
            } else if (leftNode instanceof TryCatchBlock) {
                TryCatchBlock leftTry = (TryCatchBlock) leftNode;
                TryCatchBlock rightTry = (TryCatchBlock) rightNode;
                if (!equiTryCatch(leftTry, rightTry, count))
                    return false;
            } else
                // TODO: support switch
                return false;
        }
        return true;
    }

    private static boolean equiTryCatch(TryCatchBlock leftTry, TryCatchBlock rightTry, Counter count) {
        List<CatchBlock> leftCatches = leftTry.getCatchBlocks();
        List<CatchBlock> rightCatches = rightTry.getCatchBlocks();
        if (leftCatches.size() != rightCatches.size())
            return false;
        for (int j = 0; j < leftCatches.size(); j++) {
            CatchBlock leftCatch = leftCatches.get(j);
            CatchBlock rightCatch = rightCatches.get(j);
            if (!equiTypes(leftCatch.getExceptionType(), rightCatch.getExceptionType()))
                return false;
            List<TypeReference> leftTypes = leftCatch.getCaughtTypes();
            List<TypeReference> rightTypes = rightCatch.getCaughtTypes();
            if (leftTypes.size() != rightTypes.size())
                return false;
            for (int k = 0; k < leftTypes.size(); k++) {
                if (!equiTypes(leftTypes.get(k), rightTypes.get(k)))
                    return false;
            }
            if (!equiBlocks(leftCatch, rightCatch, count))
                return false;
        }
        if (!equiBlocks(leftTry.getTryBlock(), rightTry.getTryBlock(), count))
            return false;
        if (!equiBlocks(leftTry.getFinallyBlock(), rightTry.getFinallyBlock(), count))
            return false;
        return true;
    }
    
    public static boolean equiExpressions(Expression left, Expression right) {
        return equiExpressions(left, right, new Counter());
    }
    
    public static boolean equiExpressions(Expression left, Expression right, Counter count) {
        if (left == null)
            return right == null;
        if (!count.addAndCheck())
            return false;
        if (right == null)
            return false;
        if (left.getCode() != right.getCode())
            return false;
        
        Object leftOp = left.getOperand();
        Object rightOp = right.getOperand();

        if (!equiOperands(leftOp, rightOp, count))
            return false;

        if (left.getArguments().size() != right.getArguments().size()) {
            return false;
        }
        
        if (left.getArguments().size() == 2) {
            // Commutative operators check
            switch(left.getCode()) {
            case And:
            case Or:
            case Xor:
            case Add:
            case Mul:
            case CmpEq:
            case CmpNe:
                return (equiExpressions(left.getArguments().get(0), right.getArguments().get(0)) &&
                        equiExpressions(left.getArguments().get(1), right.getArguments().get(1))) ||
                        (equiExpressions(left.getArguments().get(0), right.getArguments().get(1)) &&
                                equiExpressions(left.getArguments().get(1), right.getArguments().get(0)));
            default:
            }
        }

        for (int i = 0, n = left.getArguments().size(); i < n; i++) {
            final Expression a1 = left.getArguments().get(i);
            final Expression a2 = right.getArguments().get(i);

            if (!equiExpressions(a1, a2)) {
                return false;
            }
        }

        return true;
    }

    private static boolean equiOperands(Object left, Object right, Counter count) {
        if (left == null)
            return right == null;
        if (right == null)
            return false;
        if (left instanceof FieldReference) {
            if (!(right instanceof FieldReference))
                return false;
            return equiFields((FieldReference) left, (FieldReference) right);
        }
        if (left instanceof MethodReference) {
            if (!(right instanceof MethodReference))
                return false;
            return equiMethods((MethodReference) left, (MethodReference) right);
        }
        if (left instanceof Lambda) {
            if(right.getClass() != left.getClass())
                return false;
            return equiLambdas((Lambda)left, (Lambda)right, count);
        }
        if (left instanceof Variable) {
            if(right.getClass() != left.getClass())
                return false;
            return equiVariables((Variable)left, (Variable)right);
        }
        return Objects.equals(right, left);
    }

    private static boolean equiVariables(Variable left, Variable right) {
        if(left.isLambdaParameter() && right.isLambdaParameter()) {
            return left.getOriginalParameter().getPosition() == right.getOriginalParameter().getPosition();
        }
        return left.equals(right);
    }

    private static boolean equiMethods(final MethodReference left, final MethodReference right) {
        return StringUtilities.equals(left.getFullName(), right.getFullName()) &&
            StringUtilities.equals(left.getErasedSignature(), right.getErasedSignature());
    }

    private static boolean equiFields(final FieldReference left, final FieldReference right) {
        return StringUtilities.equals(left.getFullName(), right.getFullName());
    }

    private static boolean equiLambdas(Lambda left, Lambda right, Counter count) {
        return equiMethods(left.getMethod(), right.getMethod())
                && equiTypes(left.getFunctionType(), right.getFunctionType())
                && equiBlocks(left.getBody(), right.getBody(), count);
    }

    private static boolean equiTypes(TypeReference left, TypeReference right) {
        if (left == null)
            return right == null;
        if (right == null)
            return false;
        return left.getInternalName().equals(right.getInternalName());
    }

}
