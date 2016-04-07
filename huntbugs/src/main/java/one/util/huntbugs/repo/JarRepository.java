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

import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.JarTypeLoader;

/**
 * @author lan
 *
 */
public class JarRepository implements Repository {
    private final JarFile file;

    public JarRepository(JarFile file) {
        this.file = file;
    }

    @Override
    public ITypeLoader createTypeLoader() {
        return new JarTypeLoader(file);
    }

    @Override
    public void visit(String rootPackage, RepositoryVisitor visitor) {
        Enumeration<JarEntry> entries = file.entries();
        String skipPrefix = null;
        while(entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if(skipPrefix != null) {
                if(entry.getName().startsWith(skipPrefix))
                    continue;
                skipPrefix = null;
            }
            if(entry.isDirectory()) {
                if(!visitor.visitPackage(entry.getName())) {
                    skipPrefix = entry.getName()+"/";
                }
            } else {
                if(entry.getName().endsWith(".class")) {
                    String className = entry.getName();
                    className = className.substring(0, className.length()-".class".length());
                    if(!className.contains("$"))
                        visitor.visitClass(className);
                }
            }
        }
    }

}
