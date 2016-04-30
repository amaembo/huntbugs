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

import java.util.List;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Loop;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Switch;
import com.strobel.decompiler.ast.TryCatchBlock;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Correctness", name = "InfiniteRecursion", maxScore = 80)
public class InfiniteRecursion {
    boolean stateChange;
    boolean controlTransfer;

    @MethodVisitor
    public void init() {
        stateChange = controlTransfer = false;
    }

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public boolean visit(Expression expr, NodeChain nc, MethodContext mc, MethodDefinition md) {
        if (selfCall(expr, md) && (!stateChange && checkArgs(expr) || !controlTransfer && checkControlFlow(nc))) {
            mc.report("InfiniteRecursion", 0, expr);
        }
        if (expr.getCode() == AstCode.StoreElement || expr.getCode() == AstCode.PutField
            || expr.getCode() == AstCode.PutStatic) {
            stateChange = true;
        }
        if ((Nodes.isInvoke(expr) || expr.getCode() == AstCode.InitObject) && !Nodes.isSideEffectFreeMethod(expr)) {
            stateChange = true;
        }
        if (expr.getCode() == AstCode.Return || expr.getCode() == AstCode.AThrow) {
            controlTransfer = true;
        }
        if (controlTransfer && stateChange)
            return false;
        return true;
    }

    private boolean selfCall(Expression expr, MethodDefinition md) {
        if (!(expr.getOperand() instanceof MethodReference))
            return false;
        MethodReference mr = (MethodReference) expr.getOperand();
        if (!mr.isEquivalentTo(md))
            return false;
        switch (expr.getCode()) {
        case InvokeStatic:
            return md.isStatic();
        case InitObject:
        case InvokeSpecial:
            return md.isConstructor();
        case InvokeVirtual:
        case InvokeInterface:
            return !md.isStatic();
        default:
            return false;
        }
    }

    private boolean checkControlFlow(NodeChain nc) {
        while (nc != null) {
            Node node = nc.getNode();
            if (node instanceof Condition || node instanceof Switch || node instanceof Loop
                || node instanceof TryCatchBlock)
                return false;
            if (node instanceof Expression) {
                Expression expr = (Expression)node;
                if(expr.getCode() == AstCode.LogicalAnd || expr.getCode() == AstCode.LogicalOr || expr.getCode() == AstCode.TernaryOp)
                    return false;
            }
            nc = nc.getParent();
        }
        return true;
    }

    private boolean checkArgs(Expression expr) {
        List<Expression> args = expr.getArguments();
        if ((expr.getCode() == AstCode.InvokeInterface || expr.getCode() == AstCode.InvokeVirtual)
            && !Nodes.isThis(expr.getArguments().get(0)))
            return false;
        int base = (expr.getCode() == AstCode.InvokeStatic || expr.getCode() == AstCode.InitObject) ? 0 : 1;
        for (int i = base; i < args.size(); i++) {
            Expression arg = args.get(i);
            Object operand = ValuesFlow.getSource(arg).getOperand();
            if (!(operand instanceof ParameterDefinition))
                return false;
            ParameterDefinition pd = (ParameterDefinition) operand;
            if (pd.getPosition() != i - base)
                return false;
        }
        return true;
    }
}
