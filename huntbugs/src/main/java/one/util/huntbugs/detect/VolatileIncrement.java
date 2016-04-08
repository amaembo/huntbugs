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

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Multithreading", name = "VolatileIncrement", maxScore = 85)
@WarningDefinition(category = "Multithreading", name = "VolatileMath", maxScore = 85)
public class VolatileIncrement {
    @AstExpressionVisitor
    public void visitNode(Expression node, MethodContext ctx, NodeChain parents, MethodDefinition md) {
        if (node.getCode() == AstCode.PreIncrement || node.getCode() == AstCode.PostIncrement) {
            Expression arg = node.getArguments().get(0);
            if (arg.getCode() == AstCode.GetField || arg.getCode() == AstCode.GetStatic) {
                FieldDefinition field = ((FieldReference) arg.getOperand()).resolve();
                if (field != null && Flags.testAny(field.getFlags(), Flags.VOLATILE))
                    ctx.report("VolatileIncrement", computeScore(field, md, parents), node);
            }
        }
        if (node.getCode() == AstCode.PutField || node.getCode() == AstCode.PutStatic) {
            FieldDefinition field = ((FieldReference) node.getOperand()).resolve();
            if (field != null && Flags.testAny(field.getFlags(), Flags.VOLATILE)) {
                Expression self = Nodes.getThis(node);
                Expression op = node.getArguments().get(node.getCode() == AstCode.PutStatic ? 0 : 1);
                if (Nodes.isBinaryMath(op)) {
                    List<Expression> opArgs = op.getArguments();
                    Expression left = opArgs.get(0);
                    Expression right = opArgs.get(1);
                    if (left.getCode() == AstCode.GetField || left.getCode() == AstCode.GetStatic) {
                        if (((FieldReference) left.getOperand()).resolve() == field
                            && Nodes.isEquivalent(self, Nodes.getThis(left))) {
                            ctx.report(op.getCode() == AstCode.Add ? "VolatileIncrement" : "VolatileMath",
                                computeScore(field, md, parents), node);
                        }
                    }
                    if (right.getCode() == AstCode.GetField || right.getCode() == AstCode.GetStatic) {
                        if (((FieldReference) right.getOperand()).resolve() == field
                            && Nodes.isEquivalent(self, Nodes.getThis(right))) {
                            ctx.report(op.getCode() == AstCode.Add ? "VolatileIncrement" : "VolatileMath",
                                computeScore(field, md, parents), node);
                        }
                    }
                }
            }
        }
    }

    private int computeScore(FieldDefinition field, MethodDefinition md, NodeChain parents) {
        int score = 0;
        JvmType type = field.getFieldType().getSimpleType();
        if (type != JvmType.Long && type != JvmType.Double)
            score -= 10;

        if (Flags.testAny(md.getFlags(), Flags.SYNCHRONIZED) || parents.isSynchronized())
            score -= 30;
        return score;
    }

}
