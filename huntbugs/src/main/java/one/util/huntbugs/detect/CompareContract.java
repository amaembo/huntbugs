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

import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

/**
 * @author shustkost
 *
 */
@WarningDefinition(category="BadPractice", name="CompareReturnsMinValue", maxScore=40)
public class CompareContract {
    private static final Integer MIN_VALUE = Integer.valueOf(Integer.MIN_VALUE);

    @MethodVisitor
    public boolean check(MethodDefinition md, TypeDefinition td) {
        if(md.isSynthetic())
            return false;
        if(md.getName().equals("compare") && md.getParameters().size() == 2 && md.getReturnType().getSimpleType() == JvmType.Integer
                && Types.isInstance(td, "java/util/Comparator"))
            return true;
        if(md.getName().equals("compareTo") && md.getParameters().size() == 1 && md.getReturnType().getSimpleType() == JvmType.Integer
                && Types.isInstance(td, "java/lang/Comparable"))
            return true;
        return false;
    }
    
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if(MIN_VALUE.equals(Nodes.getConstant(expr))) {
            if(ValuesFlow.findTransitiveUsages(expr, true).anyMatch(e -> e.getCode() == AstCode.Return)) {
                mc.report("CompareReturnsMinValue", 0, expr);
            }
        }
    }
}
