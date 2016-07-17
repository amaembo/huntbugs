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

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;

import one.util.huntbugs.registry.ClassContext;
import one.util.huntbugs.registry.FieldContext;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.FieldVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Role.TypeRole;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category="Serialization", name="ComparatorIsNotSerializable", maxScore=45)
@WarningDefinition(category="Serialization", name="SerialVersionUidNotFinal", maxScore=50)
@WarningDefinition(category="Serialization", name="SerialVersionUidNotStatic", maxScore=50)
@WarningDefinition(category="Serialization", name="SerialVersionUidNotLong", maxScore=40)
@WarningDefinition(category="Serialization", name="SerializationMethodMustBePrivate", maxScore=60)
@WarningDefinition(category="Serialization", name="ReadResolveMustReturnObject", maxScore=60)
@WarningDefinition(category="Serialization", name="ReadResolveIsStatic", maxScore=60)
public class SerializationIdiom {
    private static final TypeRole SHOULD_IMPLEMENT = TypeRole.forName("SHOULD_IMPLEMENT");
    
    boolean isSerializable;
    
    @ClassVisitor
    public void visitClass(TypeDefinition td, ClassContext cc) {
        isSerializable = Types.isInstance(td, "java/io/Serializable");
        if(Types.isInstance(td, "java/util/Comparator") && !td.isAnonymous() && !td.isLocalClass()
                && !isSerializable) {
            int priority = 0;
            for(FieldDefinition fd : td.getDeclaredFields()) {
                TypeReference fieldType = fd.getFieldType();
                while(fieldType.isArray())
                    fieldType = fieldType.getElementType();
                if(fieldType.isPrimitive())
                    continue;
                if(Types.isInstance(fieldType, "java/io/Serializable")) {
                    priority+=10;
                    if(priority > 20)
                        break;
                }
            }
            cc.report("ComparatorIsNotSerializable", priority, SHOULD_IMPLEMENT.create("java/io/Serializable"));
        }
    }
    
    @MethodVisitor
    public void visitMethod(MethodDefinition md, MethodContext mc) {
        if(isSerializable) {
            switch(md.getName()) {
            case "readResolve":
                if(md.getSignature().startsWith("()")) {
                    if(!md.getSignature().equals("()Ljava/lang/Object;")) {
                        mc.report("ReadResolveMustReturnObject", 0);
                    } else if(md.isStatic()) {
                        mc.report("ReadResolveIsStatic", 0);
                    }
                }
                break;
            case "readObject":
                if(md.getSignature().equals("(Ljava/io/ObjectInputStream;)V") && !md.isPrivate())
                    mc.report("SerializationMethodMustBePrivate", 0);
                break;
            case "readObjectNoData":
                if(md.getSignature().equals("()V") && !md.isPrivate())
                    mc.report("SerializationMethodMustBePrivate", 0);
                break;
            case "writeObject":
                if(md.getSignature().equals("(Ljava/io/ObjectOutputStream;)V") && !md.isPrivate())
                    mc.report("SerializationMethodMustBePrivate", 0);
                break;
            }
        }
    }
    
    @FieldVisitor
    public void visitField(FieldDefinition fd, FieldContext fc) {
        if(fd.getName().equals("serialVersionUID")) {
            if(!fd.isFinal()) {
                fc.report("SerialVersionUidNotFinal", 0);
            }
            if(!fd.isStatic()) {
                fc.report("SerialVersionUidNotStatic", 0);
            }
            if(fd.getFieldType().getSimpleType() == JvmType.Integer) {
                fc.report("SerialVersionUidNotLong", 0);
            }
        }
    }
}
