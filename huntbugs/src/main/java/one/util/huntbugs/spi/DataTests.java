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
package one.util.huntbugs.spi;

import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.analysis.HuntBugsResult;
import one.util.huntbugs.input.XmlReportReader;
import one.util.huntbugs.output.Reports;
import one.util.huntbugs.repo.CompositeRepository;
import one.util.huntbugs.repo.Repository;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * @author Tagir Valeev
 *
 */
public abstract class DataTests {

    public static void test(String packageToAnalyze) throws Exception {

        // creating built-in and plugins repositories
        List<Repository> repositories = new ArrayList<>();
        repositories.add(Repository.createSelfRepository());
        for (HuntBugsPlugin huntBugsPlugin : ServiceLoader.load(HuntBugsPlugin.class)) {
            repositories.add(Repository.createPluginRepository(huntBugsPlugin));
        }
        CompositeRepository repository = new CompositeRepository(repositories);

        Context ctx = new Context(repository, new AnalysisOptions());
        ctx.analyzePackage(packageToAnalyze);
        ctx.reportStats(System.out);
        ctx.reportErrors(System.err);
        ctx.reportWarnings(new PrintStream("target/testWarnings.out"));
        Path xmlReport = Paths.get("target/testWarnings.xml");
        Reports.write(xmlReport, Paths.get("target/testWarnings.html"), ctx);
        System.out.println("Analyzed " + ctx.getClassesCount() + " classes");
        if (ctx.getErrorCount() > 0) {
            List<ErrorMessage> errorMessages = ctx.errors().collect(Collectors.toList());
            throw new AssertionError(format("Analysis finished with %s errors: %s", ctx.getErrorCount(), errorMessages));
        }
        HuntBugsResult result = XmlReportReader.read(ctx, xmlReport);
        Path rereadReport = Paths.get("target/testWarnings_reread.xml");
        Reports.write(rereadReport, null, result);
        byte[] expectedReport = Files.readAllBytes(xmlReport);
        byte[] actualReport = Files.readAllBytes(rereadReport);
        if (!Arrays.equals(expectedReport, actualReport)) {
            String errorMessage = format("Expected: \n%s\n\nActual: \n%s\n\n", new String(expectedReport), new String(actualReport));
            throw new AssertionError(errorMessage);
        }
    }

}
