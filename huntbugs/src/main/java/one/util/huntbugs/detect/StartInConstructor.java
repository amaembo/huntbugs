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
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.db.Hierarchy.TypeHierarchy;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Types;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Multithreading", name="StartInConstructor", maxScore=50)
public class StartInConstructor {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public boolean visit(Expression expr, NodeChain nc, MethodContext mc, MethodDefinition md, TypeDefinition td, TypeHierarchy th) {
        if (!md.isConstructor() || !td.isPublic() || td.isFinal() || md.isPrivate() || md.isPackagePrivate())
            return false;
        if(expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if(mr.getName().equals("start") && mr.getSignature().equals("()V")) {
                if(Types.isInstance(mr.getDeclaringType(), "java/lang/Thread")) {
                    int priority = 0;
                    if(!th.hasSubClasses())
                        priority += 10;
                    else if(!th.hasSubClassesOutOfPackage())
                        priority += 5;
                    List<Node> body = nc.getRoot().getBody();
                    if(body.get(body.size()-1) == expr)
                        priority += 10;
                    mc.report("StartInConstructor", priority, expr);
                }
            }
        }
        return true;
    }
}
