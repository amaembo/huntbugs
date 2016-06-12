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
package one.util.huntbugs.db;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;

import one.util.huntbugs.registry.AbstractTypeDatabase;
import one.util.huntbugs.registry.anno.TypeDatabase;

/**
 * @author shustkost
 *
 */
@TypeDatabase
public class Mutability extends AbstractTypeDatabase<Boolean> {
    public Mutability() {
        super(type -> Boolean.TRUE);
    }

    @Override
    protected void visitType(TypeDefinition td) {
        if(!td.isPublic())
            return;
        for(FieldDefinition fd : td.getDeclaredFields()) {
            if(!fd.isStatic() && !fd.isFinal() && fd.isPublic()) {
                getOrCreate(td);
                return;
            }
        }
    }
    
    public boolean isKnownMutable(TypeReference tr) {
        return get(tr.getInternalName()) != null;
    }
}
