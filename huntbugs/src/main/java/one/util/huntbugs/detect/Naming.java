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

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Types;

/**
 * @author lan
 *
 */
@WarningDefinition(category="CodeStyle", name="BadNameOfMethod", maxScore=40)
public class Naming {
    int count = 0;
    
    @MethodVisitor
    public void visitMethod(MethodDefinition md, TypeDefinition td, MethodContext mc) {
        if(badMethodName(md.getName()) && !Types.isInstance(td, "org/eclipse/osgi/util/NLS")) {
            if(++count > 3)
                return;
            int priority = 0;
            if(!td.isPublic())
                priority += 20;
            else {
                if(td.isFinal())
                    priority += 3;
                if(md.isProtected())
                    priority += 3;
                else if(md.isPackagePrivate())
                    priority += 6;
                else if(md.isPrivate())
                    priority += 10;
            }
            mc.report("BadNameOfMethod", priority);
        }
    }
    
    private boolean badMethodName(String mName) {
        return mName.length() >= 2 && Character.isLetter(mName.charAt(0)) && !Character.isLowerCase(mName.charAt(0))
            && Character.isLetter(mName.charAt(1)) && Character.isLowerCase(mName.charAt(1))
            && mName.indexOf('_') == -1;
    }

}
