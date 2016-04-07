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

import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="RemOne", baseScore=70)
@WarningDefinition(category="RedundantCode", name="UselessOrWithZero", baseScore=60)
@WarningDefinition(category="RedundantCode", name="UselessAndWithMinusOne", baseScore=60)
public class BadMath {
    private static boolean isConst(Expression expr, long wantedValue) {
        Object constant = Nodes.getConstant(expr);
        return (constant instanceof Integer || constant instanceof Long) && ((Number)constant).longValue() == wantedValue;
    }
    
    @AstExpressionVisitor
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.Rem) {
            if(isConst(expr.getArguments().get(1), 1)) {
                mc.report("RemOne", 0, expr.getArguments().get(0));
            }
        }
        if(expr.getCode() == AstCode.Or || expr.getCode() == AstCode.Xor) {
            if(isConst(expr.getArguments().get(1), 0)) {
                mc.report("UselessOrWithZero", 0, expr.getArguments().get(0));
            } else if(isConst(expr.getArguments().get(0), 0)) {
                mc.report("UselessOrWithZero", 0, expr.getArguments().get(1));
            }
        }
        if(expr.getCode() == AstCode.And) {
            if(isConst(expr.getArguments().get(1), -1)) {
                mc.report("UselessAndWithMinusOne", 0, expr.getArguments().get(0));
            } else if(isConst(expr.getArguments().get(0), -1)) {
                mc.report("UselessAndWithMinusOne", 0, expr.getArguments().get(1));
            }
        }
    }
}
