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

import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Multithreading", name="InitializerRefersSubclass", maxScore=40)
public class InitializerRefersSubclass {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS, methodName="<clinit>")
    public void visit(Expression expr, NodeChain nc, MethodContext mc, TypeDefinition td) {
        if(expr.getOperand() instanceof MemberReference) {
            MemberReference mr = (MemberReference) expr.getOperand();
            TypeReference tr = mr.getDeclaringType();
            TypeDefinition subType = tr == null ? null : tr.resolve();
            if (subType != null && (subType.isAnonymous() || subType.isLocalClass())) {
                subType = subType.getBaseType().resolve();
            }
            if (subType != null && !td.isEquivalentTo(subType) && Types.isInstance(subType, td) && nc
                    .getLambdaMethod() == null) {
                mc.report("InitializerRefersSubclass", td.isNonPublic() || subType.isNonPublic() ? 5 : 0, expr,
                    Roles.SUBCLASS.create(subType));
            }
        }
    }
}
