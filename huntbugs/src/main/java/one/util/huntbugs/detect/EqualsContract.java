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

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstBodyVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category="BadPractice", name="EqualsReturnsTrue", baseScore=50)
@WarningDefinition(category="BadPractice", name="EqualsReturnsFalse", baseScore=50)
public class EqualsContract {
    @AstBodyVisitor
    public void visitMethod(Block body, MethodContext mc, MethodDefinition md, TypeDefinition td) {
        if(!md.getName().equals("equals") || !md.getErasedSignature().equals("(Ljava/lang/Object;)Z"))
            return;
        List<Node> list = body.getBody();
        if(list.size() == 1) {
            Node node = list.get(0);
            if(Nodes.isOp(node, AstCode.Return)) {
                Object constant = Nodes.getConstant(Nodes.getOperand(node, 0));
                int score = 0;
                if(td.isNonPublic())
                    score -= 30;
                if(td.isFinal())
                    score -= 5;
                if(((Integer)1).equals(constant))
                    mc.report("EqualsReturnsTrue", score, node);
                else if(((Integer)0).equals(constant))
                    mc.report("EqualsReturnsFalse", score, node);
            }
        }
    }
}
