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

import com.strobel.assembler.ir.Instruction;
import com.strobel.assembler.ir.OpCode;
import com.strobel.assembler.metadata.MethodBody;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;

import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author Tagir Valeev
 *
 */
public class Methods {
    public static boolean isEqualsMethod(MethodReference mr) {
        return mr.getName().equals("equals") && mr.getSignature().equals("(Ljava/lang/Object;)Z");
    }

    public static boolean isGetClass(MethodReference mr) {
        return mr.getName().equals("getClass") && mr.getErasedSignature().equals("()Ljava/lang/Class;");
    }
    
    public static MethodDefinition findSuperMethod(MethodReference md) {
        TypeDefinition td = md.getDeclaringType().resolve();
        return td == null ? null : findSuperMethod(td, new MemberInfo(md));
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

    private static MethodDefinition findMethod(TypeDefinition td, MemberInfo mi) {
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
        if(Types.isObject(tr) && mr.isConstructor())
            return true;
        String sig = mr.getErasedSignature();
        String name = mr.getName();
        if(name.equals("hashCode") && sig.equals("()I"))
            return true;
        if(name.equals("toString") && sig.equals("()Ljava/lang/String;"))
            return true;
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
                if(name.equals("get") && sig.equals("(I)Ljava/lang/Object;"))
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
}
