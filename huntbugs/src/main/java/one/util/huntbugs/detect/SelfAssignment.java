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

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
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
@WarningDefinition(category="Correctness", name="SelfAssignmentField", baseScore=80)
public class SelfAssignment {
    @AstExpressionVisitor
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.PutField) {
            FieldDefinition frPut = ((FieldReference) expr.getOperand()).resolve();
            if(frPut != null) {
                Expression getField = Nodes.getChild(expr, 1);
                if(getField.getCode() == AstCode.GetField) {
                    FieldReference frGet = (FieldReference) getField.getOperand();
                    if(frPut.equals(frGet.resolve())) {
                        Node selfPut = Nodes.getChild(expr, 0);
                        Node selfGet = Nodes.getChild(getField, 0);
                        if(Nodes.isEquivalent(selfGet, selfPut)) {
                            mc.report("SelfAssignmentField", 0, expr);
                        }
                    }
                }
            }
        } else if(expr.getCode() == AstCode.PutStatic) {
            FieldDefinition frPut = ((FieldReference) expr.getOperand()).resolve();
            if(frPut != null) {
                Expression getStatic = Nodes.getChild(expr, 0);
                if(getStatic.getCode() == AstCode.GetStatic) {
                    FieldReference frGet = (FieldReference) getStatic.getOperand();
                    if(frPut.equals(frGet.resolve())) {
                        mc.report("SelfAssignmentField", 0, expr);
                    }
                }
            }
        }
    }
}
