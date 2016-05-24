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

import java.math.BigDecimal;
import java.util.Locale;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Role.StringRole;
import one.util.huntbugs.warning.Role.TypeRole;

/**
 * @author Tagir Valeev
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
@WarningDefinition(category = "Correctness", name = "ArrayHashCode", maxScore = 60)
@WarningDefinition(category = "Correctness", name = "DoubleLongBitsToDoubleOnInt", maxScore = 70)
@WarningDefinition(category = "Correctness", name = "ScheduledThreadPoolExecutorChangePoolSize", maxScore = 70)
@WarningDefinition(category = "Correctness", name = "DateBadMonth", maxScore = 70)
@WarningDefinition(category = "Correctness", name = "CollectionAddedToItself", maxScore = 65)
@WarningDefinition(category = "RedundantCode", name = "NullCheckMethodForConstant", maxScore = 65)
@WarningDefinition(category = "Correctness", name = "WrongArgumentOrder", maxScore = 65)
public class BadMethodCalls {
    private static final StringRole DOUBLE_NUMBER = StringRole.forName("DOUBLE_NUMBER");
    private static final StringRole BIGDECIMAL_NUMBER = StringRole.forName("BIGDECIMAL_NUMBER");
    private static final TypeRole ARG_TYPE = TypeRole.forName("ARG_TYPE");

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
        if (typeName.equals("java/lang/Thread") && !signature.contains("Runnable")) {
            ctx.report("UselessThread", 0, node);
        } else if (typeName.equals("java/math/BigDecimal") && signature.equals("(D)V")) {
            Object value = Nodes.getConstant(node.getArguments().get(0));
            if (value instanceof Double) {
                Double val = (Double) value;
                if (val.isInfinite() || val.isNaN()) {
                    ctx.report("BigDecimalConstructedFromInfiniteOrNaN", 0, node, Roles.NUMBER.create(val));
                } else {
                    double arg = val.doubleValue();
                    String dblString = value.toString();
                    String bigDecimalString = new BigDecimal(arg).toString();
                    boolean ok = dblString.equals(bigDecimalString) || dblString.equals(bigDecimalString + ".0");

                    if (!ok) {
                        boolean scary = dblString.length() <= 8 && bigDecimalString.length() > 12 && dblString
                                .toUpperCase().indexOf('E') == -1;
                        ctx.report("BigDecimalConstructedFromDouble", scary ? 0 : 15, node, Roles.REPLACEMENT_METHOD
                                .create("java/math/BigDecimal", "valueOf", "(D)Ljava/math/BigDecimal;"), DOUBLE_NUMBER
                                        .create(dblString), BIGDECIMAL_NUMBER.create(bigDecimalString));
                    }
                }
            }
        }
    }

    private void check(Expression node, MethodReference mr, NodeChain nc, MethodContext ctx,
            MethodDefinition curMethod) {
        String typeName = mr.getDeclaringType().getInternalName();
        String name = mr.getName();
        String signature = mr.getSignature();
        if (typeName.equals("java/lang/System") && name.equals("exit")) {
            String curName = curMethod.getName();
            if (Methods.isMain(curMethod) || curName.equals("processWindowEvent") || curName.startsWith("windowClos"))
                return;
            int priority = 0;
            curName = curName.toLowerCase(Locale.ENGLISH);
            if (curName.indexOf("exit") > -1 || curName.indexOf("crash") > -1 || curName.indexOf("die") > -1 || curName
                    .indexOf("destroy") > -1 || curName.indexOf("close") > -1 || curName.indexOf("main") > -1)
                priority += 20;
            if (curMethod.isStatic())
                priority += 10;
            String curType = curMethod.getDeclaringType().getInternalName();
            if (curType.endsWith("Applet") || curType.endsWith("App") || curType.endsWith("Application"))
                priority += 10;
            if (curMethod.getDeclaringType().getDeclaredMethods().stream().anyMatch(Methods::isMain))
                priority += 20;
            ctx.report("SystemExit", priority, node);
        } else if ((typeName.equals("java/lang/System") || typeName.equals("java/lang/Runtime")) && name.equals("gc")
            && signature.equals("()V")) {
            String curName = curMethod.getName();
            if (Methods.isMain(curMethod) || curName.startsWith("test"))
                return;
            if (nc.isInCatch("java/lang/OutOfMemoryError"))
                return;
            if (Nodes.find(nc.getRoot(), BadMethodCalls::isTimeMeasure) != null)
                return;
            int priority = 0;
            if (curName.toLowerCase(Locale.ENGLISH).contains("garbage") || curName.toLowerCase(Locale.ENGLISH).contains(
                "memory") || curName.startsWith("gc") || curName.endsWith("gc"))
                priority += 10;
            ctx.report("SystemGc", priority, node);
        } else if ((typeName.equals("java/lang/System") || typeName.equals("java/lang/Runtime")) && name.equals(
            "runFinalizersOnExit")) {
            ctx.report("SystemRunFinalizersOnExit", 0, node);
        } else if (typeName.equals("java/lang/Thread") && name.equals("stop") && signature.equals(
            "(Ljava/lang/Throwable;)V")) {
            ctx.report("ThreadStopThrowable", 0, node);
        } else if (typeName.equals("java/net/URL") && (name.equals("equals") || name.equals("hashCode"))) {
            ctx.report("URLBlockingMethod", 0, node);
        } else if (isToStringCall(typeName, name, signature)) {
            Expression lastArg = Nodes.getChild(node, node.getArguments().size() - 1);
            TypeReference type = ValuesFlow.reduceType(lastArg);
            if (type != null && type.isArray()) {
                ctx.report("ArrayToString", 0, lastArg);
            }
        } else if (name.equals("hashCode") && signature.equals("()I") || typeName.equals("java/util/Objects") && name
                .equals("hashCode") && signature.equals("(Ljava/lang/Object;)I")) {
            Expression arg = Nodes.getChild(node, 0);
            TypeReference type = ValuesFlow.reduceType(arg);
            if (type != null && type.isArray()) {
                ctx.report("ArrayHashCode", 0, arg);
            }
        } else if (typeName.equals("java/util/Objects") && name.equals("hash") && signature.equals(
            "([Ljava/lang/Object;)I")) {
            Expression arg = node.getArguments().get(0);
            if (arg.getCode() == AstCode.InitArray) {
                for (Expression child : arg.getArguments()) {
                    TypeReference type = ValuesFlow.reduceType(ValuesFlow.getSource(child));
                    if (type != null && type.isArray()) {
                        ctx.report("ArrayHashCode", 0, arg);
                    }
                }
            }
        } else if (typeName.equals("java/lang/Double") && name.equals("longBitsToDouble")) {
            Expression arg = Nodes.getChild(node, 0);
            if (arg.getCode() == AstCode.I2L) {
                ctx.report("DoubleLongBitsToDoubleOnInt", 0, arg, Roles.RETURN_VALUE_OF.create(mr));
            }
        } else if (typeName.equals("java/util/concurrent/ThreadPoolExecutor") && name.equals("setMaximumPoolSize")) {
            TypeReference type = ValuesFlow.reduceType(node.getArguments().get(0));
            if (type.getInternalName().equals("java/util/concurrent/ScheduledThreadPoolExecutor"))
                ctx.report("ScheduledThreadPoolExecutorChangePoolSize", 0, node, ARG_TYPE.create(type));
        } else if ((typeName.equals("java/util/Date") || typeName.equals("java/sql/Date")) && signature.equals("(I)V")
            && name.equals("setMonth")) {
            Object month = Nodes.getConstant(node.getArguments().get(1));
            if (month instanceof Integer) {
                int m = (int) month;
                if (m < 0 || m > 11) {
                    ctx.report("DateBadMonth", 0, node, Roles.NUMBER.create(m));
                }
            }
        } else if (name.equals("add") && mr.getErasedSignature().equals("(Ljava/lang/Object;)Z") && Types.isCollection(
            mr.getDeclaringType())) {
            if (Nodes.isEquivalent(Nodes.getChild(node, 0), Nodes.getChild(node, 1))) {
                ctx.report("CollectionAddedToItself", 0, node);
            }
        } else if (node.getCode() == AstCode.InvokeStatic && (typeName.endsWith("/Assert") && name.equals(
            "assertNotNull") || typeName.equals("com/google/common/base/Preconditions") && name.equals("checkNotNull")
            || typeName.equals("java/util/Objects") && name.equals("requireNonNull") || typeName.equals(
                "com/google/common/base/Strings") && (name.equals("nullToEmpty") || name.equals("emptyToNull") || name
                        .equals("isNullOrEmpty")))) {
            if (node.getArguments().size() == 1) {
                Expression arg = node.getArguments().get(0);
                Object constant = Nodes.getConstant(arg);
                if (constant != null) {
                    ctx.report("NullCheckMethodForConstant", 0, node, Roles.CALLED_METHOD.create(mr));
                }
            }
            if (node.getArguments().size() == 2) {
                Object stringArg = null, objArg = null;
                if (mr.getErasedSignature().startsWith("(Ljava/lang/Object;Ljava/lang/String;)")) {
                    objArg = Nodes.getConstant(node.getArguments().get(0));
                    stringArg = Nodes.getConstant(node.getArguments().get(1));
                } else if (mr.getErasedSignature().startsWith("(Ljava/lang/String;Ljava/lang/Object;)")) {
                    objArg = Nodes.getConstant(node.getArguments().get(1));
                    stringArg = Nodes.getConstant(node.getArguments().get(0));
                }
                if (objArg instanceof String && !(stringArg instanceof String)) {
                    ctx.report("WrongArgumentOrder", 0, node.getArguments().get(0), Roles.CALLED_METHOD.create(mr),
                        Roles.STRING.create((String) objArg));
                }
            }
        }
    }

    private boolean isToStringCall(String typeName, String name, String signature) {
        if (name.equals("toString") && signature.equals("()Ljava/lang/String;"))
            return true;
        if (name.equals("append") && typeName.startsWith("java/lang/StringBu") && signature.startsWith(
            "(Ljava/lang/Object;)Ljava/lang/StringBu"))
            return true;
        if ((name.equals("print") || name.equals("println")) && signature.equals("(Ljava/lang/Object;)V"))
            return true;
        return false;
    }

    private static boolean isTimeMeasure(Node node) {
        if (!Nodes.isOp(node, AstCode.InvokeStatic))
            return false;
        MethodReference mr = (MethodReference) ((Expression) node).getOperand();
        return mr.getName().equals("currentTimeMillis") || mr.getName().equals("nanoTime");
    }
}
