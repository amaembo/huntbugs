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

import com.strobel.assembler.metadata.ITypeLoader;
import one.util.huntbugs.spi.HuntBugsPlugin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * @author Tagir Valeev
 *
 */
public interface Repository {
    ITypeLoader createTypeLoader();

    void visit(String rootPackage, RepositoryVisitor visitor);

    static Repository createSelfRepository() {
        List<Repository> repos = new ArrayList<>();
        Set<Path> paths = new HashSet<>();
        try {
            Enumeration<URL> resources = CompositeRepository.class.getClassLoader().getResources(".");
            while (resources.hasMoreElements()) {
                try {
                    Path path = Paths.get(resources.nextElement().toURI());
                    if(paths.add(path)) {
                        repos.add(new DirRepository(path));
                    }
                } catch (URISyntaxException e) {
                    // ignore
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // adding HuntBugs built-in detectors
        addDetectorsForClass(CompositeRepository.class, repos, paths);

        // adding HuntBugs 3-rd party detectors if any
        for (HuntBugsPlugin huntBugsPlugin : ServiceLoader.load(HuntBugsPlugin.class)) {
            addDetectorsForClass(huntBugsPlugin.getClass(), repos, paths);
        }

        return new CompositeRepository(repos);
    }

    static void addDetectorsForClass(Class<?> clazz, List<Repository> repos, Set<Path> paths) {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        URL url = codeSource == null ? null : codeSource.getLocation();
        if(url != null) {
            try {
                Path path = Paths.get(url.toURI());
                if(paths.add(path)) {
                    if(Files.isDirectory(path))
                        repos.add(new DirRepository(path));
                    else if(Files.isRegularFile(path))
                        repos.add(new JarRepository(new JarFile(path.toFile())));
                }
            } catch (URISyntaxException | FileSystemNotFoundException | IllegalArgumentException 
                    | IOException | UnsupportedOperationException e) {
                // ignore
            }
        }
    }
    
    static Repository createNullRepository() {
        return new Repository() {
            @Override
            public void visit(String rootPackage, RepositoryVisitor visitor) {
                // nothing to do
            }
            
            @Override
            public ITypeLoader createTypeLoader() {
                return (internalName, buffer) -> false;
            }
        };
    }
}
