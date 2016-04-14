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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Performance", name="NumberConstructor", maxScore = 45)
@WarningDefinition(category="Performance", name="BooleanConstructor", maxScore = 55)
public class NumberConstructor {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext ctx) {
        if(expr.getCode() == AstCode.InitObject && expr.getArguments().size() == 1) {
            MethodReference ctor = (MethodReference) expr.getOperand();
            if(ctor.getDeclaringType().getPackageName().equals("java.lang")) {
                String simpleName = ctor.getDeclaringType().getSimpleName();
                WarningAnnotation<MemberInfo> replacement = WarningAnnotation.forMember("REPLACEMENT", ctor
                    .getDeclaringType().getInternalName(), "valueOf", ctor.getSignature().replaceFirst("V$",
                "L" + ctor.getDeclaringType().getInternalName() + ";"));
                if(simpleName.equals("Boolean")) {
                    ctx.report("BooleanConstructor", 0, expr, replacement);
                }
                else if(simpleName.equals("Integer") || simpleName.equals("Long") || simpleName.equals("Short") || simpleName.equals("Byte")
                        || simpleName.equals("Character")) {
                    Object val = Nodes.getConstant(expr.getArguments().get(0));
                    if(val instanceof Number) {
                        long value = ((Number)val).longValue();
                        int priority;
                        if(value >= -128 && value < 127)
                            priority = 0;
                        else
                            priority = simpleName.equals("Integer") ? 15 : 35;
                        ctx.report("NumberConstructor", priority, expr, WarningAnnotation.forNumber((Number) val), replacement, new WarningAnnotation<>("SIMPLE_TYPE", simpleName));
                    } else {
                        ctx.report("NumberConstructor", 5, expr, replacement, new WarningAnnotation<>("SIMPLE_TYPE", simpleName));
                    }
                }
            }
        }
    }

}
