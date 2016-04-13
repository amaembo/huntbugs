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

import com.strobel.decompiler.ast.Expression;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="FloatCompareToNaN", maxScore = 90)
public class FloatingPointNaN {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression node, MethodContext ctx) {
        if(node.getCode().isComparison()) {
            Nodes.ifBinaryWithConst(node, (arg, constant) -> {
                if(constant instanceof Float && Float.isNaN((float) constant)) {
                    ctx.report("FloatCompareToNaN", 0, arg, 
                        WarningAnnotation.forMember("REPLACEMENT", "java/lang/Float", "isNaN", "(F)Z"),
                        new WarningAnnotation<>("USED_TYPE", "float"));
                } else if(constant instanceof Double && Double.isNaN((double) constant)) {
                    ctx.report("FloatCompareToNaN", 0, arg, WarningAnnotation.forMember("REPLACEMENT", "java/lang/Double", "isNaN", "(D)Z"),
                        new WarningAnnotation<>("USED_TYPE", "double"));
                }
            });
        }
    }
}
