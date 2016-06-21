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
    public static String formatExpression(Expression expr) {
        return format(new StringBuilder(), expr).toString();
    }

    private static StringBuilder format(StringBuilder sb, Expression expr) {
        switch(expr.getCode()) {
        case And:
            return formatBinary(sb, expr, "&");
        case Or:
            return formatBinary(sb, expr, "|");
        case Xor:
            return formatBinary(sb, expr, "^");
        case Not:
            return formatBinary(sb, expr, "~");
        case Neg:
            return formatUnary(sb, expr, "-", "");
        case LogicalAnd:
            return formatBinary(sb, expr, "&&");
        case LogicalOr:
            return formatBinary(sb, expr, "||");
        case LogicalNot:
            return formatUnary(sb, expr, "!", "");
        case Add:
            return formatBinary(sb, expr, "+");
        case Sub:
            return formatBinary(sb, expr, "-");
        case Mul:
            return formatBinary(sb, expr, "*");
        case Div:
            return formatBinary(sb, expr, "/");
        case Shr:
            return formatBinary(sb, expr, ">>");
        case Shl:
            return formatBinary(sb, expr, "<<");
        case UShr:
            return formatBinary(sb, expr, ">>>");
        case CmpEq:
            return formatBinary(sb, expr, "==");
        case CmpNe:
            return formatBinary(sb, expr, "!=");
        case CmpLt:
            return formatBinary(sb, expr, "<");
        case CmpLe:
            return formatBinary(sb, expr, "<=");
        case CmpGt:
            return formatBinary(sb, expr, ">");
        case CmpGe:
            return formatBinary(sb, expr, ">=");
        case GetField: {
            FieldReference fr = (FieldReference) expr.getOperand();
            return formatUnary(sb, expr, "", "."+fr.getName());
        }
        case PutField: {
            FieldReference fr = (FieldReference) expr.getOperand();
            return format(format(sb, expr.getArguments().get(0)).append(".").append(fr.getName()).append(" = "), expr
                    .getArguments().get(1));
        }
        case GetStatic: {
            FieldReference fr = (FieldReference) expr.getOperand();
            return sb.append(fr.getDeclaringType().getSimpleName()).append(".").append(fr.getName());
        }
        case PutStatic: {
            FieldReference fr = (FieldReference) expr.getOperand();
            return format(sb.append(fr.getDeclaringType().getSimpleName()).append(".").append(fr.getName()).append(" = "), expr
                .getArguments().get(0));
        }
        case Return:
            return formatUnary(sb, expr, "return ", "");
        case AThrow:
            return formatUnary(sb, expr, "throw ", "");
        case ArrayLength:
            return formatUnary(sb, expr, "", ".length");
        case LoadElement:
            return format(format(sb, expr.getArguments().get(0)).append("["), expr.getArguments().get(1)).append("]");
        case StoreElement:
            return format(format(format(sb, expr.getArguments().get(0)).append("["), expr.getArguments().get(1))
                    .append("] = "), expr.getArguments().get(2));
        case Load: {
            Object op = expr.getOperand();
            return sb.append(op instanceof Variable ? ((Variable) op).getName() : ((ParameterDefinition) op).getName());
        }
        case Store:
            return format(sb.append(((Variable)expr.getOperand()).getName()).append(" = "), expr.getArguments().get(0));
        case I2B:
            return formatUnary(sb, expr, "(byte)", "");
        case I2C:
            return formatUnary(sb, expr, "(char)", "");
        case I2S:
            return formatUnary(sb, expr, "(short)", "");
        case I2F:
        case L2F:
        case D2F:
            return formatUnary(sb, expr, "(float)", "");
        case F2I:
        case D2I:
        case L2I:
            return formatUnary(sb, expr, "(int)", "");
        case F2L:
        case D2L:
            return formatUnary(sb, expr, "(long)", "");
        case I2D:
        case I2L:
        case L2D:
        case F2D:
            return formatUnary(sb, expr, "", "");
        case CheckCast:
            return formatUnary(sb, expr, "("+((TypeReference)expr.getOperand()).getSimpleName()+")(", ")");
        case InstanceOf:
            return formatUnary(sb, expr, "", " instanceof "+((TypeReference)expr.getOperand()).getSimpleName());
        case InvokeStatic: {
            MethodReference mr = (MethodReference) expr.getOperand();
            if(Nodes.isBoxing(expr))
                return formatUnary(sb, expr, "("+mr.getDeclaringType().getSimpleName()+")(", ")");
            sb.append(mr.getDeclaringType().getSimpleName()).append(".").append(mr.getName()).append("(");
            boolean firstArg = true;
            for(Expression child : expr.getArguments()) {
                if(!firstArg) {
                    sb.append(", ");
                } else {
                    firstArg = false;
                }
                format(sb, child);
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
                format(sb, child);
            }
            return sb.append(")");
        }
        case AConstNull:
            return sb.append("null");
        case InvokeSpecial:
        case InvokeInterface:
        case InvokeVirtual: {
            if(Nodes.isUnboxing(expr))
                return format(sb, expr.getArguments().get(0));
            MethodReference mr = (MethodReference) expr.getOperand();
            format(sb, expr.getArguments().get(0));
            sb.append(".").append(mr.getName()).append("(");
            boolean firstArg = true;
            for(int i=1; i<expr.getArguments().size(); i++) {
                Expression child = expr.getArguments().get(i);
                if(!firstArg) {
                    sb.append(", ");
                } else {
                    firstArg = false;
                }
                format(sb, child);
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
        default: 
            return sb.append(expr.toString());
        }
    }

    private static StringBuilder formatUnary(StringBuilder sb, Expression expr, String prefix, String postfix) {
        return format(sb.append(prefix), expr.getArguments().get(0)).append(postfix);
    }

    private static StringBuilder formatBinary(StringBuilder sb, Expression expr, String op) {
        return format(format(sb, expr.getArguments().get(0)).append(" ").append(op).append(" "), expr.getArguments()
                .get(1));
    }
}
