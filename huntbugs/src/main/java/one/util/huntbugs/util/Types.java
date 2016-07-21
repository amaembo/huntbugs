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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

/**
 * @author Tagir Valeev
 *
 */
public class Types {
    private static final Set<String> SIDE_EFFECT_FREE_TYPES = new HashSet<>(Arrays.asList("java/lang/Integer",
        "java/lang/Long", "java/lang/Short", "java/lang/Double", "java/lang/Byte", "java/lang/Character",
        "java/lang/Boolean", "java/lang/Float", "java/lang/Math"));

    private static final Set<String> BOXED_TYPES = new HashSet<>(Arrays.asList("java/lang/Integer", "java/lang/Long",
        "java/lang/Short", "java/lang/Double", "java/lang/Byte", "java/lang/Character", "java/lang/Boolean",
        "java/lang/Float"));

    private static final Set<String> MUTABLE_TYPES = new HashSet<>(Arrays.asList("java/util/Hashtable",
        "java/util/Vector", "java/util/Date", "java/sql/Date", "java/sql/Timestamp", "java/awt/Point",
        "java/awt/Dimension", "java/awt/Rectangle"));

    public static List<TypeReference> getBaseTypes(TypeReference input) {
        List<TypeReference> result = new ArrayList<>();
        while (true) {
            result.add(input);
            TypeDefinition td = input.resolve();
            if (td == null)
                break;
            input = td.getBaseType();
            if (input == null)
                break;
        }
        Collections.reverse(result);
        return result;
    }

    public static boolean isInstance(TypeReference type, TypeReference wantedType) {
        return isInstance(type, wantedType.getInternalName());
    }

    public static boolean isInstance(TypeReference type, String wantedType) {
        if (type == null)
            return false;
        if (wantedType.equals("java/lang/Object"))
            return true;
        if (type.isArray()) {
            if(!wantedType.startsWith("["))
                return false;
            return isInstance(type.getElementType(), wantedType.substring(1));
        }
        if (type.getInternalName().equals(wantedType))
            return true;
        TypeDefinition td = type.resolve();
        if (td == null)
            return false;
        for (TypeReference iface : td.getExplicitInterfaces()) {
            if (isInstance(iface, wantedType))
                return true;
        }
        TypeReference bt = td.getBaseType();
        if (bt == null)
            return false;
        return isInstance(bt, wantedType);
    }

    public static boolean isRandomClass(TypeReference type) {
        String typeName = type.getInternalName();
        return typeName.equals("java/util/Random") || typeName.equals("java/security/SecureRandom")
            || typeName.equals("java/util/concurrent/ThreadLocalRandom")
            || typeName.equals("java/util/SplittableRandom")
            || typeName.startsWith("cern/jet/random/engine/");
    }

    public static TypeReference getExpressionType(Expression expr) {
        TypeReference exprType = expr.getInferredType();
        if (expr.getOperand() instanceof Variable) {
            Variable var = (Variable) expr.getOperand();
            exprType = var.getType();
            if (var.getOriginalParameter() != null)
                exprType = var.getOriginalParameter().getParameterType();
        }
        return exprType;
    }

    /**
     * @param type
     * @return true if all methods of given type are known not to produce
     *         side-effects
     */
    public static boolean isSideEffectFreeType(TypeReference type) {
        return SIDE_EFFECT_FREE_TYPES.contains(type.getInternalName()) || type.getInternalName().startsWith("java/time/");
    }

    public static boolean samePackage(String internalName1, String internalName2) {
        int pos = internalName1.lastIndexOf('/');
        if (pos == -1)
            return internalName2.indexOf('/') == 1;
        return internalName2.startsWith(internalName1.substring(0, pos + 1));
    }

    /**
     * @param type
     * @return true if type is known to be mutable
     */
    public static boolean isMutable(TypeReference type) {
        if (type.isArray())
            return true;
        return MUTABLE_TYPES.contains(type.getInternalName());
    }

    /**
     * @param type
     * @return true if type is known to be immutable
     */
    public static boolean isImmutable(TypeReference type) {
        if (type == null) 
            return false;
        if(BOXED_TYPES.contains(type.getInternalName()) || isString(type))
            return true;
        return false;
    }
    
    public static boolean isBoxed(TypeReference type) {
        return BOXED_TYPES.contains(type.getInternalName());
    }
    
    public static boolean isObject(TypeReference type) {
        return type.getInternalName().equals("java/lang/Object");
    }

    public static boolean isCollection(TypeReference type) {
        return isInstance(type, "java/util/Collection");
    }
    
    public static boolean isStream(TypeReference type) {
        return isInstance(type, "java/util/stream/Stream");
    }
    
    public static boolean isString(TypeReference type) {
        return type.getInternalName().equals("java/lang/String");
    }

    public static boolean isBaseStream(TypeReference type) {
        return isInstance(type, "java/util/stream/BaseStream");
    }
    
    public static boolean is(TypeReference type, Class<?> clazz) {
        return type != null && type.getFullName().equals(clazz.getName());
    }

    public static boolean is(TypeReference type, String internalName) {
        return type != null && type.getInternalName().equals(internalName);
    }
    
    /**
     * @param type type to check
     * @return true if all superclasses and superinterfaces could be loaded
     */
    public static boolean hasCompleteHierarchy(TypeDefinition type) {
        if(type == null)
            return false;
        TypeReference base = type.getBaseType();
        if(base != null && !hasCompleteHierarchy(base.resolve()))
            return false;
        for(TypeReference tr : type.getExplicitInterfaces()) {
            if(!hasCompleteHierarchy(tr.resolve()))
                return false;
        }
        return true;
    }
}
