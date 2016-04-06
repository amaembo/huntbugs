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

import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodeVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="FloatCompareToNaN", baseRank = 90)
public class FloatingPointNaN {
    @AstNodeVisitor
    public void visit(Expression node, MethodContext ctx) {
        if(Nodes.isComparison(node)) {
            List<Expression> args = node.getArguments();
            Node leftNode = args.get(0);
            Node rightNode = args.get(1);
			Object left = Nodes.getConstant(leftNode);
			Object right = Nodes.getConstant(rightNode);
            if(left instanceof Float && Float.isNaN((float) left)) {
                ctx.report("FloatCompareToNaN", 0, rightNode, new WarningAnnotation<>("REPLACEMENT", "Float.isNaN()"));
            } else if(right instanceof Float && Float.isNaN((float) right)) {
                ctx.report("FloatCompareToNaN", 0, leftNode, new WarningAnnotation<>("REPLACEMENT", "Float.isNaN()"));
            } else if(left instanceof Double && Double.isNaN((double) left)) {
                ctx.report("FloatCompareToNaN", 0, rightNode, new WarningAnnotation<>("REPLACEMENT", "Double.isNaN()"));
            } else if(right instanceof Double && Double.isNaN((double) right)) {
                ctx.report("FloatCompareToNaN", 0, leftNode, new WarningAnnotation<>("REPLACEMENT", "Double.isNaN()"));
            }
        }
    }
}
