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

import java.util.Set;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "Performance", name = "NumberConstructor", maxScore = 45)
@WarningDefinition(category = "Performance", name = "BooleanConstructor", maxScore = 55)
public class NumberConstructor {
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext ctx, MethodDefinition md) {
        if (expr.getCode() == AstCode.InitObject && expr.getArguments().size() == 1) {
            MethodReference ctor = (MethodReference) expr.getOperand();
            if (ctor.getDeclaringType().getPackageName().equals("java.lang")) {
                String simpleName = ctor.getDeclaringType().getSimpleName();
                WarningAnnotation<MemberInfo> replacement = Roles.REPLACEMENT_METHOD.create(ctor.getDeclaringType()
                        .getInternalName(), "valueOf", ctor.getSignature().replaceFirst("V$", "L" + ctor
                                .getDeclaringType().getInternalName() + ";"));
                int priority = 0;
                if(md.isTypeInitializer()) {
                    // Static field initializer: only one object is created
                    // not a big performance problem and probably intended
                    Set<Expression> usages = ValuesFlow.findUsages(expr);
                    if(usages.size() == 1 && usages.iterator().next().getCode() == AstCode.PutStatic) {
                        priority = 15;
                    }
                }
                if (simpleName.equals("Boolean")) {
                    ctx.report("BooleanConstructor", priority, expr, replacement);
                } else if (simpleName.equals("Integer") || simpleName.equals("Long") || simpleName.equals("Short")
                    || simpleName.equals("Byte") || simpleName.equals("Character")) {
                    Object val = Nodes.getConstant(expr.getArguments().get(0));
                    if (val instanceof Number) {
                        long value = ((Number) val).longValue();
                        if (value < -128 || value > 127)
                            priority += simpleName.equals("Integer") ? 15 : 30;
                        ctx.report("NumberConstructor", priority, expr, Roles.NUMBER.create((Number) val), replacement,
                            Roles.TARGET_TYPE.create(ctor.getDeclaringType()));
                    } else {
                        ctx.report("NumberConstructor", 5, expr, replacement, Roles.TARGET_TYPE.create(ctor
                                .getDeclaringType()));
                    }
                }
            }
        }
    }

}
