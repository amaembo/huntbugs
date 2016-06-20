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
package one.util.huntbugs.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.strobel.assembler.ir.Instruction;
import com.strobel.assembler.ir.OpCode;
import com.strobel.assembler.metadata.MethodBody;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;

import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author Tagir Valeev
 *
 */
public class Methods {
    private static final Set<String> SERIALIZATION_METHODS = 
            new HashSet<>(Arrays.asList("writeReplace", "readResolve",
                "readObject", "readObjectNoData", "writeObject"));
    
    public static boolean isEqualsMethod(MethodReference mr) {
        return mr.getName().equals("equals") && mr.getSignature().equals("(Ljava/lang/Object;)Z");
    }

    public static boolean isGetClass(MethodReference mr) {
        return mr.getName().equals("getClass") && mr.getErasedSignature().equals("()Ljava/lang/Class;");
    }
    
    public static MethodDefinition findSuperMethod(MethodReference mr) {
        MethodDefinition md = mr.resolve();
        if(md == null)
            return null;
        TypeDefinition td = md.getDeclaringType();
        return findSuperMethod(td, new MemberInfo(resolveToBridge(md)));
    }
    
    public static MethodDefinition resolveToBridge(MethodDefinition md) {
        if (md.isBridgeMethod()) {
            return md;
        }
        for (MethodDefinition candidate : md.getDeclaringType().getDeclaredMethods()) {
            if (candidate.getName().equals(md.getName()) && candidate.isBridgeMethod()) {
                List<ParameterDefinition> params = candidate.getParameters();
                if (params.size() == md.getParameters().size()) {
                    MethodBody body = candidate.getBody();
                    if (body != null) {
                        for (Instruction instr : body.getInstructions()) {
                            if (instr.getOperandCount() == 1) {
                                Object operand = instr.getOperand(0);
                                if (operand instanceof MethodReference) {
                                    MethodReference mr = (MethodReference) operand;
                                    if (mr.getName().equals(md.getName()) && mr.getErasedSignature().equals(md
                                            .getErasedSignature()) && mr.getDeclaringType().isEquivalentTo(md
                                                    .getDeclaringType())) {
                                        return candidate;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return md;
    }
    
    public static MethodDefinition findSuperMethod(TypeDefinition type, MemberInfo mi) {
        TypeReference superType = type.getBaseType();
        if(superType != null) {
            TypeDefinition superTd = superType.resolve();
            if(superTd != null) {
                MethodDefinition result = findMethod(superTd, mi);
                if(result != null)
                    return result;
                result = findSuperMethod(superTd, mi);
                if(result != null)
                    return result;
            }
        }
        for(TypeReference iface : type.getExplicitInterfaces()) {
            TypeDefinition ifaceTd = iface.resolve();
            if(ifaceTd != null) {
                MethodDefinition result = findMethod(ifaceTd, mi);
                if(result != null)
                    return result;
                result = findSuperMethod(ifaceTd, mi);
                if(result != null)
                    return result;
            }
        }
        return null; 
    }

    public static MethodDefinition findMethod(TypeDefinition td, MemberInfo mi) {
        if(td == null)
            return null;
        for(MethodDefinition decl : td.getDeclaredMethods()) {
            if(decl.getName().equals(mi.getName())) {
                String sig1 = decl.getErasedSignature();
                String sig2 = mi.getSignature();
                if(sig1 == sig2)
                    return decl;
                if(sig1.substring(0, sig1.indexOf(')')).equals(sig2.substring(0, sig2.indexOf(')'))))
                    return decl;
            }
        }
        return null;
    }

    public static boolean isMain(MethodDefinition md) {
        return md.getName().equals("main") && md.isPublic() && md.isStatic() && md.getErasedSignature().startsWith("([Ljava/lang/String;)");
    }

    public static boolean isSideEffectFree(MethodReference mr) {
        if(isPure(mr))
            return true;
        if(isEqualsMethod(mr))
            return true;
        TypeReference tr = mr.getDeclaringType();
        String sig = mr.getErasedSignature();
        String name = mr.getName();
        if(name.equals("hashCode") && sig.equals("()I"))
            return true;
        if(name.equals("toString") && sig.equals("()Ljava/lang/String;"))
            return true;
        switch(tr.getInternalName()) {
        case "java/util/Arrays":
            return name.equals("hashCode") || name.equals("equals") || name.equals("toString")
                || name.equals("binarySearch") || name.equals("stream")
                || name.equals("spliterator") || name.startsWith("deep")
                || name.startsWith("copyOf") || name.equals("asList");
        case "java/lang/Object":
            return mr.isConstructor();
        case "java/util/Collections":
            return name.equals("min") || name.equals("max") || name.startsWith("unmodifiable") || name.startsWith("synchronized")
                    || name.startsWith("empty");
        }
        if(Types.isCollection(tr)) {
            if(name.equals("contains") && sig.equals("(Ljava/lang/Object;)Z"))
                return true;
            if(name.equals("containsAll") && sig.equals("(Ljava/util/Collection;)Z"))
                return true;
            if(name.equals("isEmpty") && sig.equals("()Z"))
                return true;
            if(name.equals("size") && sig.equals("()I"))
                return true;
            if(Types.isInstance(tr, "java/util/List")) {
                if (name.equals("get") && sig.equals("(I)Ljava/lang/Object;") || name.equals("subList")
                    && sig.equals("(II)Ljava/util/List;"))
                    return true;
            }
            return false;
        }
        if(Types.isInstance(tr, "java/util/Map")) {
            if ((name.equals("containsKey") || name.equals("containsValue"))
                && sig.equals("(Ljava/lang/Object;)Z"))
                return true;
            if (name.equals("get") && sig.equals("(Ljava/lang/Object;)Ljava/lang/Object;"))
                return true;
        }
        return Types.isSideEffectFreeType(tr);
    }

    public static boolean isPure(MethodReference mr) {
        TypeReference tr = mr.getDeclaringType();
        if(Types.isBoxed(tr) || tr.getInternalName().startsWith("java/time/"))
            return true;
        if(tr.getInternalName().equals("java/util/String"))
            return !mr.getName().equals("getChars");
        if(tr.getInternalName().equals("java/lang/Math"))
            return !mr.getName().equals("random");
        if(tr.getInternalName().equals("java/util/Objects"))
            return true;
        if(tr.getInternalName().equals("java/util/Optional"))
            return mr.getName().equals("get") || mr.getName().equals("orElse") || mr.getName().equals("isPresent");
        return false;
    }

    public static boolean isThrower(MethodDefinition md) {
        MethodBody body = md.getBody();
        if(body == null)
            return false;
        for(Instruction inst : body.getInstructions()) {
            if(inst.hasLabel() || inst.getOpCode() == OpCode.RETURN || inst.getOpCode() == OpCode.ARETURN)
                return false;
            if(inst.getOpCode() == OpCode.ATHROW)
                return true;
        }
        // Actually should not go here for valid bytecode
        return false;
    }

    public static boolean isSerializationMethod(MethodDefinition md) {
        return SERIALIZATION_METHODS.contains(md.getName());
    }
}
