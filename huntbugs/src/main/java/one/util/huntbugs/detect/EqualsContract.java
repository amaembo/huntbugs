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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.ClassContext;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.VisitOrder;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;
import one.util.huntbugs.warning.Role.MemberRole;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "BadPractice", name = "EqualsReturnsTrue", maxScore = 50)
@WarningDefinition(category = "BadPractice", name = "EqualsReturnsFalse", maxScore = 50)
@WarningDefinition(category = "BadPractice", name = "EqualsClassNames", maxScore = 45)
@WarningDefinition(category = "BadPractice", name = "EqualsOther", maxScore = 40)
@WarningDefinition(category = "BadPractice", name = "EqualsSelf", maxScore = 50)
@WarningDefinition(category = "BadPractice", name = "EqualsEnum", maxScore = 60)
@WarningDefinition(category = "BadPractice", name = "HashCodeObjectEquals", maxScore = 45)
@WarningDefinition(category = "BadPractice", name = "HashCodeNoEquals", maxScore = 55)
@WarningDefinition(category = "BadPractice", name = "EqualsObjectHashCode", maxScore = 45)
@WarningDefinition(category = "BadPractice", name = "EqualsNoHashCode", maxScore = 55)
@WarningDefinition(category = "Correctness", name = "EqualsSuspiciousFieldComparison", maxScore = 60)
public class EqualsContract {
    private static final MemberRole NORMAL_EQUALS = MemberRole.forName("NORMAL_EQUALS");
    private static final MemberRole OTHER_FIELD = MemberRole.forName("OTHER_FIELD");

    boolean alwaysFalse = false;
    boolean instanceCheckingEquals = false;

    @AstVisitor(nodes = AstNodes.ROOT, methodName = "equals", methodSignature = "(Ljava/lang/Object;)Z")
    public void visitEquals(Block body, MethodContext mc, TypeDefinition td) {
        List<Node> list = body.getBody();
        if (list.size() == 1) {
            Node node = list.get(0);
            if (Nodes.isOp(node, AstCode.Return)) {
                Expression retVal = ((Expression) node).getArguments().get(0);
                Object constant = Nodes.getConstant(retVal);
                int priority = getPriority(td);
                if (((Integer) 1).equals(constant))
                    mc.report("EqualsReturnsTrue", priority, node);
                else if (((Integer) 0).equals(constant)) {
                    mc.report("EqualsReturnsFalse", priority, node);
                    alwaysFalse = true;
                }
                if(isInstanceOfAndSuperEquals(retVal))
                    instanceCheckingEquals = true;
                // Check the following pattern:
                // public boolean equals(Object x) {
                //     return this == x || x instanceof XYZ && super.equals(x);
                // }
                if(retVal.getCode() == AstCode.LogicalOr && isSelfCheck(retVal.getArguments().get(0)) && isInstanceOfAndSuperEquals(retVal.getArguments().get(1))) {
                    instanceCheckingEquals = true;
                }
            }
        }
        if (list.size() == 2) {
            // Check the following pattern:
            // public boolean equals(Object x) {
            //     if(!(x instanceof XYZ)) return false;
            //     return super.equals(x);
            // }
            Node node1 = list.get(0);
            Node node2 = list.get(1);
            if (node1 instanceof Condition) {
                Expression cond = ((Condition) node1).getCondition();
                if (cond.getCode() == AstCode.LogicalNot && cond.getArguments().get(0).getCode() == AstCode.InstanceOf) {
                    if (Nodes.isOp(node2, AstCode.Return)) {
                        Expression child = ((Expression) node2).getArguments().get(0);
                        if (child.getCode() == AstCode.InvokeSpecial
                            && Methods.isEqualsMethod((MethodReference) child.getOperand())) {
                            instanceCheckingEquals = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * @param expr expression to check
     * @return true if expression is like x instanceof XYZ && super.equals(x) 
     */
    private static boolean isInstanceOfAndSuperEquals(Expression expr) {
        boolean isInstanceOfAndSuperEquals = false;
        if (expr.getCode() == AstCode.LogicalAnd) {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            if (left.getCode() == AstCode.InstanceOf && right.getCode() == AstCode.InvokeSpecial
                && Methods.isEqualsMethod((MethodReference) right.getOperand())) {
                isInstanceOfAndSuperEquals = true;
            }
        }
        return isInstanceOfAndSuperEquals;
    }
    
    /**
     * @param expr expression to check
     * @return true if expression is like this == arg 
     */
    private static boolean isSelfCheck(Expression expr) {
        if(expr.getCode() == AstCode.CmpEq) {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            return Exprs.isThis(left) && right.getCode() == AstCode.Load ||
                    Exprs.isThis(right) && left.getCode() == AstCode.Load;
        }
        return false;
    }

    @ClassVisitor(order = VisitOrder.AFTER)
    public void visitClass(TypeDefinition td, ClassContext cc) {
        MethodDefinition equalsSelf = null;
        MethodDefinition equalsObject = null;
        MethodDefinition equalsOther = null;
        MethodDefinition hashCode = null;
        MethodDefinition superEquals = null;
        MethodDefinition superHashCode = null;
        int basePriority = td.isPrivate() || td.isPackagePrivate() ? 20 : 0;
        for (MethodDefinition md : td.getDeclaredMethods()) {
            if (!md.isStatic() && md.getName().equals("equals")
                && md.getReturnType().getSimpleType() == JvmType.Boolean && md.getParameters().size() == 1) {
                TypeReference type = md.getParameters().get(0).getParameterType();
                if (Types.isObject(type)) {
                    equalsObject = md;
                } else if (type.isEquivalentTo(td)) {
                    equalsSelf = md;
                } else if (!type.isPrimitive()) {
                    equalsOther = md;
                }
            }
            if (!md.isStatic() && !md.isBridgeMethod() && Methods.isHashCodeMethod(md)) {
                hashCode = md;
            }
        }
        if (equalsObject == null) {
            superEquals = Methods.findSuperMethod(td, new MemberInfo(td.getInternalName(), "equals",
                    "(Ljava/lang/Object;)Z"));
        }
        if (hashCode == null) {
            superHashCode = Methods.findSuperMethod(td, new MemberInfo(td.getInternalName(), "hashCode", "()I"));
        }
        if (hashCode != null && !hashCode.isAbstract() && equalsObject == null && equalsSelf == null) {
            if (superEquals == null || Types.isObject(superEquals.getDeclaringType())) {
                cc.report("HashCodeObjectEquals", basePriority, Roles.METHOD.create(hashCode));
            } else if (!superEquals.isFinal()) {
                cc.report("HashCodeNoEquals", basePriority, Roles.METHOD.create(hashCode), Roles.SUPER_METHOD
                        .create(superEquals));
            }
        }
        if (hashCode == null && equalsObject != null && !alwaysFalse && !instanceCheckingEquals) {
            if (superHashCode == null || Types.isObject(superHashCode.getDeclaringType())) {
                int priority = basePriority;
                if (Flags.testAny(td.getFlags(), Flags.ABSTRACT)) {
                    priority += 10;
                }
                cc.report("EqualsObjectHashCode", priority, Roles.METHOD.create(equalsObject));
            } else if (!superHashCode.getDeclaringType().getInternalName().startsWith("java/util/Abstract")
                && !Methods.isThrower(superHashCode)) {
                int priority = basePriority;
                if (Flags.testAny(td.getFlags(), Flags.ABSTRACT)) {
                    priority += 10;
                }
                if (td.getDeclaredFields().isEmpty())
                    priority += 10;
                cc.report("EqualsNoHashCode", priority, Roles.METHOD.create(equalsObject), Roles.SUPER_METHOD
                        .create(superHashCode));
            }
        }
        if (equalsObject == null && equalsSelf == null && equalsOther != null) {
            cc.report("EqualsOther", getPriority(td) + getPriority(equalsOther), Roles.METHOD.create(equalsOther),
                NORMAL_EQUALS.create("java/lang/Object", "equals", "(Ljava/lang/Object;)Z"));
        } else if (equalsObject == null && equalsSelf != null) {
            if (Types.isInstance(td, "java/lang/Enum"))
                cc.report("EqualsEnum", getPriority(td) + getPriority(equalsSelf), Roles.METHOD.create(equalsSelf),
                    NORMAL_EQUALS.create("java/lang/Enum", "equals", "(Ljava/lang/Object;)Z"));
            else
                cc.report("EqualsSelf", getPriority(td) + getPriority(equalsSelf), Roles.METHOD.create(equalsSelf),
                    NORMAL_EQUALS.create("java/lang/Object", "equals", "(Ljava/lang/Object;)Z"));
        }
    }

    private static int getPriority(TypeDefinition td) {
        int priority = 0;
        if (td.isNonPublic())
            priority += 30;
        if (td.isFinal())
            priority += 5;
        return priority;
    }

    private static int getPriority(MethodDefinition md) {
        int priority = 0;
        if (md.isProtected())
            priority += 10;
        else if (md.isPackagePrivate() || md.isPrivate())
            priority += 20;
        return priority;
    }

    @AstVisitor(nodes = AstNodes.EXPRESSIONS, methodName = "equals", methodSignature = "(Ljava/lang/Object;)Z")
    public void visitEqualsExpression(Expression expr, MethodContext mc) {
        if(isComparison(expr)) {
            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);
            if(left.getCode() == AstCode.GetField && right.getCode() == AstCode.GetField) {
                FieldReference lfr = ((FieldReference) left.getOperand());
                FieldReference rfr = ((FieldReference) right.getOperand());
                if(!lfr.isEquivalentTo(rfr)) {
                    lfr = lfr.resolve();
                    rfr = rfr.resolve();
                    if(lfr != null && rfr != null && !lfr.isEquivalentTo(rfr)) {
                        if (Exprs.bothMatch(Exprs.getChild(left, 0), Exprs.getChild(right, 0), Exprs::isThis,
                            EqualsContract::isParameter)) {
                            mc.report("EqualsSuspiciousFieldComparison", 0, left, OTHER_FIELD.create(rfr));
                        }
                    }
                }
            }
            if (expr.getCode() == AstCode.InvokeVirtual && Methods.isEqualsMethod((MethodReference) expr.getOperand())) {
                checkGetName(expr, mc, left);
                checkGetName(expr, mc, right);
            }
        }
    }
    
    private static boolean isParameter(Expression expr) {
        if(expr.getCode() == AstCode.CheckCast) {
            expr = Exprs.getChild(expr, 0);
        }
        return Exprs.isParameter(expr) && !Exprs.isThis(expr);
    }

    private static boolean isComparison(Expression expr) {
        switch(expr.getCode()) {
        case CmpEq:
        case CmpNe:
            return true;
        case InvokeVirtual:
            return Methods.isEqualsMethod((MethodReference) expr.getOperand());
        case InvokeStatic: {
            MethodReference mr = (MethodReference) expr.getOperand();
            TypeReference tr = mr.getDeclaringType();
            return (mr.getName().equals("equals") || mr.getName().equals("deepEquals")) &&
                    (Types.is(tr, Arrays.class) || Types.is(tr, Objects.class));
        }
        default:
            return false;
        }
    }

    private void checkGetName(Expression expr, MethodContext mc, Expression getName) {
        if (isClassGetName(getName)) {
            Expression getClass = Exprs.getChild(getName, 0);
            if (isGetClass(getClass)) {
                Expression source = Exprs.getChild(getClass, 0);
                if (Exprs.isThis(source) || Exprs.isParameter(source)) {
                    mc.report("EqualsClassNames", 0, expr);
                }
            }
        }
    }

    private static boolean isClassGetName(Expression expr) {
        if (expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if (mr.getName().equals("getName") && Types.is(mr.getDeclaringType(), Class.class))
                return true;
        }
        return false;
    }

    private static boolean isGetClass(Expression expr) {
        if (expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            return Methods.isGetClass(mr);
        }
        return false;
    }
}
