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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.assembler.metadata.annotations.CustomAnnotation;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.db.FieldStats;
import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.FieldContext;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AssertWarning;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.FieldVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.VisitOrder;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.Location;

/**
 * @author lan
 *
 */
@WarningDefinition(category="RedundantCode", name="UnusedPrivateField", maxScore=45)
@WarningDefinition(category="RedundantCode", name="UnusedPublicField", maxScore=38)
@WarningDefinition(category="RedundantCode", name="UnreadPrivateField", maxScore=48)
@WarningDefinition(category="RedundantCode", name="UnreadPublicField", maxScore=37)
@WarningDefinition(category="Correctness", name="UnwrittenPrivateField", maxScore=60)
@WarningDefinition(category="Correctness", name="UnwrittenPublicField", maxScore=45)
@WarningDefinition(category="Correctness", name="FieldIsAlwaysNull", maxScore=55)
@WarningDefinition(category="Performance", name="FieldShouldBeStatic", maxScore=50)
@WarningDefinition(category="Performance", name="FieldUsedInSingleMethod", maxScore=55)
public class FieldAccess {
    static class FieldRecord {
        MethodReference firstWriteMethod;
        MethodReference firstReadMethod;
        Location firstWriteLocation;
        Location firstReadLocation;
        Object constant;
        boolean usedInSingleMethod = true;
    }
    
    private final Map<String, FieldRecord> fields = new HashMap<>();
    private boolean fullyAnalyzed = true;
    
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visitCode(Expression expr, MethodContext mc, MethodDefinition md, TypeDefinition td) {
        if(expr.getCode() == AstCode.PutField || expr.getCode() == AstCode.PutStatic ||
                expr.getCode() == AstCode.GetField || expr.getCode() == AstCode.GetStatic) {
            FieldDefinition fd = ((FieldReference) expr.getOperand()).resolve();
            if(fd != null && !fd.isSynthetic() && fd.getDeclaringType().isEquivalentTo(td)) {
                FieldRecord fieldRecord = fields.computeIfAbsent(fd.getName(), n -> new FieldRecord());
                if(Nodes.isFieldRead(expr)) {
                    if(fieldRecord.firstReadMethod == null) {
                        fieldRecord.firstReadMethod = md;
                        fieldRecord.firstReadLocation = mc.getLocation(expr);
                    }
                } else {
                    if(fieldRecord.firstWriteMethod == null) {
                        fieldRecord.firstWriteMethod = md;
                        fieldRecord.firstWriteLocation = mc.getLocation(expr);
                        fieldRecord.constant = Nodes.getConstant(Nodes.getChild(expr, expr.getArguments().size()-1));
                    } else {
                        if(fieldRecord.constant != null) {
                            Object constant = Nodes.getConstant(Nodes.getChild(expr, expr.getArguments().size()-1));
                            if(!Objects.equals(fieldRecord.constant, constant))
                                fieldRecord.constant = null;
                        }
                    }
                }
                if(fieldRecord.usedInSingleMethod) {
                    if (md.isTypeInitializer()) {
                        fieldRecord.usedInSingleMethod = false;
                    }
                    if (Nodes.isFieldRead(expr) && ValuesFlow.getSource(expr) == expr) {
                        fieldRecord.usedInSingleMethod = false;
                    }
                    if((expr.getCode() == AstCode.PutField || expr.getCode() == AstCode.GetField) && 
                            (md.isStatic() || !Nodes.isThis(Nodes.getChild(expr, 0)))) {
                        fieldRecord.usedInSingleMethod = false;
                    }
                    if(fieldRecord.firstWriteMethod != null && fieldRecord.firstWriteMethod != md || 
                            fieldRecord.firstReadMethod != null && fieldRecord.firstReadMethod != md) {
                        fieldRecord.usedInSingleMethod = false;
                    }
                }
            }
        }
    }
    
    @MethodVisitor(order=VisitOrder.AFTER)
    public void checkAnalyzed(MethodContext mc) {
        fullyAnalyzed &= mc.isFullyAnalyzed();
    }
    
    @FieldVisitor
    public void visit(FieldContext fc, FieldDefinition fd, TypeDefinition td, FieldStats fs) {
        if(fd.isSynthetic() || fd.isEnumConstant())
            return;
        int flags = fs.getFlags(fd);
        if(Flags.testAny(flags, FieldStats.UNRESOLVED) || hasAnnotation(fd)) {
            return;
        }
        boolean isConstantType = fd.getFieldType().isPrimitive() || Types.is(fd.getFieldType(), String.class);
        if(!Flags.testAny(flags, FieldStats.ACCESS)) {
            if(fd.isStatic() && fd.isFinal() && isConstantType)
                return;
            // Autogenerated by javacc 
            if(fd.getName().equals("lengthOfMatch") && td.getName().endsWith("TokenManager"))
                return;
            fc.report(fd.isPublic() || fd.isProtected() ? "UnusedPublicField" : "UnusedPrivateField", fd.isPublic() ? 5 : 0);
            return;
        }
        FieldRecord fieldRecord = fields.get(fd.getName());
        if (fieldRecord != null && !fd.isStatic() && fd.isFinal() && fieldRecord.constant != null) {
            fc.report("FieldShouldBeStatic", 0, Roles.METHOD.create(fieldRecord.firstWriteMethod),
                Roles.LOCATION.create(fieldRecord.firstWriteLocation));
            return;
        }
        if(!Flags.testAny(flags, FieldStats.READ)) {
            // Autogenerated by javacc 
            if(fd.getName().startsWith("jj") && td.getName().endsWith("TokenManager"))
                return;
            if(fd.getName().equals("errorCode") && td.getSimpleName().equals("TokenMgrError"))
                return;
            WarningAnnotation<?>[] anno = {};
            int priority = 0;
            String warningType = fd.isPublic() || fd.isProtected() ? "UnreadPublicField" : "UnreadPrivateField";
            if (fieldRecord != null && fieldRecord.firstWriteMethod != null) {
                anno = new WarningAnnotation[] { Roles.METHOD.create(fieldRecord.firstWriteMethod),
                        Roles.LOCATION.create(fieldRecord.firstWriteLocation) };
            }
            if(fd.isPublic()) {
                priority += 5;
                if(fd.isFinal()) {
                    priority += 5;
                    if(fd.isStatic()) {
                        priority += 10;
                    }
                }
            }
            fc.report(warningType, priority, anno);
            return;
        }
        if(!Flags.testAny(flags, FieldStats.WRITE)) {
            WarningAnnotation<?>[] anno = {};
            int priority = 0;
            String warningType = fd.isPublic() || fd.isProtected() ? "UnwrittenPublicField" : "UnwrittenPrivateField";
            if (fieldRecord != null && fieldRecord.firstReadMethod != null) {
                anno = new WarningAnnotation[] { Roles.METHOD.create(fieldRecord.firstReadMethod),
                        Roles.LOCATION.create(fieldRecord.firstReadLocation) };
            }
            if(fd.isPublic()) {
                priority += 5;
            }
            fc.report(warningType, priority, anno);
            return;
        }
        if(!Flags.testAny(flags, FieldStats.WRITE_NONNULL)) {
            WarningAnnotation<?>[] anno = {};
            int priority = 0;
            if (fieldRecord != null && fieldRecord.firstWriteMethod != null) {
                anno = new WarningAnnotation[] { Roles.METHOD.create(fieldRecord.firstWriteMethod),
                        Roles.LOCATION.create(fieldRecord.firstWriteLocation) };
            }
            if(fd.isPublic()) {
                priority += 10;
            }
            fc.report("FieldIsAlwaysNull", priority, anno);
            return;
        }
        if (fullyAnalyzed
            && !Flags.testAny(flags, FieldStats.READ_PACKAGE | FieldStats.READ_OUTSIDE | FieldStats.WRITE_PACKAGE
                | FieldStats.WRITE_OUTSIDE) && fieldRecord != null && fieldRecord.usedInSingleMethod
            && fieldRecord.firstWriteLocation != null) {
            int priority = 0;
            if(!fd.isStatic())
                priority += 5;
            if(fd.isPublic())
                priority += 10;
            else if(fd.isProtected())
                priority += 3;
            if(fieldRecord.firstWriteMethod.isConstructor())
                priority += 5;
            if(fd.getFieldType().isPrimitive())
                priority += 3;
            fc.report("FieldUsedInSingleMethod", priority, Roles.METHOD.create(fieldRecord.firstWriteMethod),
                Roles.LOCATION.create(fieldRecord.firstWriteLocation));
        }
    }

    private static boolean hasAnnotation(FieldDefinition fd) {
        for(CustomAnnotation ca : fd.getAnnotations()) {
            TypeReference annoType = ca.getAnnotationType();
            if(annoType.getPackageName().equals(AssertWarning.class.getPackage().getName()))
                continue;
            if(annoType.getSimpleName().equalsIgnoreCase("nonnull") ||
                   annoType.getSimpleName().equalsIgnoreCase("notnull") ||
                   annoType.getSimpleName().equalsIgnoreCase("nullable") ||
                   annoType.getSimpleName().equalsIgnoreCase("checkfornull"))
                continue;
            return true;
        }
        return false;
    }
}
