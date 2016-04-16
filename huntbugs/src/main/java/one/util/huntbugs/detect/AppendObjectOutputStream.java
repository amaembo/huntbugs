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

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Correctness", name = "AppendObjectOutputStream", maxScore = 65)
public class AppendObjectOutputStream {
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if (expr.getCode() != AstCode.InitObject)
            return;
        MethodReference ctor = (MethodReference) expr.getOperand();
        if (!ctor.getDeclaringType().getInternalName().equals("java/io/ObjectOutputStream")
            || !ctor.getSignature().equals("(Ljava/io/OutputStream;)V"))
            return;
        Expression outStream = Nodes.getChild(expr, 0);
        while (isBufferedStream(outStream))
            outStream = Nodes.getChild(outStream, 0);
        Expression target = ValuesFlow.findFirst(outStream, AppendObjectOutputStream::isAppendOutput);
        if (target != null) {
            mc.report("AppendObjectOutputStream", 0, expr, WarningAnnotation.forLocation("STREAM_CREATED_AT", mc
                    .getLocation(target)), WarningAnnotation.forType("OOS_TYPE", ctor.getDeclaringType()));
        }
    }

    private static boolean isBufferedStream(Expression expr) {
        if (expr.getCode() == AstCode.InitObject) {
            MethodReference ctor = (MethodReference) expr.getOperand();
            if (ctor.getDeclaringType().getInternalName().equals("java/io/BufferedOutputStream")
                && ctor.getSignature().equals("(Ljava/io/OutputStream;)V")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAppendOutput(Expression expr) {
        if (expr.getCode() == AstCode.InitObject) {
            MethodReference ctor = (MethodReference) expr.getOperand();
            if (ctor.getDeclaringType().getInternalName().equals("java/io/FileOutputStream")
                && (ctor.getSignature().equals("(Ljava/io/File;Z)V") || ctor.getSignature().equals(
                    "(Ljava/lang/String;Z)V"))) {
                Object constant = Nodes.getConstant(expr.getArguments().get(1));
                if (constant instanceof Integer && ((Integer) constant).intValue() == 1) {
                    return true;
                }
            }
        }
        return false;
    }
}
