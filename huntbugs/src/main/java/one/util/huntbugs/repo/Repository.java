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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;

import com.strobel.assembler.metadata.ITypeLoader;

/**
 * @author lan
 *
 */
public interface Repository {
    ITypeLoader createTypeLoader();

    void visit(String rootPackage, RepositoryVisitor visitor);

    public static Repository createSelfRepository() {
        List<Repository> repos = new ArrayList<>();
        try {
            Enumeration<URL> resources = CompositeRepository.class.getClassLoader().getResources(".");
            while (resources.hasMoreElements()) {
                try {
                    repos.add(new DirRepository(new File(resources.nextElement().toURI()).toPath()));
                } catch (URISyntaxException e) {
                    // ignore
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (repos.isEmpty()) {
            try {
                repos.add(new JarRepository(new JarFile(CompositeRepository.class.getProtectionDomain().getCodeSource()
                        .getLocation().toURI().getPath())));
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (URISyntaxException e) {
                // ignore
            }
        }
        CompositeRepository repo = new CompositeRepository(repos);
        return repo;
    }
    
    public static Repository createNullRepository() {
        return new Repository() {
            @Override
            public void visit(String rootPackage, RepositoryVisitor visitor) {
            }
            
            @Override
            public ITypeLoader createTypeLoader() {
                return (internalName, buffer) -> false;
            }
        };
    }
}
