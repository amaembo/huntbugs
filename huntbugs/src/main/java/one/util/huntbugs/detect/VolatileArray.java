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
import com.strobel.assembler.metadata.JvmType;
import one.util.huntbugs.db.FieldStats;
import one.util.huntbugs.registry.FieldContext;
import one.util.huntbugs.registry.anno.FieldVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.warning.Roles;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Multithreading", name="VolatileArray", maxScore=50)
public class VolatileArray {
    @FieldVisitor
    public void checkField(FieldDefinition fd, FieldStats fs, FieldContext fc) {
        if(fd.getFieldType().isArray() && Flags.testAny(fd.getFlags(), Flags.VOLATILE)) {
            int priority = 0;
            int flags = fs.getFlags(fd);
            if(!Flags.testAny(flags, FieldStats.UNRESOLVED)) {
                if(!Flags.testAny(flags, FieldStats.WRITE_NONNULL))
                    return; // will be reported as unwritten
                if(Flags.testAny(flags, FieldStats.WRITE_CLASS | FieldStats.WRITE_PACKAGE | FieldStats.WRITE_OUTSIDE)) {
                    priority += 20; 
                }
            }
            fc.report("VolatileArray", priority, Roles.REPLACEMENT_CLASS.create(getAtomicArrayReplacement(fd
                    .getFieldType().getElementType().getSimpleType())));
        }
    }

    private String getAtomicArrayReplacement(JvmType simpleType) {
        switch(simpleType) {
        case Boolean:
        case Byte:
        case Character:
        case Integer:
            return "java/util/concurrent/atomic/AtomicIntegerArray";
        case Long:
            return "java/util/concurrent/atomic/AtomicLongArray";
        default:
            return "java/util/concurrent/atomic/AtomicReferenceArray";
        }
    }
}
