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

import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.Exceptional;
import one.util.huntbugs.flow.Inf;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation.TypeInfo;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="ExceptionalExpression", maxScore=80)
public class ExceptionalExpression {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc) {
        Object constValue = Inf.CONST.getValue(expr);
        if(constValue instanceof Exceptional) {
            int priority = 0;
            TypeInfo exc = ((Exceptional) constValue).getType();
            if(nc.isInTry(exc.getTypeName()))
                priority += 30;
            mc.report("ExceptionalExpression", priority, expr, Roles.EXPRESSION.create(expr), Roles.EXCEPTION
                    .create(exc));
        }
    }
}
