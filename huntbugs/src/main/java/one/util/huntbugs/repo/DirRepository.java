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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import com.strobel.assembler.metadata.ClasspathTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;

/**
 * @author lan
 *
 */
public class DirRepository implements Repository {
    private final Path root;

    public DirRepository(Path root) {
        this.root = root;
    }

    @Override
    public ITypeLoader createTypeLoader() {
        return new ClasspathTypeLoader(root.toString());
    }

    @Override
    public void visit(String rootPackage, RepositoryVisitor visitor) {
        Path path = root.resolve(rootPackage);
        if(!Files.isDirectory(path))
            return;
        try {
            Files.walkFileTree(path, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String pkg = root.relativize(dir).toString().replace('\\', '/');
                    if(visitor.visitPackage(pkg))
                        return FileVisitResult.CONTINUE;
                    return FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if(file.getFileName().toString().endsWith(".class")) {
                        String baseName = root.relativize(file).toString();
                        if(!baseName.contains("$")) {
                            baseName = baseName.substring(0, baseName.length() - ".class".length()).replace('\\', '/');
                            visitor.visitClass(baseName);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
