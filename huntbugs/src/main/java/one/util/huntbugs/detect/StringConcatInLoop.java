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

import java.util.ArrayList;
import java.util.List;

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Loop;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Switch;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Performance", name = "StringConcatInLoop", maxScore = 60)
public class StringConcatInLoop {
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression store, NodeChain nc, MethodContext mc) {
        if (store.getCode() != AstCode.Store)
            return;
        Variable var = (Variable) store.getOperand();
        Expression stringExpr = store.getArguments().get(0);
        if (!isStringBuilderToString(stringExpr))
            return;
        Expression appendSource = getAppendSource(stringExpr);
        if (appendSource == null)
            return;
        if (appendSource.getCode() != AstCode.Load)
            return;
        Variable loadVar = (Variable) appendSource.getOperand();
        if (loadVar != var)
            return;
        // String concatenation like a += x+y+z found
        if (!ValuesFlow.hasPhiSource(appendSource))
            return;
        List<Expression> phiArgs = new ArrayList<>(ValuesFlow.getSource(appendSource).getArguments());
        phiArgs.remove(stringExpr);
        NodeChain parent = nc;
        int priority = 0;
        while (parent != null) {
            Node node = parent.getNode();
            if (node instanceof Condition || node instanceof Switch)
                priority += 5;
            if (node instanceof Loop && !phiArgs.stream().allMatch(src -> Nodes.find(node, src::equals) != null)) {
                mc.report("StringConcatInLoop", priority, store);
                return;
            }
            parent = parent.getParent();
        }
    }

    private boolean isStringValueOf(Expression expr) {
        if (expr.getCode() != AstCode.InvokeStatic)
            return false;
        MethodReference mr = (MethodReference) expr.getOperand();
        return mr.getName().equals("valueOf") && mr.getSignature().equals("(Ljava/lang/Object;)Ljava/lang/String;")
            && mr.getDeclaringType().getInternalName().equals("java/lang/String");
    }

    private boolean isStringBuilderToString(Expression expr) {
        if (expr.getCode() != AstCode.InvokeVirtual)
            return false;
        MethodReference mr = (MethodReference) expr.getOperand();
        return mr.getName().equals("toString") && mr.getSignature().equals("()Ljava/lang/String;")
            && mr.getDeclaringType().getInternalName().startsWith("java/lang/StringBu");
    }

    private Expression getAppendSource(Expression expr) {
        Expression arg = expr.getArguments().get(0);
        Expression prev = null;
        while (true) {
            if (arg.getCode() == AstCode.InitObject) {
                if(prev == null)
                    return null;
                if(arg.getArguments().isEmpty()) {
                    // javac scenario
                    return prev.getArguments().size() == 2 ? prev.getArguments().get(1) : null;
                }
                if(arg.getArguments().size() == 1) {
                    // ecj scenario
                    Expression ctorArg = arg.getArguments().get(0);
                    while (isStringValueOf(ctorArg))
                        ctorArg = ctorArg.getArguments().get(0);
                    return ctorArg;
                }
                return null;
            }
            if (arg.getCode() != AstCode.InvokeVirtual)
                return null;
            MethodReference appendMr = (MethodReference) arg.getOperand();
            if (!appendMr.getName().equals("append")
                || !appendMr.getDeclaringType().getInternalName().startsWith("java/lang/StringBu"))
                return null;
            prev = arg;
            arg = arg.getArguments().get(0);
        }
    }
}
