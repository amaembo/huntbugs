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

import java.util.List;
import java.util.Objects;
import com.strobel.assembler.metadata.CompositeTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;

/**
 * @author Tagir Valeev
 *
 */
public class CompositeRepository implements Repository {
    private final List<Repository> repos;

    public CompositeRepository(List<Repository> repos) {
        this.repos = Objects.requireNonNull(repos);
    }

    @Override
    public ITypeLoader createTypeLoader() {
        return new CompositeTypeLoader(repos.stream().map(Repository::createTypeLoader).toArray(ITypeLoader[]::new));
    }

    @Override
    public void visit(String rootPackage, RepositoryVisitor visitor) {
        for(Repository repo : repos)
            repo.visit(rootPackage, visitor);
    }

}
