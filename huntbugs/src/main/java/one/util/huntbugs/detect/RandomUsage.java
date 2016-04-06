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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodeVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Performance", name="RandomNextIntViaNextDouble", baseRank=50)
@WarningDefinition(category="Correctness", name="RandomDoubleToInt", baseRank=80)
public class RandomUsage {

    @AstNodeVisitor
    public void visit(Node node, MethodContext ctx) {
        if(Nodes.isOp(node, AstCode.D2I)) {
            if(isRandomDouble(Nodes.getOperand(node, 0))) {
                ctx.report("RandomDoubleToInt", 0, node);
            }
            Expression mul = ((Expression)node).getArguments().get(0);
            if(mul.getCode() == AstCode.Mul) {
                mul.getArguments().stream().filter(this::isRandomDouble).findFirst().ifPresent(expr -> {
                    int priority = 0;
                    if(((MethodReference)expr.getOperand()).getDeclaringType().getInternalName().equals("java/lang/Math"))
                        priority = -20;
                    ctx.report("RandomNextIntViaNextDouble", priority, node);
                });
            }
        }
    }
    
    private boolean isRandomDouble(Node node) {
        if(Nodes.isInvoke(node)) {
            MethodReference mr = (MethodReference) ((Expression)node).getOperand();
            if(mr.getSignature().equals("()D") && (Types.isRandomClass(mr.getDeclaringType()) && mr.getName().equals("nextDouble")
                    || mr.getDeclaringType().getInternalName().equals("java/lang/Math") && mr.getName().equals("random"))) {
                return true;
            }
        }
        return false;
    }
}
