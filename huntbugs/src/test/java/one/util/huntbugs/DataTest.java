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

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.HuntBugsResult;
import one.util.huntbugs.input.XmlReportReader;
import one.util.huntbugs.output.Reports;
import one.util.huntbugs.repo.Repository;
import static org.junit.Assert.*;

import org.junit.Test;

/**
 * @author Tagir Valeev
 *
 */
public class DataTest {
    @Test
    public void test() throws Exception {
        Context ctx = new Context(Repository.createSelfRepository(), new AnalysisOptions());
        ctx.analyzePackage("one/util/huntbugs/testdata");
        ctx.reportStats(System.out);
        ctx.reportErrors(System.err);
        ctx.reportWarnings(new PrintStream("target/testWarnings.out"));
        Path xmlReport = Paths.get("target/testWarnings.xml");
        Reports.write(xmlReport, Paths.get("target/testWarnings.html"), ctx);
        System.out.println("Analyzed "+ctx.getClassesCount()+" classes");
        if(ctx.getErrorCount() > 0)
            fail("Analysis finished with "+ctx.getErrorCount()+" errors");
        HuntBugsResult result = XmlReportReader.read(ctx, xmlReport);
        Path rereadReport = Paths.get("target/testWarnings_reread.xml");
        Reports.write(rereadReport, null, result);
        assertArrayEquals(Files.readAllBytes(xmlReport), Files.readAllBytes(rereadReport));
    }
}
