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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.CatchBlock;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category="BadPractice", name="CatchIllegalMonitorStateException", maxScore=50)
@WarningDefinition(category="BadPractice", name="CatchConcurrentModificationException", maxScore=50)
public class DubiousCatch {
    private static final Map<String, String> EXCEPTION_TO_WARNING = new HashMap<>();
    
    static {
        EXCEPTION_TO_WARNING.put("java/lang/IllegalMonitorStateException", "CatchIllegalMonitorStateException");
        EXCEPTION_TO_WARNING.put("java/util/ConcurrentModificationException", "CatchConcurrentModificationException");
    }
    
    @AstVisitor
    public void visit(Node node, MethodContext mc) {
        if(node instanceof CatchBlock) {
            CatchBlock block = (CatchBlock)node;
            List<TypeReference> exceptions = block.getCaughtTypes();
            if(exceptions.isEmpty() && block.getExceptionType() != null)
                exceptions = Collections.singletonList(block.getExceptionType());
            for(TypeReference type : exceptions) {
                String warningType = EXCEPTION_TO_WARNING.get(type.getInternalName());
                if(warningType != null) {
                    mc.report(warningType, 0, node, WarningAnnotation.forType("EXCEPTION", type));
                }
            }
        }
    }
}
