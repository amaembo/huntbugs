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

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "BadPractice", name = "FloatComparison", maxScore = 40)
public class FloatingPointComparison {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression node, MethodContext ctx, MethodDefinition md) {
        if (node.getCode() != AstCode.CmpEq && node.getCode() != AstCode.CmpNe)
            return;
        List<Expression> args = node.getArguments();
        TypeReference inferredType = args.get(0).getInferredType();
        if(inferredType == null)
            return;
        JvmType type = inferredType.getSimpleType();
        if (type != JvmType.Double && type != JvmType.Float)
            return;
        Expression leftArg = Nodes.getChild(node, 0);
        Expression rightArg = Nodes.getChild(node, 1);
        Object left = Nodes.getConstant(leftArg);
        Object right = Nodes.getConstant(rightArg);
        int priority = tweakPriority(args.get(0)) + tweakPriority(args.get(1));
        if(ValuesFlow.anyMatch(leftArg, rightArg::equals) || ValuesFlow.anyMatch(rightArg, leftArg::equals))
            priority += 20;
        if(md.getName().toLowerCase(Locale.ENGLISH).contains("equal"))
            priority += 20;
        Number n = left instanceof Number ? (Number) left : right instanceof Number ? (Number) right : null;
        if(n != null)
            ctx.report("FloatComparison", priority, node, Roles.NUMBER.create(n));
        else
            ctx.report("FloatComparison", priority, node);
    }

    private int tweakPriority(Expression expr) {
        Object val = Nodes.getConstant(expr);
        if (val instanceof Double || val instanceof Float) {
            double v = ((Number) val).doubleValue();
            float f = ((Number) val).floatValue();
            if (v == 0.0 || Double.isInfinite(v) || Double.isNaN(v))
                return 50;
            if (v == 1.0 || v == 2.0 || v == -1.0 || v == -2.0 || v == Double.MIN_VALUE || v == Double.MAX_VALUE
                || v == -Double.MAX_VALUE || v == -Double.MIN_VALUE || f == Float.MAX_VALUE || f == Float.MIN_VALUE ||
                f == -Float.MAX_VALUE || f == -Float.MIN_VALUE)
                return 40;
            if (v == 3.0 || v == 4.0 || v == -3.0 || v == -4.0)
                return 30;
            int prec = new BigDecimal(v).precision();
            if (prec < 3)
                return 25;
            if (prec < 7)
                return 20;
            if (prec < 10)
                return 15;
            return 5;
        }
        if (expr.getCode() == AstCode.InvokeStatic) {
            MethodReference method = (MethodReference) expr.getOperand();
            if(method.getName().equals("floor") || method.getName().equals("round") || method.getName().equals("rint")) {
                return method.getDeclaringType().getInternalName().equals("java/lang/Math") ? 50 : 35;
            }
        }
        return 0;
    }
}
