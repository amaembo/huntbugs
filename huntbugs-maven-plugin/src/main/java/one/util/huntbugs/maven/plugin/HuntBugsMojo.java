package one.util.huntbugs.maven.plugin;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.output.XmlReportWriter;
import one.util.huntbugs.repo.DirRepository;
import one.util.huntbugs.repo.Repository;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

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

    @Override
    public void execute() throws MojoExecutionException {
        try {
            Repository repo = new DirRepository(classesDirectory.toPath());
            AnalysisOptions options = new AnalysisOptions();
            options.minScore = minScore;
            Context ctx = new Context(repo, options);
            ctx.addListener((stepName, className) -> {
                if (!stepName.equals("Preparing")) {
                    int totalClasses = ctx.getTotalClasses();
                    int classesCount = ctx.getClassesCount() + 1;
                    if (classesCount == totalClasses || classesCount % 50 == 0)
                        getLog().info("HuntBugs: " + stepName + " [" + classesCount + "/" + totalClasses + "]");
                }
                return true;
            });
            getLog().info("HuntBugs: Preparing");
            ctx.analyzePackage("");
            getLog().info("HuntBugs: Writing report (" + ctx.getStat("Warnings") + " warnings)");
            Path path = outputDirectory.toPath();
            Files.createDirectories(path);
            Path xmlFile = path.resolve("report.xml");
            Path htmlFile = path.resolve("report.html");
            new XmlReportWriter(xmlFile, htmlFile).write(ctx);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to run HuntBugs", e);
        }
    }
}
