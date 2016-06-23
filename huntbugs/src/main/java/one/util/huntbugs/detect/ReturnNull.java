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

import java.util.HashMap;
import java.util.Map;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Role.TypeRole;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "BadPractice", name = "OptionalReturnNull", maxScore = 50)
@WarningDefinition(category = "BadPractice", name = "BooleanReturnNull", maxScore = 50)
@WarningDefinition(category = "BadPractice", name = "ArrayReturnNull", maxScore = 38)
public class ReturnNull {
    private static final TypeRole RETURN_TYPE = TypeRole.forName("RETURN_TYPE");

    private static final Map<String, String> TYPE_TO_WARNING = new HashMap<>();

    static {
        TYPE_TO_WARNING.put("java/util/Optional", "OptionalReturnNull");
        TYPE_TO_WARNING.put("com/google/common/base/Optional", "OptionalReturnNull");
        TYPE_TO_WARNING.put("java/lang/Boolean", "BooleanReturnNull");
    }

    public boolean checkMethod(MethodDefinition md) {
        if(!md.getReturnType().isArray() && !TYPE_TO_WARNING.containsKey(md.getReturnType().getInternalName()))
            return false;
        if(md.getName().equals("getName") && md.getReturnType().getSignature().equals("[B") && md.getParameters().size() == 1
                && Types.isInstance(md.getDeclaringType(), "java/sql/ResultSet"))
            return false;
        return true;
    }

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc, MethodDefinition md, TypeDefinition td) {
        if (expr.getCode() == AstCode.Return && !expr.getArguments().isEmpty()) {
            Expression child = Exprs.getChild(expr, 0);
            if (child.getCode() == AstCode.AConstNull) {
                MethodDefinition curMethod = nc.getLambdaMethod();
                if (curMethod == null)
                    curMethod = md;
                String warningType = curMethod.getReturnType().isArray() ? "ArrayReturnNull" : TYPE_TO_WARNING
                        .get(curMethod.getReturnType().getInternalName());
                if (warningType != null) {
                    int priority = 0;
                    if (!td.isPublic() || md.isPrivate() || md.isPackagePrivate())
                        priority = 20;
                    else if (md.isProtected() || md != curMethod)
                        priority = 10;
                    // Method which simply contains "return null": probably stub or something
                    if(nc.getParent() == null && nc.isOnlyChild(expr))
                        priority += 10;
                    mc.report(warningType, priority, expr.getArguments().get(0), RETURN_TYPE.create(md
                            .getReturnType()));
                }
            }
        }
    }
}
