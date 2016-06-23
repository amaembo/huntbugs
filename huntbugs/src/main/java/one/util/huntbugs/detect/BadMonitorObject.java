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

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Role.TypeRole;
import one.util.huntbugs.warning.Roles;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category="Multithreading", name="SynchronizationOnBoolean", maxScore=70)
@WarningDefinition(category="Multithreading", name="SynchronizationOnBoxedNumber", maxScore=65)
@WarningDefinition(category="Multithreading", name="SynchronizationOnUnsharedBoxed", maxScore=40)
public class BadMonitorObject {
    private static final TypeRole MONITOR_TYPE = TypeRole.forName("MONITOR_TYPE");
    
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.MonitorEnter) {
            Expression arg = Exprs.getChild(expr, 0);
            TypeReference type = arg.getInferredType();
            if(type != null && Types.isBoxed(type)) {
                String warningType;
                if(arg.getCode() == AstCode.InitObject) {
                    warningType = "SynchronizationOnUnsharedBoxed";
                } else if(type.getInternalName().equals("java/lang/Boolean")) {
                    warningType = "SynchronizationOnBoolean";
                } else {
                    warningType = "SynchronizationOnBoxedNumber";
                }
                mc.report(warningType, 0, arg, MONITOR_TYPE.create(type), Roles.EXPRESSION.create(arg));
            }
        }
    }
}
