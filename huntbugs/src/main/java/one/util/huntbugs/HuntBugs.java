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
package one.util.huntbugs;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.logging.LogManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.strobel.assembler.metadata.ClasspathTypeLoader;
import com.strobel.assembler.metadata.CompositeTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.JarTypeLoader;

import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.HuntBugsResult;
import one.util.huntbugs.input.XmlReportReader;
import one.util.huntbugs.output.Reports;
import one.util.huntbugs.repo.AuxRepository;
import one.util.huntbugs.repo.CompositeRepository;
import one.util.huntbugs.repo.DirRepository;
import one.util.huntbugs.repo.JarRepository;
import one.util.huntbugs.repo.Repository;
import one.util.huntbugs.warning.rule.CategoryRule;
import one.util.huntbugs.warning.rule.CompositeRule;
import one.util.huntbugs.warning.rule.RegexRule;
import one.util.huntbugs.warning.rule.Rule;

/**
 * @author Tagir Valeev
 *
 */
public class HuntBugs {
    private boolean listDetectors = false;
    private boolean listVariables = false;
    private boolean listDatabases = false;
    private boolean listMessages = false;
    private final AnalysisOptions options = new AnalysisOptions();
    private Repository repo;
    private Path compareTo;

    private void parseCommandLine(String[] args) {
        List<Repository> repos = new ArrayList<>();
        List<ITypeLoader> deps = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("-lw")) {
                listDetectors = true;
            } else if (arg.equals("-lv")) {
                listVariables = true;
            } else if (arg.equals("-ldb")) {
                listDatabases = true;
            } else if (arg.equals("-lm")) {
                listMessages = true;
            } else if (arg.startsWith("-C")) {
                compareTo = Paths.get(arg.substring(2));
            } else if (arg.startsWith("-D")) {
                int pos = arg.indexOf('=');
                if (pos < 0) {
                    throw new IllegalArgumentException("Illegal option: " + arg + " (expected -Dname=value)");
                }
                String name = arg.substring(2, pos).trim();
                String value = arg.substring(pos + 1).trim();
                options.set(name, value);
            } else if (arg.startsWith("-R")) {
                int colonPos = arg.indexOf(':');
                int equalPos = arg.lastIndexOf('=');
                if (colonPos < 0 || equalPos < 0 || equalPos < colonPos) {
                    throw new IllegalArgumentException("Illegal option: " + arg
                        + " (expected -Rruletype:rule=adjustment)");
                }
                String ruleType = arg.substring(2, colonPos);
                String ruleArg = arg.substring(colonPos + 1, equalPos);
                String adjustmentString = arg.substring(equalPos + 1);
                int adjustment;
                if (adjustmentString.equals("disable")) {
                    adjustment = -100;
                } else {
                    try {
                        adjustment = Integer.parseInt(adjustmentString);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Illegal option: " + arg
                            + ": adjustment must be either number from -100 to +100 or 'disable' word");
                    }
                }
                switch (ruleType) {
                case "category":
                    rules.add(new CategoryRule(ruleArg, adjustment));
                    break;
                case "pattern":
                    rules.add(new RegexRule(ruleArg, adjustment));
                    break;
                default:
                    throw new IllegalArgumentException("Illegal option: " + arg
                        + ": ruletype must be either 'category' or 'pattern'");
                }
            } else if(arg.startsWith("-A")){
                try {
                    glob(arg.substring(2)).map(this::createTypeLoader).forEach(deps::add);
                } catch (IOException e) {
                    throw new IllegalArgumentException("Cannot open JAR file " + arg);
                }
            } else {
                try {
                    glob(arg).map(this::createRepository).forEach(repos::add);
                } catch (IOException e) {
                    throw new IllegalArgumentException("Cannot open JAR file " + arg);
                }
            }
        }
        if (!deps.isEmpty()) {
            repos.add(new AuxRepository(new CompositeTypeLoader(deps.toArray(new ITypeLoader[0]))));
        }
        if (!repos.isEmpty()) {
            repo = new CompositeRepository(repos);
        }
        if (rules.size() == 1)
            options.setRule(rules.get(0));
        else if (rules.size() > 1)
            options.setRule(new CompositeRule(rules));
    }

    private Repository createRepository(Path path) {
        try {
            return Files.isDirectory(path) ? new DirRepository(path) : new JarRepository(new JarFile(path.toFile()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ITypeLoader createTypeLoader(Path path) {
        try {
            return Files.isDirectory(path) ? new ClasspathTypeLoader(path.toString()) : new JarTypeLoader(new JarFile(path.toFile()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    private int run(String[] args) {
        LogManager.getLogManager().reset();
        if (args.length == 0) {
            System.out.println("Welcome to HuntBugs");
            System.out.println("Please specify at least one option or at least one directory/jar to analyze");
            System.out.println("Options are:");
            System.out.println("    -lw                        -- list all warning types");
            System.out.println("    -lv                        -- list all variables");
            System.out.println("    -ldb                       -- list all databases");
            System.out.println("    -lm                        -- list warning titles");
            System.out.println("    -ColdResult.xml            -- output difference with old result");
            System.out.println("    -Apath                     -- dependency path");
            System.out.println("    -Dname=value               -- set given variable");
            System.out.println("    -Rruletype:rule=adjustment -- adjust score for warnings");
            return -1;
        }
        try {
            parseCommandLine(args);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            return -3;
        }
        boolean list = false;
        Context ctx = new Context(repo, options);
        if (listDetectors) {
            System.out.println("List of warning types:");
            ctx.reportWarningTypes(System.out);
            list = true;
        }
        if (listVariables) {
            System.out.println("List of variables:");
            options.report(System.out);
            list = true;
        }
        if (listDatabases) {
            System.out.println("List of databases:");
            ctx.reportDatabases(System.out);
            list = true;
        }
        if (listMessages) {
            System.out.println("List of warning titles:");
            ctx.reportTitles(System.out);
            list = true;
        }
        if (repo == null) {
            if (list) {
                ctx.reportStats(System.out);
                return 0;
            }
            System.err.println("No repositories specified");
            return -2;
        }
        long start = System.nanoTime();
        ctx.addListener((stage, className, count, total) -> {
            if (count == 0)
                System.out.printf("\r%70s\r%s...%n", "", stage);
            else {
                if (className == null)
                    className = "";
                String name = className.length() > 50 ? "..." + className.substring(className.length() - 47) : className;
                System.out.printf("\r%70s\r[%d/%d] %s", "", count, total, name);
            }
            return true;
        });
        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> {
                try {
                    ctx.reportErrors(new PrintStream("huntbugs.errors.txt", "UTF-8"));
                    ctx.reportStats(new PrintStream("huntbugs.stats.txt", "UTF-8"));
                    HuntBugsResult result = ctx;
                    if(compareTo != null) {
                        try {
                            result = Reports.diff(XmlReportReader.read(ctx, compareTo), ctx);
                        } catch (Exception e) {
                            System.out.println("Warning: unable to read old result file "+compareTo+": "+e);
                            System.out.println("Saving non-diff result");
                        }
                    }
                    Reports.write(Paths.get("huntbugs.warnings.xml"), Paths.get("huntbugs.warnings.html"), result);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                long end = System.nanoTime();
                Duration dur = Duration.ofNanos(end - start);
                System.out.printf("\r%70s\r", "");
                System.out.println("Analyzed " + ctx.getClassesCount() + " of " + ctx.getTotalClasses() + " classes");
                ctx.reportStats(System.out);
                System.out.println("Analyzis time " + dur.toMinutes() + "m" + dur.getSeconds() % 60 + "s");
            }));
        ctx.analyzePackage("");
        return 0;
    }
    
    static Stream<Path> glob(String mask) throws IOException {
        Matcher matcher = Pattern.compile("(.*)[\\\\/](.*)").matcher(mask);
        Path parentPath;
        String fName;
        if(matcher.matches()) {
            parentPath = Paths.get(matcher.group(1));
            fName = matcher.group(2);
        } else {
            parentPath = Paths.get(".");
            fName = mask;
        }
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + fName);
        return Files.list(parentPath).filter(p -> pathMatcher.matches(p.getFileName()));
    }

    public static void main(String[] args) {
        System.exit(new HuntBugs().run(args));
    }
}
