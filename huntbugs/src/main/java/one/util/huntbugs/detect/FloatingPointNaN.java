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

import com.strobel.decompiler.ast.Expression;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Role.StringRole;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category="Correctness", name="FloatCompareToNaN", maxScore = 90)
public class FloatingPointNaN {
    private static final StringRole USED_TYPE = StringRole.forName("USED_TYPE");
    
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression node, MethodContext ctx) {
        if(node.getCode().isComparison()) {
            Nodes.ifBinaryWithConst(node, (arg, constant) -> {
                if(constant instanceof Float && Float.isNaN((float) constant)) {
                    ctx.report("FloatCompareToNaN", 0, arg, 
                        Roles.REPLACEMENT_METHOD.create("java/lang/Float", "isNaN", "(F)Z"),
                        USED_TYPE.create("float"));
                } else if(constant instanceof Double && Double.isNaN((double) constant)) {
                    ctx.report("FloatCompareToNaN", 0, arg, Roles.REPLACEMENT_METHOD.create("java/lang/Double", "isNaN", "(D)Z"),
                        USED_TYPE.create("double"));
                }
            });
        }
    }
}
