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

import java.math.BigDecimal;
import java.util.Locale;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "BadPractice", name = "SystemExit", maxScore = 60)
@WarningDefinition(category = "BadPractice", name = "SystemGc", maxScore = 50)
@WarningDefinition(category = "BadPractice", name = "SystemRunFinalizersOnExit", maxScore = 60)
@WarningDefinition(category = "BadPractice", name = "ThreadStopThrowable", maxScore = 60)
@WarningDefinition(category = "Performance", name = "URLBlockingMethod", maxScore = 60)
@WarningDefinition(category = "RedundantCode", name = "UselessThread", maxScore = 60)
@WarningDefinition(category = "Correctness", name = "BigDecimalConstructedFromDouble", maxScore = 50)
@WarningDefinition(category = "Correctness", name = "BigDecimalConstructedFromInfiniteOrNaN", maxScore = 70)
@WarningDefinition(category = "Correctness", name = "ArrayToString", maxScore = 60)
public class BadMethodCalls {
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression node, NodeChain nc, MethodContext ctx, MethodDefinition curMethod) {
        if (Nodes.isInvoke(node) && node.getCode() != AstCode.InvokeDynamic) {
            check(node, (MethodReference) node.getOperand(), nc, ctx, curMethod);
        } else if (Nodes.isOp(node, AstCode.InitObject)) {
            checkConstructor(node, (MethodReference) node.getOperand(), ctx);
        }
    }

    private void checkConstructor(Expression node, MethodReference mr, MethodContext ctx) {
        String typeName = mr.getDeclaringType().getInternalName();
        String signature = mr.getSignature();
        if (typeName.equals("java/lang/Thread")
            && !signature.contains("Runnable")) {
            ctx.report("UselessThread", 0, node);
        } else if (typeName.equals("java/math/BigDecimal")
            && signature.equals("(D)V")) {
            Object value = Nodes.getConstant(node.getArguments().get(0));
            if (value instanceof Double) {
                Double val = (Double) value;
                if (val.isInfinite() || val.isNaN()) {
                    ctx.report("BigDecimalConstructedFromInfiniteOrNaN", 0, node, WarningAnnotation.forNumber(val));
                } else {
                    double arg = val.doubleValue();
                    String dblString = value.toString();
                    String bigDecimalString = new BigDecimal(arg).toString();
                    boolean ok = dblString.equals(bigDecimalString) || dblString.equals(bigDecimalString + ".0");

                    if (!ok) {
                        boolean scary = dblString.length() <= 8 && bigDecimalString.length() > 12
                            && dblString.toUpperCase().indexOf('E') == -1;
                        ctx.report("BigDecimalConstructedFromDouble", scary ? 0 : -15, node, new WarningAnnotation<>(
                                "REPLACEMENT", "BigDecimal.valueOf(double)"), new WarningAnnotation<>("DOUBLE_NUMBER",
                                dblString), new WarningAnnotation<>("BIGDECIMAL_NUMBER", bigDecimalString));
                    }
                }
            }
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
            int priority = 0;
            curName = curName.toLowerCase(Locale.ENGLISH);
            if (curName.indexOf("exit") > -1 || curName.indexOf("crash") > -1 || curName.indexOf("die") > -1
                || curName.indexOf("destroy") > -1 || curName.indexOf("close") > -1 || curName.indexOf("main") > -1)
                priority += 20;
            if (curMethod.isStatic())
                priority += 10;
            String curType = curMethod.getDeclaringType().getInternalName();
            if (curType.endsWith("Applet") || curType.endsWith("App") || curType.endsWith("Application"))
                priority += 10;
            if (curMethod.getDeclaringType().getDeclaredMethods().stream().anyMatch(BadMethodCalls::isMain))
                priority += 20;
            ctx.report("SystemExit", priority, node);
        } else if ((typeName.equals("java/lang/System") || typeName.equals("java/lang/Runtime")) && name.equals("gc")
            && signature.equals("()V")) {
            String curName = curMethod.getName();
            if (isMain(curMethod) || curName.startsWith("test"))
                return;
            if (nc.isInCatch("java/lang/OutOfMemoryError"))
                return;
            if (Nodes.find(nc.getRoot(), BadMethodCalls::isTimeMeasure) != null)
                return;
            int priority = 0;
            if (curName.toLowerCase(Locale.ENGLISH).contains("garbage")
                || curName.toLowerCase(Locale.ENGLISH).contains("memory") || curName.startsWith("gc")
                || curName.endsWith("gc"))
                priority += 10;
            ctx.report("SystemGc", priority, node);
        } else if ((typeName.equals("java/lang/System") || typeName.equals("java/lang/Runtime"))
            && name.equals("runFinalizersOnExit")) {
            ctx.report("SystemRunFinalizersOnExit", 0, node);
        } else if (typeName.equals("java/lang/Thread") && name.equals("stop")
            && signature.equals("(Ljava/lang/Throwable;)V")) {
            ctx.report("ThreadStopThrowable", 0, node);
        } else if (typeName.equals("java/net/URL") && (name.equals("equals") || name.equals("hashCode"))) {
            ctx.report("URLBlockingMethod", 0, node);
        } else if (isToStringCall(typeName, name, signature)) {
            Expression lastArg = Nodes.getChild(node, node.getArguments().size() - 1);
            TypeReference type = lastArg.getInferredType();
            if (type != null && type.isArray()) {
                ctx.report("ArrayToString", 0, lastArg);
            }
        }
    }

    private boolean isToStringCall(String typeName, String name, String signature) {
        if (name.equals("toString") && signature.equals("()Ljava/lang/String;"))
            return true;
        if (name.equals("append") && typeName.startsWith("java/lang/StringBu")
            && signature.startsWith("(Ljava/lang/Object;)Ljava/lang/StringBu"))
            return true;
        if ((name.equals("print") || name.equals("println")) && signature.equals("(Ljava/lang/Object;)V"))
            return true;
        return false;
    }

    private static boolean isMain(MethodDefinition curMethod) {
        return curMethod.getName().equals("main") && curMethod.isStatic()
            && curMethod.getErasedSignature().startsWith("([Ljava/lang/String;)");
    }

    private static boolean isTimeMeasure(Node node) {
        if (!Nodes.isOp(node, AstCode.InvokeStatic))
            return false;
        MethodReference mr = (MethodReference) ((Expression) node).getOperand();
        return mr.getName().equals("currentTimeMillis") || mr.getName().equals("nanoTime");
    }
}
