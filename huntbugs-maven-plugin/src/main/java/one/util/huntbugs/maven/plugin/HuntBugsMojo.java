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
package one.util.huntbugs.maven.plugin;

import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.HuntBugsResult;
import one.util.huntbugs.input.XmlReportReader;
import one.util.huntbugs.output.Reports;
import one.util.huntbugs.repo.AuxRepository;
import one.util.huntbugs.repo.CompositeRepository;
import one.util.huntbugs.repo.DirRepository;
import one.util.huntbugs.repo.Repository;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.strobel.assembler.metadata.ClasspathTypeLoader;
import com.strobel.assembler.metadata.CompositeTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.JarTypeLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Goal which launches the HuntBugs static analyzer tool.
 */
@Mojo(name = "huntbugs", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresProject = true, threadSafe = true)
public class HuntBugsMojo extends AbstractMojo {
    /**
     * Location of the file.
     */
    @Parameter(defaultValue = "${project.build.directory}/huntbugs", property = "outputDir", required = true)
    private File outputDirectory;

    /**
     * Location of classes to analyze
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "classesDir", required = true)
    private File classesDirectory;

    /**
     * Minimal warning score to report
     */
    @Parameter(defaultValue = "30", property = "minScore", required = true)
    private int minScore;
    
    /**
     * Do not print progress messages
     */
    @Parameter(defaultValue = "false", property = "quiet", required = true)
    private boolean quiet;
    
    /**
     * If true and report already exists, generate diff report with previous version
     */
    @Parameter(defaultValue = "true", property = "diff", required = true)
    private boolean diff;
    
    @Parameter( defaultValue = "${project.compileClasspathElements}", readonly = true, required = true )
    private List<String> classpathElements;
    
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private MavenSession session;
    
    @Override
    public void execute() throws MojoExecutionException {
        try {
            Context ctx = new Context(constructRepository(), constructOptions());

            if (!quiet) {
                addAnalysisProgressListener(ctx);
            }

            ctx.analyzePackage("");
            writeReports(ctx);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to run HuntBugs", e);
        }
    }
    
    private Repository constructRepository() throws IOException {
        Repository repo = new DirRepository(classesDirectory.toPath());
        
        if (!quiet) {
            getLog().info("HuntBugs: +dir " + classesDirectory);
        }

        List<ITypeLoader> deps = new ArrayList<>();
        ArtifactRepository localRepository = session.getLocalRepository();

        Set<Artifact> dependencyArtifacts = project.getDependencyArtifacts();
        if (dependencyArtifacts != null) {
            for (Artifact art : dependencyArtifacts) {
                if (art.getScope().equals("compile")) {
                    File f = localRepository.find(art).getFile();
                    if (f != null) {
                        Path path = f.toPath();
                        if (!quiet) {
                            getLog().info("HuntBugs: +dep " + path);
                        }
                        if (Files.isRegularFile(path)) {
                            deps.add(new JarTypeLoader(new JarFile(path.toFile())));
                        } else if (Files.isDirectory(path)) {
                            deps.add(new ClasspathTypeLoader(path.toString()));
                        }
                    }
                }
            }
        }
        
        if (deps.isEmpty()) {
            return repo;
        }
        
        return new CompositeRepository(
            Arrays.asList(repo, new AuxRepository(new CompositeTypeLoader(deps.toArray(new ITypeLoader[0])))));
    }
    
    private AnalysisOptions constructOptions() {
        AnalysisOptions options = new AnalysisOptions();
        options.minScore = minScore;
        
        return options;
    }
    
    private void addAnalysisProgressListener(Context ctx) {
        long[] lastPrint = {0};
        ctx.addListener((stepName, className, count, total) -> {
            if (count == total || System.currentTimeMillis() - lastPrint[0] > 2000) {
                getLog().info("HuntBugs: " + stepName + " [" + count + "/" + total + "]");
                lastPrint[0] = System.currentTimeMillis();
            }
            return true;
        });
    }
    
    private void writeReports(Context ctx) throws Exception {
        getLog().info("HuntBugs: Writing report (" + ctx.getStat("Warnings") + " warnings)");
        Path path = outputDirectory.toPath();
        Files.createDirectories(path);
        Path xmlFile = path.resolve("report.xml");
        Path htmlFile = path.resolve("report.html");
        HuntBugsResult res = ctx;
        if(Files.isRegularFile(xmlFile)) {
            res = Reports.diff(XmlReportReader.read(ctx, xmlFile), ctx);
        }
        Reports.write(xmlFile, htmlFile, res);
    }
}
