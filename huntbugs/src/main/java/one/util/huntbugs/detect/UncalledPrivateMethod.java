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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.strobel.assembler.ir.Instruction;
import com.strobel.assembler.metadata.DynamicCallSite;
import com.strobel.assembler.metadata.MethodBody;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodHandle;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import one.util.huntbugs.registry.AbstractTypeDatabase;
import one.util.huntbugs.registry.ClassContext;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.TypeDatabase;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Annotations;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category="RedundantCode", name="UncalledPrivateMethod", maxScore=45)
@WarningDefinition(category="RedundantCode", name="UncalledMethodOfAnonymousClass", maxScore=45)
@WarningDefinition(category="RedundantCode", name="UncalledPrivateMethodChain", maxScore=50)
public class UncalledPrivateMethod {
    
    @TypeDatabase
    public static class NestedAnonymousCalls extends AbstractTypeDatabase<Void> {
        Set<MemberInfo> mis = new HashSet<>();
        
        public NestedAnonymousCalls() {
            super(tr -> null);
        }

        @Override
        protected void visitType(TypeDefinition td) {
            TypeReference tr = td.getDeclaringType();
            if(tr == null) return;
            TypeDefinition outer = tr.resolve();
            if(outer == null || !outer.isAnonymous()) return;
            for(MethodDefinition md : td.getDeclaredMethods()) {
                extractCalls(md, mr -> {
                    mis.add(new MemberInfo(mr));
                    return true;
                });
            }
        }
        
        public boolean isCalled(MemberInfo mi) {
            return mis.contains(mi);
        }
    }
    
    private final Map<MemberInfo, Set<MemberInfo>> candidates = new LinkedHashMap<>();
    
    @ClassVisitor
    public void visitType(TypeDefinition td, ClassContext cc, NestedAnonymousCalls nac) {
        if(Types.isInstance(td, "com/sun/jna/Callback"))
            return;
        for(MethodDefinition md : td.getDeclaredMethods()) {
            if(md.isPrivate() && !md.isSpecialName() 
                    && !Methods.isSerializationMethod(md)
                    && !md.getName().toLowerCase(Locale.ENGLISH).contains("debug")
                    && !md.getName().toLowerCase(Locale.ENGLISH).contains("trace")
                    && !Annotations.hasAnnotation(md, true)) {
                candidates.put(new MemberInfo(md), new HashSet<>());
            }
        }
        if (td.isAnonymous() && !td.isSynthetic() && !td.getSimpleName().contains("$_invokeMethod_") && Types
                .hasCompleteHierarchy(td)) {
            for(MethodDefinition md : td.getDeclaredMethods()) {
                if (!md.isSpecialName() && !md.isPrivate() && !md.isSynthetic() && Methods.findSuperMethod(
                    md) == null) {
                    MemberInfo mi = new MemberInfo(md);
                    if(!nac.isCalled(mi)) {
                        candidates.put(mi, new HashSet<>());
                    }
                }
            }
        }
        for(MethodDefinition md : td.getDeclaredMethods()) {
            if(candidates.isEmpty())
                return;
            extractCalls(md, mr -> {
                link(md, mr);
                return !candidates.isEmpty();
            });
        }
        while(!candidates.isEmpty()) {
            MemberInfo mi = candidates.keySet().iterator().next();
            Set<MemberInfo> called = new HashSet<>(candidates.remove(mi));
            boolean changed = true;
            while(changed) {
                changed = false;
                for(MemberInfo callee : called) {
                    Set<MemberInfo> called2 = candidates.remove(callee);
                    if(called2 != null && called.addAll(called2)) {
                        changed = true;
                        break;
                    }
                }
            }
            if(td.isAnonymous()) {
                cc.report("UncalledMethodOfAnonymousClass", 0, Roles.METHOD.create(mi));
            } else if(called.isEmpty()) {
                cc.report("UncalledPrivateMethod", 0, Roles.METHOD.create(mi));
            } else {
                cc.report("UncalledPrivateMethodChain", 0, Stream.concat(Stream.of(Roles.METHOD.create(mi)),
                    called.stream()
                        .filter(c -> !c.equals(mi))
                        .map(Roles.CALLED_METHOD::create))
                        .toArray(WarningAnnotation[]::new));
            }
        }
    }
    
    static void extractCalls(MethodDefinition md, Predicate<MethodReference> action) {
        MethodBody body = md.getBody();
        if(body == null)
            return;
        for(Instruction inst : body.getInstructions()) {
            for(int i=0; i<inst.getOperandCount(); i++) {
                Object operand = inst.getOperand(i);
                if(operand instanceof MethodReference) {
                    if(!action.test((MethodReference)operand))
                        return;
                }
                if(operand instanceof DynamicCallSite) {
                    MethodHandle mh = Nodes.getMethodHandle((DynamicCallSite) operand);
                    if(mh != null) {
                        if(!action.test(mh.getMethod()))
                            return;
                    }
                }
            }
        }
    }
    
    private void link(MethodReference from, MethodReference to) {
        MemberInfo miTo = new MemberInfo(to);
        if(!candidates.containsKey(miTo))
            return;
        MemberInfo miFrom = new MemberInfo(from);
        Set<MemberInfo> curCandidate = candidates.get(miFrom);
        if(curCandidate == null) {
            remove(miTo);
        } else {
            curCandidate.add(miTo);
        }
    }

    private void remove(MemberInfo mi) {
        Set<MemberInfo> called = candidates.remove(mi);
        candidates.values().forEach(set -> set.remove(mi));
        if(called != null) {
            called.forEach(this::remove);
        }
    }
}
