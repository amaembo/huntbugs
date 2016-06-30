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
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="BadResultSetArgument", maxScore=75)
@WarningDefinition(category="Correctness", name="BadPreparedStatementArgument", maxScore=75)
public class SqlBadArgument {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.InvokeInterface) {
            MethodReference mr = (MethodReference) expr.getOperand();
            String warningType = null;
            if (mr.getDeclaringType().getInternalName().equals("java/sql/ResultSet")
                && (mr.getName().startsWith("get") || mr.getName().startsWith("update"))
                && firstParameterIsInt(mr)) {
                warningType = "BadResultSetArgument";
            } else if(mr.getDeclaringType().getInternalName().equals("java/sql/PreparedStatement") &&
                    mr.getName().startsWith("set") && firstParameterIsInt(mr)) {
                warningType = "BadPreparedStatementArgument";
            }
            if(warningType != null) {
                Expression arg = expr.getArguments().get(1);
                Object constant = Nodes.getConstant(arg);
                if (Integer.valueOf(0).equals(constant)) {
                    mc.report(warningType, 0, expr);
                } else if (ValuesFlow.reduce(arg, e -> Integer.valueOf(0).equals(Nodes.getConstant(e)),
                    Boolean::logicalOr, Boolean::booleanValue)) {
                    mc.report(warningType, 20, expr);
                }
            }
        }
    }
    
    private boolean firstParameterIsInt(MethodReference mr) {
        return mr.getParameters().size() > 0
            && mr.getParameters().get(0).getParameterType().getSimpleType() == JvmType.Integer;
    }
}
