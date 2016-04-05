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

import java.util.List;

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

/**
 * @author lan
 *
 */
public class Nodes {
    public static boolean isOp(Node node, AstCode op) {
        return node instanceof Expression && ((Expression)node).getCode() == op;
    }
    
    public static boolean isInvoke(Node node) {
        if(!(node instanceof Expression))
            return false;
        AstCode code = ((Expression)node).getCode();
        return code == AstCode.InvokeDynamic || code == AstCode.InvokeStatic || code == AstCode.InvokeSpecial
                || code == AstCode.InvokeVirtual || code == AstCode.InvokeInterface;
    }
    
    public static boolean isNullCheck(Node node) {
        if(!isOp(node, AstCode.CmpEq) && !isOp(node, AstCode.CmpNe))
            return false;
        List<Expression> args = ((Expression)node).getArguments();
        return args.get(0).getCode() == AstCode.AConstNull ^ args.get(1).getCode() == AstCode.AConstNull;
    }
    
    public static boolean isBoxing(Node node) {
        if(!isOp(node, AstCode.InvokeStatic))
            return false;
        MethodReference ref = (MethodReference)((Expression)node).getOperand();
        if(!ref.getName().equals("valueOf"))
            return false;
        TypeReference type = ref.getDeclaringType();
        if(type.getInternalName().equals("java/lang/Double") && ref.getSignature().equals("(D)Ljava/lang/Double;"))
            return true;
        if(type.getInternalName().equals("java/lang/Integer") && ref.getSignature().equals("(I)Ljava/lang/Integer;"))
            return true;
        if(type.getInternalName().equals("java/lang/Long") && ref.getSignature().equals("(J)Ljava/lang/Long;"))
            return true;
        if(type.getInternalName().equals("java/lang/Boolean") && ref.getSignature().equals("(Z)Ljava/lang/Boolean;"))
            return true;
        if(type.getInternalName().equals("java/lang/Short") && ref.getSignature().equals("(S)Ljava/lang/Short;"))
            return true;
        if(type.getInternalName().equals("java/lang/Character") && ref.getSignature().equals("(C)Ljava/lang/Character;"))
            return true;
        if(type.getInternalName().equals("java/lang/Float") && ref.getSignature().equals("(F)Ljava/lang/Float;"))
            return true;
        if(type.getInternalName().equals("java/lang/Byte") && ref.getSignature().equals("(B)Ljava/lang/Byte;"))
            return true;
        return false;
    }

    public static boolean isUnboxing(Node node) {
        if(!isOp(node, AstCode.InvokeVirtual))
            return false;
        MethodReference ref = (MethodReference)((Expression)node).getOperand();
        TypeReference type = ref.getDeclaringType();
        if(type.getInternalName().equals("java/lang/Double") && ref.getName().equals("doubleValue"))
            return true;
        if(type.getInternalName().equals("java/lang/Integer") && ref.getName().equals("intValue"))
            return true;
        if(type.getInternalName().equals("java/lang/Long") && ref.getName().equals("longValue"))
            return true;
        if(type.getInternalName().equals("java/lang/Boolean") && ref.getName().equals("booleanValue"))
            return true;
        if(type.getInternalName().equals("java/lang/Short") && ref.getName().equals("shortValue"))
            return true;
        if(type.getInternalName().equals("java/lang/Character") && ref.getName().equals("charValue"))
            return true;
        if(type.getInternalName().equals("java/lang/Float") && ref.getName().equals("floatValue"))
            return true;
        if(type.getInternalName().equals("java/lang/Byte") && ref.getName().equals("byteValue"))
            return true;
        return false;
    }
}
