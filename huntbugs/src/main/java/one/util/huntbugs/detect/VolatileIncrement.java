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
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Multithreading", name="VolatileIncrement", baseScore=75)
@WarningDefinition(category="Multithreading", name="VolatileMath", baseScore=75)
public class VolatileIncrement {
    @AstExpressionVisitor
    public void visitNode(Expression node, MethodContext ctx) {
        if(node.getCode() == AstCode.PreIncrement || node.getCode() == AstCode.PostIncrement) {
            Expression arg = node.getArguments().get(0);
            if(arg.getCode() == AstCode.GetField || arg.getCode() == AstCode.GetStatic) {
                FieldDefinition field = ((FieldReference)arg.getOperand()).resolve();
                if (field != null && Flags.testAny(field.getFlags(), Flags.VOLATILE))
                    report(node, ctx, field, true);
            }
        }
        if(node.getCode() == AstCode.PutField || node.getCode() == AstCode.PutStatic) {
            FieldDefinition field = ((FieldReference)node.getOperand()).resolve();
            if(field != null && Flags.testAny(field.getFlags(), Flags.VOLATILE)) {
                Expression self = Nodes.getThis(node);
                Expression op = node.getArguments().get(node.getCode() == AstCode.PutStatic ? 0 : 1);
                if(Nodes.isBinaryMath(op)) {
                    List<Expression> opArgs = op.getArguments();
                    Expression left = opArgs.get(0);
                    Expression right = opArgs.get(1);
                    if(left.getCode() == AstCode.GetField || left.getCode() == AstCode.GetStatic) {
                        if(((FieldReference)left.getOperand()).resolve() == field && Nodes.isEquivalent(self, Nodes.getThis(left))) {
                            report(node, ctx, field, op.getCode() == AstCode.Add);
                        }
                    }
                    if(right.getCode() == AstCode.GetField || right.getCode() == AstCode.GetStatic) {
                        if(((FieldReference)right.getOperand()).resolve() == field && Nodes.isEquivalent(self, Nodes.getThis(right))) {
                            report(node, ctx, field, op.getCode() == AstCode.Add);
                        }
                    }
                }
            }
        }
    }

    private void report(Node node, MethodContext ctx, FieldDefinition field, boolean increment) {
        JvmType type = field.getFieldType().getSimpleType();
        ctx.report(increment ? "VolatileIncrement" : "VolatileMath", type == JvmType.Long || type == JvmType.Double ? 10 : 0, node);
    }

}
