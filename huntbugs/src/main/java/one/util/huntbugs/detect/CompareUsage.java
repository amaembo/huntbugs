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

import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "BadPractice", name = "NegatingComparatorResult", maxScore = 60)
@WarningDefinition(category = "Correctness", name = "ComparingComparatorResultWithNumber", maxScore = 70)
public class CompareUsage {
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if (expr.getCode() == AstCode.Neg) {
            Expression child = ValuesFlow.findFirst(Nodes.getChild(expr, 0), this::isCompare);
            if (child != null) {
                mc.report("NegatingComparatorResult", 0, expr, Roles.CALLED_METHOD.create((MethodReference) child
                        .getOperand()));
            }
        }
        if (expr.getCode() == AstCode.CmpEq || expr.getCode() == AstCode.CmpNe) {
            Nodes.ifBinaryWithConst(expr, (arg, constant) -> {
                if (constant instanceof Integer && (int) constant != 0) {
                    Expression child = ValuesFlow.findFirst(ValuesFlow.getSource(arg), this::isCompare);
                    if (child != null) {
                        mc.report("ComparingComparatorResultWithNumber", 0, expr, Roles.CALLED_METHOD.create(
                            (MemberReference) child.getOperand()), Roles.NUMBER.create((Number) constant));
                    }
                }
            });
        }
    }

    private boolean isCompare(Expression child) {
        if (child.getCode() == AstCode.InvokeVirtual || child.getCode() == AstCode.InvokeSpecial
            || child.getCode() == AstCode.InvokeInterface) {
            MethodReference mr = (MethodReference) child.getOperand();
            if (mr.getReturnType().getSimpleType() == JvmType.Integer
                && ((mr.getName().equals("compare") && Types.isInstance(mr.getDeclaringType(), "java/util/Comparator")) || mr
                        .getName().equals("compareTo"))) {
                return true;
            }
        }
        return false;
    }
}
