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

import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;

import one.util.huntbugs.registry.ClassContext;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "CodeStyle", name = "RedundantInterface", maxScore = 20)
public class RedundantInterfaces {
    @ClassVisitor
    public void visit(TypeDefinition td, ClassContext cc) {
        TypeDefinition baseType = td.getBaseType().resolve();
        if(baseType == null || Types.isObject(baseType))
            return;
        for(TypeReference tr : td.getExplicitInterfaces()) {
            if(tr.getInternalName().equals("java/io/Serializable")) {
                continue;
            }
            if(Types.isInstance(baseType, tr)) {
                cc.report("RedundantInterface", td.isPublic() ? 0 : 10, WarningAnnotation.forType("INTERFACE", tr));
            }
        }
    }
}
