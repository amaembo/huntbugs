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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.strobel.assembler.ir.Instruction;
import com.strobel.assembler.metadata.DynamicCallSite;
import com.strobel.assembler.metadata.MethodBody;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodHandle;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.assembler.metadata.annotations.CustomAnnotation;

import one.util.huntbugs.registry.ClassContext;
import one.util.huntbugs.registry.anno.AssertWarning;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category="RedundantCode", name="UncalledPrivateMethod", maxScore=45)
@WarningDefinition(category="RedundantCode", name="UncalledPrivateMethodChain", maxScore=50)
public class UncalledPrivateMethod {
    private static final Set<String> RESERVED_NAMES = 
            new HashSet<>(Arrays.asList("writeReplace", "readResolve",
                "readObject", "readObjectNoData", "writeObject"));
    
    private final Map<MemberInfo, Set<MemberInfo>> candidates = new LinkedHashMap<>();
    
    @ClassVisitor
    public void visitType(TypeDefinition td, ClassContext cc) {
        for(MethodDefinition md : td.getDeclaredMethods()) {
            if(md.isPrivate() && !md.isSpecialName() 
                    && !RESERVED_NAMES.contains(md.getName())
                    && !md.getName().toLowerCase(Locale.ENGLISH).contains("debug")
                    && !md.getName().toLowerCase(Locale.ENGLISH).contains("trace")
                    && !hasAnnotation(md)) {
                candidates.put(new MemberInfo(md), new HashSet<>());
            }
        }
        for(MethodDefinition md : td.getDeclaredMethods()) {
            if(candidates.isEmpty())
                return;
            MethodBody body = md.getBody();
            if(body == null)
                continue;
            for(Instruction inst : body.getInstructions()) {
                for(int i=0; i<inst.getOperandCount(); i++) {
                    Object operand = inst.getOperand(i);
                    if(operand instanceof MethodReference) {
                        link(md, (MethodReference)operand);
                        if(candidates.isEmpty())
                            return;
                    }
                    if(operand instanceof DynamicCallSite) {
                        MethodHandle mh = Nodes.getMethodHandle((DynamicCallSite) operand);
                        if(mh != null) {
                            link(md, mh.getMethod());
                            if(candidates.isEmpty())
                                return;
                        }
                    }
                }
            }
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
            if(called.isEmpty()) {
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

    private static boolean hasAnnotation(MethodDefinition md) {
        for(CustomAnnotation ca : md.getAnnotations()) {
            TypeReference annoType = ca.getAnnotationType();
            if(annoType.getPackageName().equals(AssertWarning.class.getPackage().getName()))
                continue;
            if(annoType.getInternalName().equals("java/lang/Deprecated"))
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
