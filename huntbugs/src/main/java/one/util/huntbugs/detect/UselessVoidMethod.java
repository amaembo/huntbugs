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

import java.util.List;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.db.MethodStats;
import one.util.huntbugs.db.MethodStats.MethodData;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category="RedundantCode", name="UselessVoidMethod", maxScore=50)
public class UselessVoidMethod {
    @AstVisitor(nodes=AstNodes.ROOT)
    public void checkMethod(Block root, MethodDefinition md, MethodStats ms, MethodContext mc) {
        if(md.getReturnType().isVoid()) {
            MethodData stats = ms.getStats(md);
            if (stats != null && !stats.mayHaveSideEffect(true) && !stats.testAny(MethodStats.METHOD_MAY_THROW, true)) {
                List<Node> body = root.getBody();
                if(body.isEmpty())
                    return;
                if(md.isConstructor() && body.size() == 1 && Nodes.isOp(body.get(0), AstCode.InvokeSpecial))
                    return;
                mc.report("UselessVoidMethod", 0);
            }
        }
    }
}
