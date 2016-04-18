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
package one.util.huntbugs.repo;

import com.strobel.assembler.metadata.ITypeLoader;

/**
 * Repository for auxiliary classes which should not be visited normally but should be accessible
 * 
 * @author lan
 *
 */
public class AuxRepository implements Repository {
    private final ITypeLoader loader;

    public AuxRepository(ITypeLoader loader) {
        this.loader = loader;
    }

    @Override
    public ITypeLoader createTypeLoader() {
        return loader;
    }

    @Override
    public void visit(String rootPackage, RepositoryVisitor visitor) {
    }
}
