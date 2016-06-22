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

import one.util.huntbugs.warning.Formatter;

import com.strobel.assembler.metadata.DynamicCallSite;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodHandle;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

/**
 * @author lan
 *
 */
public class ExpressionFormatter {
    public static final int DEF_PRECEDENCE = 100;

    public static String formatExpression(Expression expr) {
        return format(new StringBuilder(), expr, DEF_PRECEDENCE).toString();
    }

    private static StringBuilder format(StringBuilder sb, Expression expr, int outerPrecedence) {
        switch(expr.getCode()) {
        case And:
            return formatBinary(sb, expr, " & ", 9, outerPrecedence);
        case Or:
            return formatBinary(sb, expr, " | ", 11, outerPrecedence);
        case Xor:
            return formatBinary(sb, expr, " ^ ", 10, outerPrecedence);
        case Not:
            return formatUnary(sb, expr, "~", "", 2, outerPrecedence);
        case Neg:
            return formatUnary(sb, expr, "-", "", 2, outerPrecedence);
        case LogicalAnd:
            return formatBinary(sb, expr, " && ", 12, outerPrecedence);
        case LogicalOr:
            return formatBinary(sb, expr, " || ", 13, outerPrecedence);
        case LogicalNot:
            return formatUnary(sb, expr, "!", "", 2, outerPrecedence);
        case Add:
            return formatBinary(sb, expr, " + ", 5, outerPrecedence);
        case Sub:
            return formatBinary(sb, expr, " - ", 5, outerPrecedence);
        case Mul:
            return formatBinary(sb, expr, " * ", 4, outerPrecedence);
        case Div:
            return formatBinary(sb, expr, " / ", 4, outerPrecedence);
        case Rem:
            return formatBinary(sb, expr, " % ", 4, outerPrecedence);
        case Shr:
            return formatBinary(sb, expr, " >> ", 6, outerPrecedence);
        case Shl:
            return formatBinary(sb, expr, " << ", 6, outerPrecedence);
        case UShr:
            return formatBinary(sb, expr, " >>> ", 6, outerPrecedence);
        case CmpEq:
            return formatBinary(sb, expr, " == ", 8, outerPrecedence);
        case CmpNe:
            return formatBinary(sb, expr, " != ", 8, outerPrecedence);
        case CmpLt:
            return formatBinary(sb, expr, " < ", 7, outerPrecedence);
        case CmpLe:
            return formatBinary(sb, expr, " <= ", 7, outerPrecedence);
        case CmpGt:
            return formatBinary(sb, expr, " > ", 7, outerPrecedence);
        case CmpGe:
            return formatBinary(sb, expr, " >= ", 7, outerPrecedence);
        case GetField: {
            FieldReference fr = (FieldReference) expr.getOperand();
            return formatUnary(sb, expr, "", "."+fr.getName(), 1, outerPrecedence);
        }
        case PutField: {
            FieldReference fr = (FieldReference) expr.getOperand();
            return formatBinary(sb, expr, "."+fr.getName()+" = ", 15, outerPrecedence);
        }
        case GetStatic: {
            FieldReference fr = (FieldReference) expr.getOperand();
            return sb.append(fr.getDeclaringType().getSimpleName()).append(".").append(fr.getName());
        }
        case PutStatic: {
            FieldReference fr = (FieldReference) expr.getOperand();
            return formatUnary(sb, expr, fr.getDeclaringType().getSimpleName() + "." + fr.getName() + " = ", "", 15,
                outerPrecedence);
        }
        case Return:
            return formatUnary(sb, expr, "return ", "", DEF_PRECEDENCE, outerPrecedence);
        case AThrow:
            return formatUnary(sb, expr, "throw ", "", DEF_PRECEDENCE, outerPrecedence);
        case ArrayLength:
            return formatUnary(sb, expr, "", ".length", 1, outerPrecedence);
        case LoadElement:
            return format(format(sb, expr.getArguments().get(0), outerPrecedence).append("["), expr.getArguments().get(1), outerPrecedence).append("]");
        case StoreElement:
            return format(format(format(sb, expr.getArguments().get(0), outerPrecedence).append("["), expr.getArguments().get(1), outerPrecedence)
                    .append("] = "), expr.getArguments().get(2), outerPrecedence);
        case Load: {
            Object op = expr.getOperand();
            return sb.append(op instanceof Variable ? ((Variable) op).getName() : ((ParameterDefinition) op).getName());
        }
        case Store:
            return formatUnary(sb, expr, ((Variable)expr.getOperand()).getName()+ " = ", "", 15, outerPrecedence);
        case I2B:
            return formatUnary(sb, expr, "(byte)", "", 3, outerPrecedence);
        case I2C:
            return formatUnary(sb, expr, "(char)", "", 3, outerPrecedence);
        case I2S:
            return formatUnary(sb, expr, "(short)", "", 3, outerPrecedence);
        case I2F:
        case L2F:
        case D2F:
            return formatUnary(sb, expr, "(float)", "", 3, outerPrecedence);
        case F2I:
        case D2I:
        case L2I:
            return formatUnary(sb, expr, "(int)", "", 3, outerPrecedence);
        case F2L:
        case D2L:
            return formatUnary(sb, expr, "(long)", "", 3, outerPrecedence);
        case I2D:
        case I2L:
        case L2D:
        case F2D:
            return format(sb, expr.getArguments().get(0), outerPrecedence);
        case CheckCast:
            return formatUnary(sb, expr, "("+((TypeReference)expr.getOperand()).getSimpleName()+")(", ")", 3, outerPrecedence);
        case InstanceOf:
            return formatUnary(sb, expr, "", " instanceof "+((TypeReference)expr.getOperand()).getSimpleName(), 7, outerPrecedence);
        case InvokeStatic: {
            MethodReference mr = (MethodReference) expr.getOperand();
            if(Nodes.isBoxing(expr))
                return formatUnary(sb, expr, "("+mr.getDeclaringType().getSimpleName()+")(", ")", 3, outerPrecedence);
            sb.append(mr.getDeclaringType().getSimpleName()).append(".").append(mr.getName()).append("(");
            boolean firstArg = true;
            for(Expression child : expr.getArguments()) {
                if(!firstArg) {
                    sb.append(", ");
                } else {
                    firstArg = false;
                }
                format(sb, child, DEF_PRECEDENCE);
            }
            return sb.append(")");
        }
        case InitObject: {
            MethodReference mr = (MethodReference) expr.getOperand();
            sb.append("new ").append(mr.getDeclaringType().getSimpleName()).append("(");
            boolean firstArg = true;
            for(Expression child : expr.getArguments()) {
                if(!firstArg) {
                    sb.append(", ");
                } else {
                    firstArg = false;
                }
                format(sb, child, DEF_PRECEDENCE);
            }
            return sb.append(")");
        }
        case AConstNull:
            return sb.append("null");
        case InvokeSpecial:
        case InvokeInterface:
        case InvokeVirtual: {
            if(Nodes.isUnboxing(expr))
                return format(sb, expr.getArguments().get(0), outerPrecedence);
            MethodReference mr = (MethodReference) expr.getOperand();
            format(sb, expr.getArguments().get(0), 1);
            sb.append(".").append(mr.getName()).append("(");
            boolean firstArg = true;
            for(int i=1; i<expr.getArguments().size(); i++) {
                Expression child = expr.getArguments().get(i);
                if(!firstArg) {
                    sb.append(", ");
                } else {
                    firstArg = false;
                }
                format(sb, child, DEF_PRECEDENCE);
            }
            return sb.append(")");
        }
        case InvokeDynamic: {
            MethodHandle mh = Nodes.getMethodHandle((DynamicCallSite)expr.getOperand());
            if(mh != null) {
                MethodReference mr = mh.getMethod();
                return sb.append(mr.getDeclaringType().getSimpleName()).append("::").append(mr.getName());
            }
            return sb.append(expr.toString());
        }
        case LdC: {
            return sb.append(Formatter.formatConstant(expr.getOperand()));
        }
        case PreIncrement: {
            Object op = expr.getOperand();
            if(op instanceof Integer) {
                int amount = (int)op;
                if(amount == 1) {
                    return formatUnary(sb, expr, "++", "", 2, outerPrecedence);
                } else if(amount == -1) {
                    return formatUnary(sb, expr, "--", "", 2, outerPrecedence);
                }
            }
            return sb.append(expr.toString());
        }
        case PostIncrement: {
            Object op = expr.getOperand();
            if(op instanceof Integer) {
                int amount = (int)op;
                if(amount == 1) {
                    return formatUnary(sb, expr, "", "++", 2, outerPrecedence);
                } else if(amount == -1) {
                    return formatUnary(sb, expr, "", "--", 2, outerPrecedence);
                }
            }
            return sb.append(expr.toString());
        }
        default: 
            return sb.append(expr.toString());
        }
    }

    private static StringBuilder formatUnary(StringBuilder sb, Expression expr, String prefix, String postfix, int precedence, int outerPrecedence) {
        if(precedence > outerPrecedence) {
            return format(sb.append("(").append(prefix), expr.getArguments().get(0), precedence).append(postfix).append(")");
        }
        return format(sb.append(prefix), expr.getArguments().get(0), precedence).append(postfix);
    }

    private static StringBuilder formatBinary(StringBuilder sb, Expression expr, String op, int precedence, int outerPrecedence) {
        if(precedence > outerPrecedence) {
            return format(
                format(sb.append("("), expr.getArguments().get(0), precedence).append(op),
                expr.getArguments().get(1), precedence).append(")");
        }
        return format(format(sb, expr.getArguments().get(0), precedence).append(op), expr
                .getArguments().get(1), precedence);
    }
}
