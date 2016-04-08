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
package one.util.huntbugs.db;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import one.util.huntbugs.registry.AbstractTypeDatabase;
import one.util.huntbugs.registry.anno.TypeDatabase;
import one.util.huntbugs.registry.anno.TypeDatabaseItem;

/**
 * @author lan
 *
 */
@TypeDatabase
public class Hierarchy extends AbstractTypeDatabase<Hierarchy.TypeHierarchy>{

    public Hierarchy() {
        super(TypeHierarchy::new);
    }

    @TypeDatabaseItem(parentDatabase=Hierarchy.class)
    public static class TypeHierarchy {
        final String internalName;
        int flags;
        final Set<TypeHierarchy> superClasses = new HashSet<>();
        final Set<TypeHierarchy> subClasses = new HashSet<>();
        
        public TypeHierarchy(String name) {
            this.internalName = name;
        }
        
        public String getInternalName() {
            return internalName;
        }

        public Set<TypeHierarchy> getSuperClasses() {
            return Collections.unmodifiableSet(superClasses);
        }

        public Set<TypeHierarchy> getSubClasses() {
            return Collections.unmodifiableSet(subClasses);
        }
    }
}
