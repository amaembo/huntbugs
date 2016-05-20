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

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import one.util.huntbugs.flow.ValuesFlow;

import com.strobel.assembler.metadata.DynamicCallSite;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodHandle;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.assembler.metadata.VariableDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Lambda;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.TryCatchBlock;
import com.strobel.decompiler.ast.Variable;

/**
 * @author Tagir Valeev
 *
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

    public static Expression getChild(Expression node, int i) {
        return ValuesFlow.getSource(node.getArguments().get(i));
    }

    public static Object getConstant(Node node) {
        if (!(node instanceof Expression))
            return null;
        Expression expr = (Expression) node;
        if (expr.getCode() == AstCode.LdC)
            return expr.getOperand();
        return ValuesFlow.getValue(expr);
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

    public static boolean isComparison(Node node) {
        if (!(node instanceof Expression) || node.getChildren().size() != 2)
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
        if (!(node instanceof Expression) || node.getChildren().size() != 2)
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

    public static Expression getThis(Expression node) {
        if (node.getCode() == AstCode.GetField || node.getCode() == AstCode.PutField)
            return node.getArguments().get(0);
        if (node.getCode() == AstCode.GetStatic || node.getCode() == AstCode.PutStatic)
            return null;
        throw new IllegalArgumentException(node + ": expected field operation");
    }

    public static boolean isEquivalent(Node expr1, Node expr2) {
        if (expr1 == expr2)
            return true;
        if (expr1 == null)
            return expr2 == null;
        if (expr1 instanceof Expression && expr2 instanceof Expression)
            return Equi.equiExpressions((Expression) expr1, (Expression) expr2) && isSideEffectFree(expr1);
        return false;
    }

    public static boolean isSideEffectFreeMethod(Node node) {
        if (!(node instanceof Expression))
            return false;
        Object operand = ((Expression) node).getOperand();
        if (!(operand instanceof MethodReference))
            return false;
        MethodReference mr = (MethodReference) operand;
        return Methods.isSideEffectFree(mr);
    }

    public static boolean isSideEffectFree(Node node) {
        if (node == null)
            return true;
        if (!(node instanceof Expression))
            return false;
        Expression expr = (Expression) node;
        switch (expr.getCode()) {
        case PreIncrement:
        case PostIncrement:
        case InvokeDynamic:
        case Store:
        case StoreElement:
        case CompoundAssignment:
        case PutField:
        case PutStatic:
        case NewArray:
        case InitArray:
        case InitObject:
            return false;
        case InvokeSpecial:
        case InvokeStatic:
        case InvokeVirtual:
        case InvokeInterface:
            if (!Methods.isSideEffectFree((MethodReference) expr.getOperand()))
                return false;
        default:
            for (Expression child : expr.getArguments()) {
                if (!isSideEffectFree(child))
                    return false;
            }
        }
        return true;
    }

    public static boolean isPure(Node node) {
        if (node == null)
            return true;
        if (!(node instanceof Expression))
            return false;
        Expression expr = (Expression) node;
        switch (expr.getCode()) {
        case PreIncrement:
        case PostIncrement:
        case InvokeDynamic:
        case Store:
        case StoreElement:
        case CompoundAssignment:
        case PutField:
        case PutStatic:
        case NewArray:
        case InitArray:
        case InitObject:
        case GetField:
        case GetStatic:
        case LoadElement:
            return false;
        case InvokeSpecial:
        case InvokeStatic:
        case InvokeVirtual:
        case InvokeInterface:
            if (!Methods.isPure((MethodReference) expr.getOperand()))
                return false;
        default:
            for (Expression child : expr.getArguments()) {
                if (!isPure(child))
                    return false;
            }
        }
        return true;
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
        return Nodes.getChild((Expression) n, 0);
    }

    public static boolean isCompoundAssignment(Node node) {
        if (!(node instanceof Expression))
            return false;
        Expression store = (Expression) node;
        if (store.getCode() != AstCode.Store)
            return false;
        Expression expr = store.getArguments().get(0);
        if (!isBinaryMath(expr))
            return false;
        Expression load = expr.getArguments().get(0);
        return load.getCode() == AstCode.Load && Objects.equals(load.getOperand(), store.getOperand());
    }

    public static Node find(Node node, Predicate<Node> predicate) {
        if (predicate.test(node))
            return node;
        for (Node child : node.getChildren()) {
            Node result = find(child, predicate);
            if (result != null)
                return result;
        }
        return null;
    }

    public static Expression findExpression(Expression node, Predicate<Expression> predicate) {
        if (predicate.test(node))
            return node;
        for (Expression child : node.getArguments()) {
            Expression result = findExpression(child, predicate);
            if (result != null)
                return result;
        }
        return null;
    }

    public static Expression findExpressionWithSources(Expression node, Predicate<Expression> predicate) {
        if (predicate.test(node))
            return node;
        for (Expression child : node.getArguments()) {
            Expression result = findExpressionWithSources(child, predicate);
            if (result != null)
                return result;
        }
        Expression source = ValuesFlow.getSource(node);
        if (source != node) {
            Expression result = findExpressionWithSources(source, predicate);
            if (result != null)
                return result;
        }
        return null;
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

    public static boolean isThis(Expression self) {
        if (self.getCode() != AstCode.Load)
            return false;
        if (self.getOperand() instanceof Variable) {
            VariableDefinition origVar = ((Variable) self.getOperand()).getOriginalVariable();
            return origVar != null && origVar.getSlot() == 0;
        }
        if (self.getOperand() instanceof ParameterDefinition) {
            ParameterDefinition pd = (ParameterDefinition) self.getOperand();
            return pd.getSlot() == 0;
        }
        return false;
    }

    public static boolean isParameter(Expression self) {
        if (self.getCode() == AstCode.Load) {
            if (self.getOperand() instanceof ParameterDefinition)
                return true;
            if (self.getOperand() instanceof Variable && ((Variable) self.getOperand()).getOriginalParameter() != null)
                return true;
        }
        return false;
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

    public static Stream<Expression> stream(Expression expr) {
        return Stream.concat(Stream.of(expr), expr.getArguments().stream().flatMap(Nodes::stream));
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
}
