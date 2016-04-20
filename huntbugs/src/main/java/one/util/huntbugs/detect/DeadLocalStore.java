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

import java.util.Collections;

import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;

/**
 * @author lan
 *
 */
@WarningDefinition(category="RedundantCode", name="DeadStoreOfLocalVariable", maxScore=60)
public class DeadLocalStore {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public boolean visit(Expression expr, MethodContext mc) {
        if(!mc.isAnnotated())
            return false;
        if (expr.getCode() == AstCode.Store
            && ValuesFlow.getSource(expr) == expr
            && ValuesFlow.findUsages(expr.getArguments().get(0)).equals(Collections.singleton(expr))) {
            mc.report("DeadStoreOfLocalVariable", 0, expr);
        }
        return true;
    }
}
