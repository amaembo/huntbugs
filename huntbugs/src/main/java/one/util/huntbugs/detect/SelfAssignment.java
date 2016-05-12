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

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category="Correctness", name="SelfAssignmentField", maxScore=80)
@WarningDefinition(category="Correctness", name="SelfAssignmentLocal", maxScore=80)
@WarningDefinition(category="Correctness", name="SelfAssignmentLocalInsteadOfField", maxScore=90)
@WarningDefinition(category="Correctness", name="SelfAssignmentArrayElement", maxScore=80)
public class SelfAssignment {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc, MethodDefinition md, TypeDefinition td) {
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
        } else if(expr.getCode() == AstCode.StoreElement) {
            Expression sourceRef = Nodes.getChild(expr, 2);
            if(sourceRef.getCode() == AstCode.LoadElement) {
                Expression storeArrayRef = Nodes.getChild(expr, 0);
                Expression storeIndexRef = Nodes.getChild(expr, 1);
                Expression loadArrayRef = Nodes.getChild(sourceRef, 0);
                Expression loadIndexRef = Nodes.getChild(sourceRef, 1);
                if(Nodes.isEquivalent(storeArrayRef, loadArrayRef) && Nodes.isEquivalent(storeIndexRef, loadIndexRef)) {
                    mc.report("SelfAssignmentArrayElement", 0, expr);
                }
            }
        } else if(expr.getCode() == AstCode.Store) {
            Expression ref = expr.getArguments().get(0);
            if(ref.getCode() == AstCode.Load && ref.getOperand() == expr.getOperand()) {
                if(!md.isStatic()) {
                    Variable v = (Variable)ref.getOperand();
                    for(FieldDefinition fd : td.getDeclaredFields()) {
                        if(fd.getName().equals(v.getName())) {
                            mc.report("SelfAssignmentLocalInsteadOfField", 0, expr, WarningAnnotation.forField(fd));
                            return;
                        }
                    }
                }
                mc.report("SelfAssignmentLocal", 0, expr);
            }
        }
    }
}
