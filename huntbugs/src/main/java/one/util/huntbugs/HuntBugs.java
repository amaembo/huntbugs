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
package one.util.huntbugs;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.repo.CompositeRepository;
import one.util.huntbugs.repo.DirRepository;
import one.util.huntbugs.repo.JarRepository;
import one.util.huntbugs.repo.Repository;

/**
 * @author lan
 *
 */
public class HuntBugs {
    private boolean listDetectors = false;
    private boolean listOptions = false;
    private final AnalysisOptions options = new AnalysisOptions();
    private Repository repo;
    
    private void parseCommandLine(String[] args) {
        List<Repository> repos = new ArrayList<>();
        for(String arg : args) {
            if(arg.equals("-lw")) {
                listDetectors = true;
            } else if(arg.equals("-lv")) {
                listOptions = true;
            } else if(arg.startsWith("-D")) {
                int pos = arg.indexOf('=');
                if(pos == 0) {
                    throw new IllegalArgumentException("Illegal option: "+arg+" (expected -Dname=value)");
                }
                String name = arg.substring(2, pos).trim();
                String value = arg.substring(pos+1).trim();
                options.set(name, value);
            } else {
                Path root = Paths.get(arg);
                try {
                    repos.add(Files.isDirectory(root) ? new DirRepository(root) : new JarRepository(new JarFile(root
                            .toFile())));
                } catch (IOException e) {
                    throw new IllegalArgumentException("Cannot open JAR file "+arg);
                }
            }
        }
        if(!repos.isEmpty()) {
            repo = new CompositeRepository(repos);
        }
    }
    
    private int run(String[] args) {
        if(args.length == 0) {
            System.out.println("Welcome to HuntBugs");
            System.out.println("Please specify at least one option or at least one directory/jar to analyze");
            System.out.println("Options are:");
            System.out.println("    -lw          -- list all warning types");
            System.out.println("    -lv          -- list all variables");
            System.out.println("    -Dname=value -- set given variable");
            return -1;
        }
        try {
            parseCommandLine(args);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            return -3;
        }
        if(listDetectors) {
            System.out.println("List of warning types:");
            Context ctx = new Context(Repository.createNullRepository(), options);
            ctx.reportWarningTypes(System.out);
            return 0;
        }
        if(listOptions) {
            System.out.println("List of variables:");
            options.report(System.out);
            return 0;
        }
        if(repo == null) {
            System.err.println("No repositories specified");
            return -2;
        }
        long start = System.nanoTime();
        Context ctx = new Context(repo, options);
        ctx.addListener((stage, className) -> {
            if(className == null)
                return true;
            String name = className.length() > 50 ? "..."+className.substring(className.length()-47) : className;
            System.out.printf("\r%70s\r[%d/%d] %s", "", ctx.getClassesCount(), ctx.getTotalClasses(), name);
            return true;
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ctx.reportErrors(new PrintStream("huntbugs.errors.txt"));
                ctx.reportWarnings(new PrintStream("huntbugs.warnings.txt"));
                ctx.reportStats(new PrintStream("huntbugs.stats.txt"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            long end = System.nanoTime();
            Duration dur = Duration.ofNanos(end - start);
            System.out.printf("\r%70s\r\n", "");
            System.out.println("Analyzed "+ctx.getClassesCount()+" of "+ctx.getTotalClasses()+" classes");
            ctx.reportStats(System.out);
            System.out.println("Analyzis time "+dur.toMinutes()+"m"+dur.getSeconds()%60+"s");
        }));
        ctx.analyzePackage("");
        return 0;
    }
    
    public static void main(String[] args) {
        System.exit(new HuntBugs().run(args));
    }
}
