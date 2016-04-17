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
package one.util.huntbugs.detect;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;

import one.util.huntbugs.registry.ClassContext;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Serialization", name="ComparatorIsNotSerializable", maxScore=50)
public class SerializationIdiom {
    @ClassVisitor
    public void visitClass(TypeDefinition td, ClassContext cc) {
        if(Types.isInstance(td, "java/util/Comparator") && !td.isAnonymous() && !td.isLocalClass()
                && !Types.isInstance(td, "java/io/Serializable")) {
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
            cc.report("ComparatorIsNotSerializable", priority, new WarningAnnotation<>("SHOULD_IMPLEMENT",
                    new WarningAnnotation.TypeInfo("java/io/Serializable")));
        }
    }
}
