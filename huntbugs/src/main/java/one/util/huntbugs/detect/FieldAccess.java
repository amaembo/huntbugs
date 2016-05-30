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
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.assembler.metadata.annotations.CustomAnnotation;

import one.util.huntbugs.db.FieldStats;
import one.util.huntbugs.registry.FieldContext;
import one.util.huntbugs.registry.anno.AssertWarning;
import one.util.huntbugs.registry.anno.FieldVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;

/**
 * @author lan
 *
 */
@WarningDefinition(category="RedundantCode", name="UnusedPrivateField", maxScore=45)
@WarningDefinition(category="RedundantCode", name="UnusedPublicField", maxScore=35)
public class FieldAccess {
    @FieldVisitor
    public void visit(FieldContext fc, FieldDefinition fd, FieldStats fs) {
        if(fd.isSynthetic())
            return;
        int flags = fs.getFlags(fd);
        if(Flags.testAny(flags, FieldStats.UNRESOLVED) || hasAnnotation(fd)) {
            return;
        }
        if(!Flags.testAny(flags, FieldStats.ACCESS)) {
            if(fd.isStatic() && fd.isFinal())
                return;
            fc.report(fd.isPublic() || fd.isProtected() ? "UnusedPublicField" : "UnusedPrivateField", 0);
        }
    }

    private static boolean hasAnnotation(FieldDefinition fd) {
        for(CustomAnnotation ca : fd.getAnnotations()) {
            TypeReference annoType = ca.getAnnotationType();
            if(annoType.getPackageName().equals(AssertWarning.class.getPackage().getName()))
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
