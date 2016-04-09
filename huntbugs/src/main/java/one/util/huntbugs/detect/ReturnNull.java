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

import java.util.HashMap;
import java.util.Map;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "BadPractice", name = "OptionalReturnNull", maxScore = 50)
@WarningDefinition(category = "BadPractice", name = "BooleanReturnNull", maxScore = 50)
@WarningDefinition(category = "BadPractice", name = "ArrayReturnNull", maxScore = 40)
public class ReturnNull {
    private static final Map<String, String> TYPE_TO_WARNING = new HashMap<>();

    static {
        TYPE_TO_WARNING.put("java/util/Optional", "OptionalReturnNull");
        TYPE_TO_WARNING.put("com/google/common/base/Optional", "OptionalReturnNull");
        TYPE_TO_WARNING.put("java/lang/Boolean", "BooleanReturnNull");
    }

    @MethodVisitor
    public boolean checkMethod(MethodDefinition md) {
        return md.getReturnType().isArray() || TYPE_TO_WARNING.containsKey(md.getReturnType().getInternalName());
    }

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public boolean visit(Expression expr, NodeChain nc, MethodContext mc, MethodDefinition md) {
        // TODO: support lambdas properly
        if (nc.getLambdaMethod() != null)
            return true;
        if (expr.getCode() == AstCode.Return) {
            Expression child = ValuesFlow.getSource(expr.getArguments().get(0));
            if (child.getCode() == AstCode.AConstNull) {
                String warningType = md.getReturnType().isArray() ? "ArrayReturnNull" : TYPE_TO_WARNING.get(md
                        .getReturnType().getInternalName());
                mc.report(warningType, 0, expr.getArguments().get(0));
            }
        }
        return true;
    }
}
