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
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import static java.lang.String.format;

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

        repos.add(createDetectorsRepo(CompositeRepository.class, "HuntBugs Detectors", paths));

        return new CompositeRepository(repos);
    }

    static Repository createPluginRepository(HuntBugsPlugin huntBugsPlugin) {
        Class<?> pluginClass = huntBugsPlugin.getClass();
        String pluginName = huntBugsPlugin.name();
        return createDetectorsRepo(pluginClass, pluginName, new HashSet<>());
    }

    static Repository createDetectorsRepo(Class<?> clazz, String pluginName, Set<Path> paths) {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            throw new RuntimeException(format("Initializing plugin '%s' could not get code source for class %s", pluginName, clazz.getName()));
        }

        URL url = codeSource.getLocation();
        try {
            Path path = Paths.get(url.toURI());
            if(paths.add(path)) {
                if(Files.isDirectory(path)) {
                    return new DirRepository(path);
                } else {
                    return new JarRepository(new JarFile(path.toFile()));
                }
            } else {
                return createNullRepository();
            }
        } catch (URISyntaxException | FileSystemNotFoundException | IllegalArgumentException
                | IOException | UnsupportedOperationException e) {
            String errorMessage = format("Error creating detector repository for plugin '%s'", pluginName);
            throw new RuntimeException(errorMessage, e);
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
