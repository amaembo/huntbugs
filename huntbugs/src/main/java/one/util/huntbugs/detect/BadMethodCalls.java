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

import java.util.Locale;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "BadPractice", name = "SystemExit", maxScore = 60)
@WarningDefinition(category = "BadPractice", name = "SystemGc", maxScore = 50)
@WarningDefinition(category = "BadPractice", name = "SystemRunFinalizersOnExit", maxScore = 60)
@WarningDefinition(category = "BadPractice", name = "ThreadStopThrowable", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "UselessThread", maxScore = 60)
public class BadMethodCalls {
    @AstExpressionVisitor
    public void visit(Expression node, NodeChain nc, MethodContext ctx, MethodDefinition curMethod) {
        if (Nodes.isInvoke(node) && node.getCode() != AstCode.InvokeDynamic || Nodes.isOp(node, AstCode.InitObject)) {
            check(node, (MethodReference) node.getOperand(), nc, ctx, curMethod);
        }
    }

    private void check(Expression node, MethodReference mr, NodeChain nc, MethodContext ctx, MethodDefinition curMethod) {
        String typeName = mr.getDeclaringType().getInternalName();
        String name = mr.getName();
        String signature = mr.getSignature();
        if (typeName.equals("java/lang/System") && name.equals("exit")) {
            String curName = curMethod.getName();
            if (isMain(curMethod) || curName.equals("processWindowEvent") || curName.startsWith("windowClos"))
                return;
            int score = 0;
            curName = curName.toLowerCase(Locale.ENGLISH);
            if (curName.indexOf("exit") > -1 || curName.indexOf("crash") > -1 || curName.indexOf("die") > -1
                || curName.indexOf("destroy") > -1 || curName.indexOf("close") > -1 || curName.indexOf("main") > -1)
                score -= 20;
            if (curMethod.isStatic())
                score -= 10;
            String curType = curMethod.getDeclaringType().getInternalName();
            if (curType.endsWith("Applet") || curType.endsWith("App") || curType.endsWith("Application"))
                score -= 10;
            if (curMethod.getDeclaringType().getDeclaredMethods().stream().anyMatch(BadMethodCalls::isMain))
                score -= 20;
            ctx.report("SystemExit", score, node);
        } else if ((typeName.equals("java/lang/System") || typeName.equals("java/lang/Runtime")) && name.equals("gc")
            && signature.equals("()V")) {
            String curName = curMethod.getName();
            if (isMain(curMethod) || curName.startsWith("test"))
                return;
            if (nc.isInCatch("java/lang/OutOfMemoryError"))
                return;
            if (Nodes.find(nc.getRoot(), BadMethodCalls::isTimeMeasure) != null)
                return;
            int score = 0;
            if (curName.toLowerCase(Locale.ENGLISH).contains("garbage")
                || curName.toLowerCase(Locale.ENGLISH).contains("memory") || curName.startsWith("gc")
                || curName.endsWith("gc"))
                score -= 10;
            ctx.report("SystemGc", score, node);
        } else if ((typeName.equals("java/lang/System") || typeName.equals("java/lang/Runtime")) && name.equals("runFinalizersOnExit")) {
            ctx.report("SystemRunFinalizersOnExit", 0, node);
        } else if (typeName.equals("java/lang/Thread") && name.equals("stop")
            && signature.equals("(Ljava/lang/Throwable;)V")) {
            ctx.report("ThreadStopThrowable", 0, node);
        } else if (node.getCode() == AstCode.InitObject && typeName.equals("java/lang/Thread") && name.equals("<init>")
            && !signature.contains("Runnable")) {
            ctx.report("UselessThread", 0, node);
        }
    }

    private static boolean isMain(MethodDefinition curMethod) {
        return curMethod.getName().equals("main") && curMethod.isStatic()
            && curMethod.getErasedSignature().startsWith("([Ljava/lang/String;)");
    }
    
    private static boolean isTimeMeasure(Node node) {
        if (!Nodes.isOp(node, AstCode.InvokeStatic))
            return false;
        MethodReference mr = (MethodReference) ((Expression)node).getOperand();
        return mr.getName().equals("currentTimeMillis") || mr.getName().equals("nanoTime");
    }
}
