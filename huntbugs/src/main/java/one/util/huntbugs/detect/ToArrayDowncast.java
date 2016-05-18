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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Role.TypeRole;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "Correctness", name = "ImpossibleToArrayDowncast", maxScore = 65)
public class ToArrayDowncast {
    private static final TypeRole TARGET_ELEMENT_TYPE = TypeRole.forName("TARGET_ELEMENT_TYPE");

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if (expr.getCode() != AstCode.CheckCast)
            return;
        TypeReference targetType = (TypeReference) expr.getOperand();
        if (!targetType.isArray() || Types.isObject(targetType.getElementType()))
            return;
        Expression arg = Nodes.getChild(expr, 0);
        if (arg.getCode() != AstCode.InvokeVirtual && arg.getCode() != AstCode.InvokeInterface)
            return;
        MethodReference mr = (MethodReference) arg.getOperand();
        if (!mr.getName().equals("toArray") || !mr.getSignature().equals("()[Ljava/lang/Object;"))
            return;
        Expression target = Nodes.getChild(arg, 0);
        if (!Types.isInstance(target.getInferredType(), "java/util/Collection"))
            return;
        mc.report("ImpossibleToArrayDowncast", 0, target, Roles.TARGET_TYPE.create(targetType),
            TARGET_ELEMENT_TYPE.create(targetType.getElementType()));
    }
}
