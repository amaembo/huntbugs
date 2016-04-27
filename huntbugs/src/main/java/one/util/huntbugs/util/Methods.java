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
package one.util.huntbugs.util;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;

/**
 * @author lan
 *
 */
public class Methods {
    public static boolean isEqualsMethod(MethodReference mr) {
        return mr.getName().equals("equals") && mr.getSignature().equals("(Ljava/lang/Object;)Z");
    }

    public static boolean isGetClass(MethodReference mr) {
        return mr.getName().equals("getClass") && mr.getErasedSignature().equals("()Ljava/lang/Class;");
    }
    
    public static MethodDefinition findSuperMethod(MethodDefinition md) {
        return findSuperMethod(md.getDeclaringType(), md);
    }
    
    private static MethodDefinition findSuperMethod(TypeDefinition type, MethodDefinition md) {
        TypeReference superType = type.getBaseType();
        if(superType != null) {
            TypeDefinition superTd = superType.resolve();
            if(superTd != null) {
                MethodDefinition result = findMethod(superTd, md);
                if(result != null)
                    return result;
                result = findSuperMethod(superTd, md);
                if(result != null)
                    return result;
            }
        }
        for(TypeReference iface : type.getExplicitInterfaces()) {
            TypeDefinition ifaceTd = iface.resolve();
            if(ifaceTd != null) {
                MethodDefinition result = findMethod(ifaceTd, md);
                if(result != null)
                    return result;
                result = findSuperMethod(ifaceTd, md);
                if(result != null)
                    return result;
            }
        }
        return null; 
    }

    private static MethodDefinition findMethod(TypeDefinition td, MethodDefinition md) {
        if(td == null)
            return null;
        for(MethodDefinition decl : td.getDeclaredMethods()) {
            if(decl.getName().equals(md.getName())) {
                String sig1 = decl.getErasedSignature();
                String sig2 = md.getErasedSignature();
                if(sig1 == sig2)
                    return decl;
                if(sig1.substring(0, sig1.indexOf(')')).equals(sig2.substring(0, sig2.indexOf(')'))))
                    return decl;
            }
        }
        return null;
    }
}
