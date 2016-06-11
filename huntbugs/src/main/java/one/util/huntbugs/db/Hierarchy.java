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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.IMetadataResolver;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;

import one.util.huntbugs.registry.AbstractTypeDatabase;
import one.util.huntbugs.registry.anno.TypeDatabase;
import one.util.huntbugs.registry.anno.TypeDatabaseItem;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author Tagir Valeev
 *
 */
@TypeDatabase
public class Hierarchy extends AbstractTypeDatabase<Hierarchy.TypeHierarchy> {
    public Hierarchy() {
        super(TypeHierarchy::new);
    }

    @Override
    protected void visitType(TypeDefinition td) {
        TypeHierarchy th = getOrCreate(td);
        th.flags = td.getFlags();
        link(th, td.getBaseType());
        for (TypeReference id : td.getExplicitInterfaces())
            link(th, id);
    }
    
    public boolean isOverridden(MethodDefinition md) {
        if(md.isStatic() || md.isFinal() || md.getDeclaringType().isFinal())
            return false;
        IMetadataResolver resolver = md.getDeclaringType().getResolver();
        MemberInfo mi = new MemberInfo(md);
        TypeHierarchy th = get(md.getDeclaringType());
        return th != null && th.isOverridden(resolver, mi);
    }
    
    private void link(TypeHierarchy th, TypeReference superType) {
        if (superType == null || Types.isObject(superType))
            return;
        TypeHierarchy superTh = getOrCreate(superType);
        th.superClasses.add(superTh);
        superTh.subClasses.add(th);
    }

    @TypeDatabaseItem(parentDatabase = Hierarchy.class)
    public static class TypeHierarchy {
        final String internalName;
        long flags = Flags.LOAD_BODY_FAILED;
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
        
        boolean isOverridden(IMetadataResolver resolver, MemberInfo mi) {
            for(TypeHierarchy th : subClasses) {
                TypeReference str = resolver.lookupType(th.internalName);
                if(str == null)
                    continue;
                TypeDefinition std = resolver.resolve(str);
                if(std == null)
                    continue;
                MethodDefinition res = Methods.findMethod(std, mi);
                if(res != null)
                    return true;
            }
            for(TypeHierarchy th : subClasses) {
                if(th.isOverridden(resolver, mi))
                    return true;
            }
            return false;
        }
        
        public boolean isResolved() {
            return !hasFlag(Flags.LOAD_BODY_FAILED);
        }
        
        public boolean hasFlag(long flag) {
            return Flags.testAny(flags, flag);
        }
        
        @Override
        public String toString() {
            return internalName;
        }

        public boolean hasSubClasses() {
            return !subClasses.isEmpty();
        }
        
        public boolean hasSubClassesOutOfPackage() {
            return !subClasses.stream().allMatch(sc -> Types.samePackage(internalName, sc.getInternalName()));
        }
    }
}
