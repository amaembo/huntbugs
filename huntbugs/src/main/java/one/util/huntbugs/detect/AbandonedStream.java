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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.Inf;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Types;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="AbandonedStream", maxScore=80)
public class AbandonedStream {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS, minVersion=8)
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.InvokeInterface) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if(mr.getReturnType().getPackageName().equals("java.util.stream") && 
                    Types.isBaseStream(mr.getReturnType())) {
                // intermediate stream operation
                if(mc.isAnnotated() && !Inf.BACKLINK.findTransitiveUsages(expr, true).findAny().isPresent()) {
                    mc.report("AbandonedStream", 0, expr);
                }
            }
        }
    }
}
