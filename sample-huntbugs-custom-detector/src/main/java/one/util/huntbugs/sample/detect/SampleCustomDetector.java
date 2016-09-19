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
package one.util.huntbugs.sample.detect;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Types;

/**
 * @author Mihails Volkovs
 */
@WarningDefinition(category="Demo", name="SampleCustomDetector", maxScore=80)
public class SampleCustomDetector {

    @MethodVisitor
    public void visit(MethodContext mc, MethodDefinition md, TypeDefinition td) {
        TypeReference returnType = md.getReturnType();
        if (Types.isCollection(returnType)) {
            mc.report("SampleCustomDetector", 5);
        }
    }
}
