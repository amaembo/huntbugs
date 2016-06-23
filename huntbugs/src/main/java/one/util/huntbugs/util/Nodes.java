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
package one.util.huntbugs.util;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import one.util.huntbugs.flow.Annotators;
import one.util.huntbugs.flow.Inf;
import one.util.huntbugs.flow.PurityAnnotator.Purity;
import one.util.huntbugs.flow.ValuesFlow;

import com.strobel.assembler.metadata.DynamicCallSite;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodHandle;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Lambda;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.TryCatchBlock;
import com.strobel.decompiler.ast.Variable;

/**
 * Nodes-related utility methods
 * 
 * @author Tagir Valeev
 * @see Exprs
 */
public class Nodes {
    public static boolean isOp(Node node, AstCode op) {
        return node instanceof Expression && ((Expression) node).getCode() == op;
    }

    public static boolean isInvoke(Node node) {
        if (!(node instanceof Expression))
            return false;
        AstCode code = ((Expression) node).getCode();
        return code == AstCode.InvokeDynamic || code == AstCode.InvokeStatic || code == AstCode.InvokeSpecial
            || code == AstCode.InvokeVirtual || code == AstCode.InvokeInterface;
    }

    public static boolean isNullCheck(Node node) {
        if (!isOp(node, AstCode.CmpEq) && !isOp(node, AstCode.CmpNe))
            return false;
        List<Expression> args = ((Expression) node).getArguments();
        return args.get(0).getCode() == AstCode.AConstNull ^ args.get(1).getCode() == AstCode.AConstNull;
    }

    public static boolean isFieldRead(Node node) {
        return isOp(node, AstCode.GetStatic) || isOp(node, AstCode.GetField);
    }

    public static Node getChild(Node node, int i) {
        if (node instanceof Expression) {
            return ValuesFlow.getSource(((Expression) node).getArguments().get(i));
        }
        return node.getChildren().get(i);
    }

    public static Object getConstant(Node node) {
        if (!(node instanceof Expression))
            return null;
        Expression expr = (Expression) node;
        if (expr.getCode() == AstCode.LdC)
            return expr.getOperand();
        return Inf.CONST.getValue(expr);
    }

    public static void ifBinaryWithConst(Expression expr, BiConsumer<Expression, Object> consumer) {
        if (expr.getArguments().size() == 2) {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            Object constant = getConstant(left);
            if (constant != null) {
                consumer.accept(right, constant);
            } else {
                constant = getConstant(right);
                if (constant != null) {
                    consumer.accept(left, constant);
                }
            }
        }
    }
    
    public static boolean isWriteTo(Node node, Set<Variable> vars) {
        if (!(node instanceof Expression))
            return false;
        Expression expr = (Expression) node;
        if (expr.getOperand() instanceof Variable && vars.contains(expr.getOperand()) && (expr
                .getCode() == AstCode.Store || expr.getCode() == AstCode.Inc))
            return true;
        if ((expr.getCode() == AstCode.PreIncrement || expr.getCode() == AstCode.PostIncrement) && vars.contains(expr
                .getArguments().get(0).getOperand()))
            return true;
        return false;
    }

    public static boolean isComparison(Node node) {
        if (!(node instanceof Expression) || ((Expression)node).getArguments().size() != 2)
            return false;
        switch (((Expression) node).getCode()) {
        case CmpEq:
        case CmpGe:
        case CmpGt:
        case CmpLe:
        case CmpLt:
        case CmpNe:
            return true;
        default:
            return false;
        }
    }

    public static boolean isBinaryMath(Node node) {
        if (!(node instanceof Expression) || ((Expression)node).getArguments().size() != 2)
            return false;
        switch (((Expression) node).getCode()) {
        case Add:
        case Sub:
        case Mul:
        case Div:
        case Rem:
        case Shl:
        case Shr:
        case UShr:
        case And:
        case Or:
        case Xor:
            return true;
        default:
            return false;
        }
    }

    public static boolean isBoxing(Node node) {
        if (!isOp(node, AstCode.InvokeStatic))
            return false;
        MethodReference ref = (MethodReference) ((Expression) node).getOperand();
        if (!ref.getName().equals("valueOf"))
            return false;
        TypeReference type = ref.getDeclaringType();
        if (type.getInternalName().equals("java/lang/Double") && ref.getSignature().equals("(D)Ljava/lang/Double;"))
            return true;
        if (type.getInternalName().equals("java/lang/Integer") && ref.getSignature().equals("(I)Ljava/lang/Integer;"))
            return true;
        if (type.getInternalName().equals("java/lang/Long") && ref.getSignature().equals("(J)Ljava/lang/Long;"))
            return true;
        if (type.getInternalName().equals("java/lang/Boolean") && ref.getSignature().equals("(Z)Ljava/lang/Boolean;"))
            return true;
        if (type.getInternalName().equals("java/lang/Short") && ref.getSignature().equals("(S)Ljava/lang/Short;"))
            return true;
        if (type.getInternalName().equals("java/lang/Character") && ref.getSignature().equals(
            "(C)Ljava/lang/Character;"))
            return true;
        if (type.getInternalName().equals("java/lang/Float") && ref.getSignature().equals("(F)Ljava/lang/Float;"))
            return true;
        if (type.getInternalName().equals("java/lang/Byte") && ref.getSignature().equals("(B)Ljava/lang/Byte;"))
            return true;
        return false;
    }

    public static boolean isUnboxing(Node node) {
        if (!isOp(node, AstCode.InvokeVirtual))
            return false;
        MethodReference ref = (MethodReference) ((Expression) node).getOperand();
        TypeReference type = ref.getDeclaringType();
        if (type.getInternalName().equals("java/lang/Double") && ref.getName().equals("doubleValue"))
            return true;
        if (type.getInternalName().equals("java/lang/Integer") && ref.getName().equals("intValue"))
            return true;
        if (type.getInternalName().equals("java/lang/Long") && ref.getName().equals("longValue"))
            return true;
        if (type.getInternalName().equals("java/lang/Boolean") && ref.getName().equals("booleanValue"))
            return true;
        if (type.getInternalName().equals("java/lang/Short") && ref.getName().equals("shortValue"))
            return true;
        if (type.getInternalName().equals("java/lang/Character") && ref.getName().equals("charValue"))
            return true;
        if (type.getInternalName().equals("java/lang/Float") && ref.getName().equals("floatValue"))
            return true;
        if (type.getInternalName().equals("java/lang/Byte") && ref.getName().equals("byteValue"))
            return true;
        return false;
    }
    
    public static boolean isEquivalent(Node expr1, Node expr2) {
        if (expr1 == expr2)
            return true;
        if (expr1 == null)
            return expr2 == null;
        if (expr1 instanceof Expression && expr2 instanceof Expression)
            return Equi.equiExpressions((Expression) expr1, (Expression) expr2) && 
                    Inf.PURITY.get((Expression)expr1).atLeast(Purity.HEAP_DEP);
        return false;
    }

    public static boolean isSideEffectFree(Node node) {
        if (node == null)
            return true;
        if (!(node instanceof Expression)) {
            for(Node child : getChildren(node)) {
                if(!isSideEffectFree(child)) {
                    return false;
                }
            }
            return true;
        }
        return Inf.PURITY.isSideEffectFree((Expression) node);
    }

    public static boolean isPure(Node node) {
        if (node == null)
            return true;
        if (!(node instanceof Expression))
            return false;
        return Inf.PURITY.isPure((Expression) node);
    }

    public static boolean isSynchorizedBlock(Node node) {
        if (!(node instanceof TryCatchBlock)) {
            return false;
        }
        TryCatchBlock tcb = (TryCatchBlock) node;
        return getSyncObject(tcb) != null;
    }

    public static Expression getSyncObject(TryCatchBlock tcb) {
        Block finallyBlock = tcb.getFinallyBlock();
        if (finallyBlock == null)
            return null;
        List<Node> list = finallyBlock.getBody();
        if (list.size() != 1)
            return null;
        Node n = list.get(0);
        if (!Nodes.isOp(n, AstCode.MonitorExit))
            return null;
        return Exprs.getChild((Expression) n, 0);
    }

    public static boolean isCompoundAssignment(Node node) {
        if (!(node instanceof Expression))
            return false;
        Expression store = (Expression) node;
        switch (store.getCode()) {
        case Store: {
            Expression expr = dropNarrowing(store.getArguments().get(0));
            if (!isBinaryMath(expr))
                return false;
            Expression load = expr.getArguments().get(0);
            return load.getCode() == AstCode.Load && Objects.equals(load.getOperand(), store.getOperand());
        }
        case PutField: {
            Expression expr = dropNarrowing(store.getArguments().get(1));
            if (!isBinaryMath(expr))
                return false;
            Expression load = expr.getArguments().get(0);
            return load.getCode() == AstCode.GetField
                && ((FieldReference) load.getOperand()).isEquivalentTo((MemberReference) store.getOperand())
                && Equi.equiExpressions(store.getArguments().get(0), load.getArguments().get(0));
        }
        case PutStatic: {
            Expression expr = dropNarrowing(store.getArguments().get(0));
            if (!isBinaryMath(expr))
                return false;
            Expression load = expr.getArguments().get(0);
            return load.getCode() == AstCode.GetStatic
                && ((FieldReference) load.getOperand()).isEquivalentTo((MemberReference) store.getOperand());
        }
        default:
            return false;
        }
    }

    private static Expression dropNarrowing(Expression expression) {
        switch(expression.getCode()) {
        case I2B:
        case I2C:
        case I2S:
        case L2I:
        case D2F:
        case D2I:
            return expression.getArguments().get(0);
        default:
            return expression;
        }
    }

    public static Node find(Node node, Predicate<Node> predicate) {
        if (predicate.test(node))
            return node;
        for (Node child : getChildren(node)) {
            Node result = find(child, predicate);
            if (result != null)
                return result;
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public static Iterable<Node> getChildren(Node node) {
        if(node instanceof Expression) {
            Expression expr = (Expression)node;
            Object operand = expr.getOperand();
            if(operand instanceof Lambda) {
                return Iterables.concat(expr.getArguments(), Collections.singleton((Node)operand));
            }
            return (Iterable<Node>)(Iterable<?>)expr.getArguments();
        }
        if(node instanceof Block) {
            Block block = (Block) node;
            Expression entryGoto = block.getEntryGoto();
            if(entryGoto != null) {
                return Iterables.concat(Collections.singleton(entryGoto), block.getBody());
            }
            return block.getBody();
        }
        
        return node.getChildren();
    }

    public static boolean isEmptyOrBreak(Block block) {
        List<Node> body = block.getBody();
        if (body.isEmpty())
            return true;
        if (body.size() > 1)
            return false;
        Node node = body.get(0);
        if (isOp(node, AstCode.LoopOrSwitchBreak) || isOp(node, AstCode.Return) || isOp(node, AstCode.LoopContinue)) {
            Expression expr = (Expression) node;
            if (expr.getOperand() == null && expr.getArguments().size() == 0)
                return true;
        }
        return false;
    }

    public static String getOperation(AstCode code) {
        switch (code) {
        case CmpEq:
            return "==";
        case CmpNe:
            return "!=";
        case CmpLe:
            return "<=";
        case CmpLt:
            return "<";
        case CmpGe:
            return ">=";
        case CmpGt:
            return ">";
        case And:
            return "&";
        case Or:
            return "|";
        case Xor:
            return "^";
        case Sub:
            return "-";
        case Div:
            return "/";
        case Rem:
            return "%";
        case LogicalAnd:
            return "&&";
        case LogicalOr:
            return "||";
        case Shl:
            return "<<";
        case Shr:
            return ">>";
        case UShr:
            return ">>>";
        default:
            return code.getName();
        }
    }

    public static MethodDefinition getLambdaMethod(Lambda l) {
        Object arg = l.getCallSite().getBootstrapArguments().get(1);
        if (arg instanceof MethodHandle) {
            MethodDefinition lm = ((MethodHandle) arg).getMethod().resolve();
            if (lm != null)
                return lm;
        }
        throw new InternalError("Unable to determine original method for lambda " + l);
    }

    public static int estimateCodeSize(Node node) {
        return node.getChildrenAndSelfRecursive().size();
    }

    public static MethodHandle getMethodHandle(DynamicCallSite dcs) {
        MethodHandle mh = dcs.getBootstrapMethodHandle();
        if (mh.getMethod().getDeclaringType().getInternalName().equals("java/lang/invoke/LambdaMetafactory")) {
            List<Object> args = dcs.getBootstrapArguments();
            if (args.size() > 1 && args.get(1) instanceof MethodHandle) {
                MethodHandle actualHandle = (MethodHandle) args.get(1);
                return actualHandle;
            }
        }
        return null;
    }

    public static boolean isThrow(Node node) {
        if (Nodes.isOp(node, AstCode.AThrow))
            return true;
        if (node instanceof Block) {
            List<Node> list = ((Block) node).getBody();
            if (list.size() == 1)
                return isThrow(list.get(0));
        }
        return false;
    }
    
    public static boolean isToFloatingPointConversion(Node node) {
        if(!(node instanceof Expression))
            return false;
        Expression expr = (Expression) node;
        return expr.getCode() == AstCode.I2F || expr.getCode() == AstCode.I2D || expr.getCode() == AstCode.L2F
                || expr.getCode() == AstCode.L2D;
    }
    
    public static String facts(Node node) {
        if(node instanceof Expression)
            return Annotators.facts((Expression) node);
        return "{}";
    }
}
