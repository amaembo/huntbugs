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
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import one.util.huntbugs.flow.Inf;
import one.util.huntbugs.flow.ValuesFlow;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.VariableDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

/**
 * Expression-related utility methods
 * 
 * @author Tagir Valeev
 */
public class Exprs {

    private static boolean isAssertionStatusCheck(Expression expr) {
        if(expr.getCode() != AstCode.LogicalNot)
            return false;
        Expression arg = expr.getArguments().get(0);
        if(arg.getCode() != AstCode.GetStatic)
            return false;
        FieldReference fr = (FieldReference) arg.getOperand();
        return fr.getName().startsWith("$assertions");
    }

    private static boolean isAssertionCondition(Expression expr) {
        if(expr.getCode() != AstCode.LogicalAnd)
            return false;
        return expr.getArguments().stream().anyMatch(Exprs::isAssertionStatusCheck);
    }

    private static boolean isAssertionMethod(Expression expr) {
        if(expr.getCode() != AstCode.InvokeStatic)
            return false;
        MethodReference mr = (MethodReference) expr.getOperand();
        String name = mr.getName();
        return name.equals("isTrue") || name.equals("assertTrue");
    }

    public static boolean isAssertion(Expression expr) {
        Set<Expression> usages = Inf.BACKLINK.findUsages(expr);
        return !usages.isEmpty() && usages.stream().allMatch(parent -> isAssertionCondition(parent) || isAssertion(parent) || isAssertionMethod(parent));
    }

    public static Expression getChild(Expression node, int i) {
        return ValuesFlow.getSource(node.getArguments().get(i));
    }

    public static Expression getChildNoSpecial(Expression node, int i) {
        Expression arg = node.getArguments().get(i);
        Expression src = ValuesFlow.getSource(arg);
        if(ValuesFlow.isSpecial(src))
            return arg;
        return src;
    }

    public static Expression getThis(Expression node) {
        if (node.getCode() == AstCode.GetField || node.getCode() == AstCode.PutField)
            return node.getArguments().get(0);
        if (node.getCode() == AstCode.GetStatic || node.getCode() == AstCode.PutStatic)
            return null;
        throw new IllegalArgumentException(node + ": expected field operation");
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

    public static Stream<Expression> stream(Expression expr) {
        return Stream.concat(Stream.of(expr), expr.getArguments().stream().flatMap(Exprs::stream));
    }

    public static boolean bothMatch(Expression e1, Expression e2, Predicate<Expression> p1, Predicate<Expression> p2) {
        return p1.test(e1) && p2.test(e2) || p1.test(e2) && p2.test(e1);
    }

    public static boolean bothChildrenMatch(Expression e, Predicate<Expression> p1, Predicate<Expression> p2) {
        List<Expression> args = e.getArguments();
        if(args.size() != 2)
            throw new IllegalArgumentException("Children size = "+args.size()+"; expr = "+e);
        return bothMatch(ValuesFlow.getSource(args.get(0)), ValuesFlow.getSource(args.get(1)), p1, p2);
    }
}
