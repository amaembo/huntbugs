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
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="DroppedException", maxScore=60)
public class DroppedExceptionObject {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc) {
        if (expr.getCode() == AstCode.InitObject || expr.getCode() == AstCode.InvokeSpecial
            && expr.getArguments().get(0).getCode() == AstCode.__New) { // Probably procyon bug: invokespecial(__new) is not collapsed to InitObject 
            MethodReference mr = (MethodReference) expr.getOperand();
            if(Types.isInstance(mr.getDeclaringType(), "java/lang/Throwable") ||
                    Types.isInstance(mr.getDeclaringType(), "java/lang/Exception")) {
                if(nc.getNode() instanceof Block) {
                    mc.report("DroppedException", 0, expr, WarningAnnotation.forType("EXCEPTION", mr.getDeclaringType()));
                }
            }
        }
    }
}
