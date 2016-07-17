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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.core.ArrayUtilities;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.db.FieldStats;
import one.util.huntbugs.db.Mutability;
import one.util.huntbugs.flow.Inf;
import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.FieldContext;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.FieldVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.VisitOrder;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.AccessLevel;
import one.util.huntbugs.util.Annotations;
import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.NodeChain;
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
@WarningDefinition(category="MaliciousCode", name="StaticFieldShouldBeFinal", maxScore=55)
@WarningDefinition(category="MaliciousCode", name="StaticFieldShouldBeFinalAndPackagePrivate", maxScore=55)
@WarningDefinition(category="MaliciousCode", name="StaticFieldCannotBeFinal", maxScore=35)
@WarningDefinition(category="MaliciousCode", name="StaticFieldMutableArray", maxScore=40)
@WarningDefinition(category="MaliciousCode", name="StaticFieldMutableCollection", maxScore=45)
@WarningDefinition(category="MaliciousCode", name="StaticFieldMutable", maxScore=40)
@WarningDefinition(category="MaliciousCode", name="StaticFieldShouldBeRefactoredToFinal", maxScore=40)
@WarningDefinition(category="MaliciousCode", name="StaticFieldShouldBePackagePrivate", maxScore=55)
@WarningDefinition(category="MaliciousCode", name="StaticFieldShouldBeNonInterfacePackagePrivate", maxScore=30)
@WarningDefinition(category = "MaliciousCode", name = "ExposeMutableFieldViaReturnValue", maxScore = 35)
@WarningDefinition(category = "MaliciousCode", name = "ExposeMutableStaticFieldViaReturnValue", maxScore = 50)
@WarningDefinition(category = "MaliciousCode", name = "MutableEnumField", maxScore = 55)
public class FieldAccess {
    private static final Set<String> MUTABLE_COLLECTION_CLASSES = new HashSet<>(Arrays.asList("java/util/ArrayList",
        "java/util/HashSet", "java/util/HashMap", "java/util/Hashtable", "java/util/IdentityHashMap",
        "java/util/LinkedHashSet", "java/util/LinkedList", "java/util/LinkedHashMap", "java/util/TreeSet",
        "java/util/TreeMap", "java/util/Properties"));
    
    static class MethodLocation {
        MethodDefinition md;
        Location loc;

        public MethodLocation(MethodDefinition md, Location loc) {
            this.md = md;
            this.loc = loc;
        }
        
        public WarningAnnotation<?>[] getAnnotations() {
            WarningAnnotation<?>[] anno = {Roles.METHOD.create(md), Roles.LOCATION.create(loc)};
            return anno;
        }
    }
    
    static class FieldRecord {
        MethodLocation firstWrite, firstRead, expose;
        Object constant;
        int numWrites;
        boolean mutable;
        boolean array;
        boolean collection;
        boolean usedInSingleMethod = true;
        boolean hasSimpleSetter;
    }
    
    private final Map<String, FieldRecord> fields = new HashMap<>();
    private boolean fullyAnalyzed = true;

    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visitCode(Expression expr, NodeChain nc, MethodContext mc, MethodDefinition md, TypeDefinition td, Mutability m) {
        if(expr.getCode() == AstCode.PutField || expr.getCode() == AstCode.PutStatic ||
                expr.getCode() == AstCode.GetField || expr.getCode() == AstCode.GetStatic) {
            FieldDefinition fd = ((FieldReference) expr.getOperand()).resolve();
            if(fd != null && !fd.isSynthetic() && fd.getDeclaringType().isEquivalentTo(td)) {
                FieldRecord fieldRecord = fields.computeIfAbsent(fd.getName(), n -> new FieldRecord());
                if(Nodes.isFieldRead(expr)) {
                    if(fieldRecord.firstRead == null) {
                        fieldRecord.firstRead = new MethodLocation(md, mc.getLocation(expr));
                    }
                    if (Inf.BACKLINK.findTransitiveUsages(expr, true).anyMatch(
                        e -> e.getCode() == AstCode.Return
                            && (e.getArguments().get(0).getCode() == AstCode.GetField || !ValuesFlow.hasUpdatedSource(e
                                    .getArguments().get(0))))) {
                        fieldRecord.expose = new MethodLocation(md, mc.getLocation(expr));
                    }
                } else {
                    Expression value = Exprs.getChild(expr, expr.getArguments().size()-1);
                    if(fieldRecord.firstWrite == null) {
                        fieldRecord.firstWrite = new MethodLocation(md, mc.getLocation(expr));
                        fieldRecord.constant = Nodes.getConstant(value);
                    } else {
                        if(fieldRecord.constant != null) {
                            Object constant = Nodes.getConstant(value);
                            if(!Objects.equals(fieldRecord.constant, constant))
                                fieldRecord.constant = null;
                        }
                    }
                    if (md.isPublic() && nc.getParent() == null && nc.getRoot().getBody().size() == 1 && (expr
                            .getCode() == AstCode.PutField ^ md.isStatic()) && value
                                    .getOperand() instanceof ParameterDefinition) {
                        fieldRecord.hasSimpleSetter = true;
                    }
                    if(value.getCode() == AstCode.InitObject) {
                        String typeName = ((MethodReference) value.getOperand()).getDeclaringType().getInternalName();
                        if(MUTABLE_COLLECTION_CLASSES.contains(typeName)) {
                            fieldRecord.mutable = true;
                            fieldRecord.collection = true;
                        }
                    } else if(value.getCode() == AstCode.InvokeStatic) {
                        MethodReference mr = (MethodReference) value.getOperand();
                        if (isMutableCollectionFactory(value, mr)) {
                            fieldRecord.mutable = true;
                            fieldRecord.collection = true;
                        }
                    }
                    if(fd.getFieldType().isArray() || value.getInferredType() != null && value.getInferredType().isArray()) {
                        fieldRecord.array = true;
                        if (!isEmptyArray(value)) {
                            fieldRecord.mutable = true;
                        }
                    }
                    if(m.isKnownMutable(fd.getFieldType())) {
                        fieldRecord.mutable = true;
                    }
                    fieldRecord.numWrites++;
                }
                if(fieldRecord.usedInSingleMethod) {
                    if (md.isTypeInitializer()) {
                        fieldRecord.usedInSingleMethod = false;
                    }
                    if (Nodes.isFieldRead(expr) && ValuesFlow.getSource(expr) == expr) {
                        fieldRecord.usedInSingleMethod = false;
                    }
                    if((expr.getCode() == AstCode.PutField || expr.getCode() == AstCode.GetField) && 
                            (md.isStatic() || !Exprs.isThis(Exprs.getChild(expr, 0)))) {
                        fieldRecord.usedInSingleMethod = false;
                    }
                    if(fieldRecord.firstWrite != null && fieldRecord.firstWrite.md != md || 
                            fieldRecord.firstRead != null && fieldRecord.firstRead.md != md) {
                        fieldRecord.usedInSingleMethod = false;
                    }
                }
            }
        }
    }

    private boolean isMutableCollectionFactory(Expression value, MethodReference mr) {
        if (mr.getName().equals("asList") && mr.getDeclaringType().getInternalName().equals("java/util/Arrays")
            && value.getArguments().size() == 1 && !isEmptyArray(Exprs.getChild(value, 0)))
            return true;
        if ((mr.getName().equals("newArrayList") || mr.getName().equals("newLinkedList"))
            && mr.getDeclaringType().getInternalName().equals("com/google/common/collect/Lists"))
            return true;
        if ((mr.getName().equals("newHashSet") || mr.getName().equals("newTreeSet"))
            && mr.getDeclaringType().getInternalName().equals("com/google/common/collect/Sets"))
            return true;
        return false;
    }

    private static boolean isEmptyArray(Expression value) {
        return value.getCode() == AstCode.NewArray
            && Integer.valueOf(0).equals(Nodes.getConstant(value.getArguments().get(0)));
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
        if(Flags.testAny(flags, FieldStats.UNRESOLVED) || Annotations.hasAnnotation(fd, false)) {
            return;
        }
        boolean isConstantType = fd.getFieldType().isPrimitive() || Types.isString(fd.getFieldType());
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
            fc.report("FieldShouldBeStatic", 0, fieldRecord.firstWrite.getAnnotations());
            return;
        }
        if(!Flags.testAny(flags, FieldStats.READ)) {
            // Autogenerated by javacc 
            if(fd.getName().startsWith("jj") && td.getName().endsWith("TokenManager"))
                return;
            if(fd.getName().equals("errorCode") && td.getSimpleName().equals("TokenMgrError"))
                return;
            int priority = 0;
            String warningType = fd.isPublic() || fd.isProtected() ? "UnreadPublicField" : "UnreadPrivateField";
            if(fd.isPublic()) {
                priority += 5;
                if(fd.isFinal()) {
                    priority += 5;
                    if(fd.isStatic()) {
                        priority += 10;
                    }
                }
            }
            if(!fd.isStatic() && !fd.isPublic() && fd.getName().startsWith("ref") && fd.getFieldType().getSimpleType() == JvmType.Object) {
                // probably field is used to keep strong reference
                priority += 10;
            }
            fc.report(warningType, priority, getWriteAnnotations(fieldRecord));
        }
        if(checkWrite(fc, fd, td, fieldRecord, flags, isConstantType))
            return;
        if(checkNull(fc, fd, td, fieldRecord, flags))
            return;
        checkSingleMethod(fc, fd, fieldRecord, flags);
        if(td.isEnum() && fieldRecord != null && !fd.isStatic()) {
            boolean mutable = fieldRecord.mutable;
            if(fd.isPublic() && (!fd.isFinal() || mutable)) {
                fc.report("MutableEnumField", 0, getWriteAnnotations(fieldRecord));
                return;
            }
        }
        if(fd.isStatic() && (fd.isPublic() || fd.isProtected()) && (td.isPublic() || td.isProtected())) {
            boolean mutable = fieldRecord != null && fieldRecord.mutable;
            if(!fd.isFinal() && Flags.testAny(flags, FieldStats.WRITE_CONSTRUCTOR) &&
                    !Flags.testAny(flags, FieldStats.WRITE_CLASS | FieldStats.WRITE_PACKAGE | FieldStats.WRITE_OUTSIDE)) {
                String type = "StaticFieldShouldBeRefactoredToFinal";
                if(fieldRecord != null && fieldRecord.numWrites == 1) {
                    type = "StaticFieldShouldBeFinal";
                    if(mutable && !Flags.testAny(flags, FieldStats.READ_OUTSIDE)) {
                        type = "StaticFieldShouldBeFinalAndPackagePrivate";
                    }
                }
                fc.report(type, AccessLevel.of(fd).select(0, 10, 100, 100), getWriteAnnotations(fieldRecord));
                return;
            }
            if(mutable || !fd.isFinal()) {
                String type = null;
                WarningAnnotation<?>[] anno = ArrayUtilities.append(getWriteAnnotations(fieldRecord),
                    Roles.FIELD_TYPE.create(fd.getFieldType()));
                if(!Flags.testAny(flags, FieldStats.WRITE_OUTSIDE | FieldStats.READ_OUTSIDE)) {
                    type = td.isInterface() ? "StaticFieldShouldBeNonInterfacePackagePrivate"
                        : "StaticFieldShouldBePackagePrivate";
                } else if(!fd.isFinal()) {
                    type = "StaticFieldCannotBeFinal";
                } else if(mutable && fieldRecord.array) {
                    type = "StaticFieldMutableArray";
                } else if(mutable && fieldRecord.collection) {
                    type = "StaticFieldMutableCollection";
                } else if(mutable) {
                    type = "StaticFieldMutable";
                }
                if(type != null) {
                    fc.report(type, AccessLevel.of(fd).select(0, 10, 100, 100), anno);
                    return;
                }
            }
        }
        if(fieldRecord != null && (td.isPublic() || td.isProtected()) && (fd.isPrivate() || fd.isPackagePrivate())) {
            MethodLocation expose = fieldRecord.expose;
            if(fieldRecord.mutable && expose != null && (expose.md.isPublic() || expose.md.isProtected())) {
                String type = fd.isStatic() ? "ExposeMutableStaticFieldViaReturnValue" : "ExposeMutableFieldViaReturnValue";
                int priority = AccessLevel.of(expose.md).select(0, 10, 100, 100);
                if(fieldRecord.hasSimpleSetter)
                    priority += 15;
                else if(!fd.isFinal())
                    priority += 3;
                fc.report(type, priority, ArrayUtilities.append(expose
                        .getAnnotations(), Roles.FIELD_TYPE.create(fd.getFieldType())));
            }
        }
    }

    private boolean checkWrite(FieldContext fc, FieldDefinition fd, TypeDefinition td, FieldRecord fieldRecord, int flags, boolean isConstantType) {
        if(!Flags.testAny(flags, FieldStats.WRITE)) {
            if(fd.isStatic() && fd.isFinal() && isConstantType)
                return false;
            WarningAnnotation<?>[] anno = {};
            int priority = 0;
            String warningType = fd.isPublic() || fd.isProtected() ? "UnwrittenPublicField" : "UnwrittenPrivateField";
            if (fieldRecord != null && fieldRecord.firstRead != null) {
                anno = fieldRecord.firstRead.getAnnotations();
            }
            if(fd.isPublic()) {
                priority += 5;
            }
            priority += tweakForSerialization(fd, td);
            if(fd.getFieldType().getSimpleType() == JvmType.Boolean) {
                priority += 5;
            }
            if(fd.getName().equalsIgnoreCase("debug")) {
                priority += 5;
            }
            fc.report(warningType, priority, anno);
            return true;
        }
        return false;
    }

    private int tweakForSerialization(FieldDefinition fd, TypeDefinition td) {
        // Probably field is kept for backwards serialization compatibility
        if(!fd.isStatic() && Types.isInstance(td, "java/io/Serializable")) {
            return 10;
        }
        if(Flags.testAny(fd.getFlags(), Flags.TRANSIENT)) {
            return 30;
        }
        return 0;
    }

    private boolean checkNull(FieldContext fc, FieldDefinition fd, TypeDefinition td, FieldRecord fieldRecord, int flags) {
        if(!Flags.testAny(flags, FieldStats.WRITE_NONNULL) && Flags.testAny(flags, FieldStats.READ)) {
            int priority = 0;
            if(fd.isFinal() && fd.isStatic()) {
                priority += 20;
                String lcName = fd.getName().toLowerCase(Locale.ENGLISH);
                if (lcName.contains("null") || lcName.contains("zero") || lcName.contains("empty")) {
                    priority += 15;
                }
            } else if(fd.isPublic()) {
                priority += 10;
            }
            priority += tweakForSerialization(fd, td);
            fc.report("FieldIsAlwaysNull", priority, getWriteAnnotations(fieldRecord));
            return true;
        }
        return false;
    }

    private void checkSingleMethod(FieldContext fc, FieldDefinition fd, FieldRecord fieldRecord, int flags) {
        if (fullyAnalyzed
            && Flags.testAny(flags, FieldStats.READ)
            && !Flags.testAny(flags, FieldStats.READ_PACKAGE | FieldStats.READ_OUTSIDE | FieldStats.WRITE_PACKAGE
                | FieldStats.WRITE_OUTSIDE) && fieldRecord != null && fieldRecord.usedInSingleMethod
            && fieldRecord.firstWrite != null) {
            // javacc-generated
            if(fd.getName().startsWith("jj_") && fd.getDeclaringType().getSimpleName().endsWith("Parser") &&
                    fieldRecord.firstWrite.md.getName().equals("generateParseException"))
                return;
            int priority = AccessLevel.of(fd).select(10, 3, 1, 0);
            if(!fd.isStatic())
                priority += 5;
            if(fieldRecord.firstWrite.md.isConstructor())
                priority += 5;
            if(fd.getFieldType().isPrimitive())
                priority += 3;
            fc.report("FieldUsedInSingleMethod", priority, fieldRecord.firstWrite.getAnnotations());
        }
    }

    private WarningAnnotation<?>[] getWriteAnnotations(FieldRecord fieldRecord) {
        if (fieldRecord != null && fieldRecord.firstWrite != null) {
            return fieldRecord.firstWrite.getAnnotations();
        }
        WarningAnnotation<?>[] anno = {};
        return anno;
    }
}
