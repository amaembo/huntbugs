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

import java.util.Set;
import java.util.stream.Collectors;

import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Loop;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Roles;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "Correctness", name = "InfiniteLoop", maxScore = 90)
@WarningDefinition(category = "Correctness", name = "InvariantLoopCondition", maxScore = 60)
@WarningDefinition(category = "Correctness", name = "InvariantLoopConditionPart", maxScore = 55)
public class InfiniteLoop {
    @AstVisitor
    public void visit(Node node, MethodContext mc) {
        if (node instanceof Loop) {
            Loop loop = (Loop) node;
            if (loop.getCondition() == null)
                return;
            Expression expr = loop.getCondition();
            if(!Nodes.isSideEffectFree(expr))
                return;
            checkCondition(mc, loop, expr, null);
        }
    }

    private void checkCondition(MethodContext mc, Loop loop, Expression expr, AstCode parent) {
        if((expr.getCode() == AstCode.LogicalAnd || expr.getCode() == AstCode.LogicalOr)
                && (parent == null || expr.getCode() == parent)) {
            // suppress warning for quite common scenario like "for(int x = 0; data != null && x < data.length; x++)"
            if(!Nodes.isNullCheck(expr.getArguments().get(0))) {
                checkCondition(mc, loop, expr.getArguments().get(0), expr.getCode());
            }
            checkCondition(mc, loop, expr.getArguments().get(1), expr.getCode());
            return;
        }
        if (expr.getCode() == AstCode.LogicalNot) {
            checkCondition(mc, loop, expr.getArguments().get(0), parent == AstCode.LogicalAnd ? AstCode.LogicalOr
                    : parent == AstCode.LogicalOr ? AstCode.LogicalAnd : parent);
            return;
        }
        // Will be reported as ResultOfComparisonIsStaticallyKnown
        if (expr.getCode().isComparison() && Nodes.getConstant(expr) != null)
            return;
        if (!Nodes.isPure(expr))
            return;
        Set<Variable> vars = Exprs.stream(expr).filter(e -> e.getCode() == AstCode.Load).map(
            e -> (Variable) e.getOperand()).collect(Collectors.toSet());
        if(vars.isEmpty())
            return;
        class LoopState {
            boolean hasControlFlow, hasLoads, hasStores;
        }
        LoopState ls = new LoopState();
        loop.getBody().getChildrenAndSelfRecursive().forEach(n -> {
            if(!(n instanceof Expression))
                return;
            Expression e = (Expression) n;
            if (e.getCode() == AstCode.LoopOrSwitchBreak || e.getCode() == AstCode.Return
                    || e.getCode() == AstCode.AThrow || e.getCode() == AstCode.Goto)
                    ls.hasControlFlow = true;
            if (e.getOperand() instanceof Variable && vars.contains(e.getOperand())) {
                ls.hasLoads = true;
                if(e.getCode() == AstCode.Store || e.getCode() == AstCode.Inc)
                    ls.hasStores = true;
            }
            if (e.getCode() == AstCode.PreIncrement || e.getCode() == AstCode.PostIncrement) {
                if(vars.contains(e.getArguments().get(0).getOperand()))
                    ls.hasStores = true;
            }
        });
        if(parent == null) {
            if(!ls.hasControlFlow && !ls.hasStores) {
                mc.report("InfiniteLoop", 0, loop, Roles.VARIABLE.create(vars.iterator().next().getName()),
                    Roles.EXPRESSION.create(expr));
            } else if(!ls.hasLoads) {
                mc.report("InvariantLoopCondition", 0, expr, Roles.VARIABLE.create(vars.iterator().next().getName()),
                    Roles.EXPRESSION.create(expr));
            }
        } else if((!ls.hasControlFlow && !ls.hasStores) || !ls.hasLoads) {
            mc.report("InvariantLoopConditionPart", 0, expr, Roles.VARIABLE.create(vars.iterator().next().getName()),
                Roles.EXPRESSION.create(expr));
        }
    }
}
