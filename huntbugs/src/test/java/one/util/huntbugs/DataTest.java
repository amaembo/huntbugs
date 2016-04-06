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

import java.io.PrintStream;

import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.repo.Repository;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author lan
 *
 */
public class DataTest {
    @Test
    public void test() throws Exception {
        Context ctx = new Context(Repository.createSelfRepository(), new AnalysisOptions());
        ctx.analyzePackage("one/util/huntbugs/testdata");
        ctx.reportErrors(System.err);
        
        ctx.reportWarnings(new PrintStream("target/testWarnings.out"));
        System.out.println("Analyzed "+ctx.getClassesCount()+" classes");
        if(ctx.getErrorCount() > 0)
            Assert.fail("Analysis finished with "+ctx.getErrorCount()+" errors");
    }
}
