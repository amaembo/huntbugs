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
package one.util.huntbugs.repo;

import java.util.function.Predicate;

import com.strobel.assembler.metadata.ITypeLoader;

/**
 * Repository which filters visited classes with given predicate
 * (though all classes still could be loaded).
 * 
 * @author Tagir Valeev
 */
public class FilteredRepository implements Repository {
    private final Predicate<String> classFilter;
    private final Repository repository;

    /**
     * @param repository parent repository
     * @param classFilter predicate which accepts internal class name (like "a/b/c/d") and returns true if it should be visited
     */
    public FilteredRepository(Repository repository, Predicate<String> classFilter) {
        this.repository = repository;
        this.classFilter = classFilter;
    }

    @Override
    public ITypeLoader createTypeLoader() {
        return repository.createTypeLoader();
    }

    @Override
    public void visit(String rootPackage, RepositoryVisitor visitor) {
        repository.visit(rootPackage, new RepositoryVisitor() {
            
            @Override
            public boolean visitPackage(String packageName) {
                return visitor.visitPackage(packageName);
            }
            
            @Override
            public void visitClass(String className) {
                if(classFilter.test(className))
                    visitor.visitClass(className);
            }
        });
    }
}
