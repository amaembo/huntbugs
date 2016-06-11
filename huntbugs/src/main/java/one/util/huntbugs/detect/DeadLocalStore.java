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

import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.warning.Role.StringRole;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category="RedundantCode", name="DeadStoreInReturn", maxScore=50)
@WarningDefinition(category="RedundantCode", name="DeadIncrementInReturn", maxScore=60)
@WarningDefinition(category="RedundantCode", name="DeadIncrementInAssignment", maxScore=60)
public class DeadLocalStore {
    private static final StringRole EXPRESSION = StringRole.forName("EXPRESSION");
    
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.Return && expr.getArguments().size() == 1) {
            Expression arg = expr.getArguments().get(0);
            if(arg.getCode() == AstCode.Store) {
                mc.report("DeadStoreInReturn", 0, arg);
            } else if(arg.getCode() == AstCode.PreIncrement || arg.getCode() == AstCode.PostIncrement) {
                Expression var = arg.getArguments().get(0);
                if(var.getOperand() instanceof Variable)
                    mc.report("DeadIncrementInReturn", 0, var);
            }
        }
        if(expr.getCode() == AstCode.Store) {
            Variable var = (Variable) expr.getOperand();
            Expression arg = expr.getArguments().get(0);
            if(arg.getCode() == AstCode.PostIncrement) { // XXX: bug in Procyon? Seems that should be PreIncrement
                Expression load = arg.getArguments().get(0);
                if(load.getCode() == AstCode.Load && var.equals(load.getOperand()) && Integer.valueOf(1).equals(arg.getOperand())) {
                    mc.report("DeadIncrementInAssignment", 0, expr, EXPRESSION.create(var.getName() + " = " + var
                            .getName() + "++"));
                }
            }
        }
    }
}
