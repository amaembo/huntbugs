/*
 * Copyright 2015, 2016 Tagir Valeev
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
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Correctness", name = "InfiniteLoop", maxScore = 80)
@WarningDefinition(category = "Correctness", name = "InvariantLoopCondition", maxScore = 55)
public class InfiniteLoop {
    @AstVisitor
    public void visit(Node node, MethodContext mc) {
        if (node instanceof Loop) {
            Loop loop = (Loop) node;
            if (loop.getCondition() == null)
                return;
            Expression expr = loop.getCondition();
            if (!Nodes.isPure(expr))
                return;
            Set<Variable> vars = Nodes.stream(expr).filter(e -> e.getCode() == AstCode.Load).map(
                e -> (Variable) e.getOperand()).collect(Collectors.toSet());
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
            if(!ls.hasControlFlow && !ls.hasStores) {
                mc.report("InfiniteLoop", 0, node);
            } else if(!ls.hasLoads) {
                mc.report("InvariantLoopCondition", 0, node);
            }
        }
    }
}
