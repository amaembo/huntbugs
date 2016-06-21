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
import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.annotations.CustomAnnotation;

import one.util.huntbugs.registry.FieldContext;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.FieldVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;

/**
 * @author lan
 *
 */
@WarningDefinition(category="BadPractice", name="NonFinalFieldInImmutableClass", maxScore=60)
public class JcipProblems {
    @ClassVisitor
    public boolean checkClass(TypeDefinition td) {
        for(CustomAnnotation ca : td.getAnnotations()) {
            String name = ca.getAnnotationType().getInternalName();
            if(name.equals("net/jcip/annotations/Immutable") || name.equals("javax/annotation/concurrent/Immutable"))
                return true;
        }
        return false;
    }
    
    @FieldVisitor
    public void visitField(FieldDefinition fd, FieldContext fc) {
        if(!Flags.testAny(fd.getFlags(), Flags.VOLATILE | Flags.FINAL | Flags.TRANSIENT | Flags.STATIC)) {
            fc.report("NonFinalFieldInImmutableClass", 0);
        }
    }
}
