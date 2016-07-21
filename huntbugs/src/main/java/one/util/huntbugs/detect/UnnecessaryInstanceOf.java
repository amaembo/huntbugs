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

import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.Inf;
import one.util.huntbugs.flow.etype.EType;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.YesNoMaybe;
import one.util.huntbugs.warning.Role.StringRole;
import one.util.huntbugs.warning.Roles;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "RedundantCode", name = "UnnecessaryInstanceOf", maxScore = 60)
@WarningDefinition(category = "Correctness", name = "ImpossibleInstanceOf", maxScore = 70)
@WarningDefinition(category = "Correctness", name = "ImpossibleCast", maxScore = 70)
public class UnnecessaryInstanceOf {
    private static final StringRole ETYPE = StringRole.forName("ETYPE");

    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression node, MethodContext mc) {
        if (node.getCode() == AstCode.InstanceOf) {
            TypeReference typeRef = (TypeReference) node.getOperand();
            Expression expr = node.getArguments().get(0);
            EType eType = Inf.ETYPE.get(expr);
            YesNoMaybe ynm = eType.isSubtypeOf(typeRef);
            if (ynm == YesNoMaybe.YES) {
                mc.report("UnnecessaryInstanceOf", 0, expr, Roles.TARGET_TYPE.create(typeRef), ETYPE.create(eType
                        .toString()));
            } else if (ynm == YesNoMaybe.NO) {
                mc.report("ImpossibleInstanceOf", 0, expr, Roles.TARGET_TYPE.create(typeRef), ETYPE.create(eType
                        .toString()));
            }
        } else if (node.getCode() == AstCode.CheckCast) {
            TypeReference typeRef = (TypeReference) node.getOperand();
            Expression expr = node.getArguments().get(0);
            EType eType = Inf.ETYPE.get(expr);
            YesNoMaybe ynm = eType.isSubtypeOf(typeRef);
            if (ynm == YesNoMaybe.NO) {
                mc.report("ImpossibleCast", 0, expr, Roles.TARGET_TYPE.create(typeRef), ETYPE.create(eType.toString()));
            }
        }
    }
}
