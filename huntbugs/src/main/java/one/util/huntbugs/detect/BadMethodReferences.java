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

import com.strobel.assembler.metadata.DynamicCallSite;
import com.strobel.assembler.metadata.IMethodSignature;
import com.strobel.assembler.metadata.MethodHandle;
import com.strobel.assembler.metadata.MethodHandleType;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="MaxMinMethodReferenceForComparator", maxScore=90)
public class BadMethodReferences {
    @AstExpressionVisitor
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.InvokeDynamic) {
            DynamicCallSite dcs = (DynamicCallSite)expr.getOperand();
            MethodHandle mh = dcs.getBootstrapMethodHandle();
            if(mh.getMethod().getDeclaringType().getInternalName().equals("java/lang/invoke/LambdaMetafactory")) {
                List<Object> args = dcs.getBootstrapArguments();
                if(args.size() > 1 && args.get(1) instanceof MethodHandle) {
                    MethodHandle actualHandle = (MethodHandle) args.get(1);
                    IMethodSignature signature = dcs.getMethodType();
                    if(signature != null) {
                        check(actualHandle, signature.getReturnType(), mc, expr);
                    }
                }
            }
        }
    }

    private void check(MethodHandle handle, TypeReference functionalInterface, MethodContext mc, Expression expr) {
        if(functionalInterface.getInternalName().equals("java/util/Comparator") && handle.getHandleType() == MethodHandleType.InvokeStatic) {
            MethodReference mr = handle.getMethod();
            if((mr.getName().equals("min") || mr.getName().equals("max")) && mr.getDeclaringType().getPackageName().equals("java.lang")) {
                mc.report("MaxMinMethodReferenceForComparator", 0, expr, new WarningAnnotation<>("METHOD_REFERENCE", mr));
            }
        }
    }
}
