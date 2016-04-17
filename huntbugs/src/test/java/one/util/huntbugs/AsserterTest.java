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

import static org.junit.Assert.*;

import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.repo.Repository;

import org.junit.Test;

/**
 * @author lan
 *
 */
public class AsserterTest {
    @Test
    public void test() throws Exception {
        Context ctx = new Context(Repository.createSelfRepository(), new AnalysisOptions());
        ctx.analyzePackage("one/util/huntbugs/asserter");
        assertEquals(4, ctx.getErrorCount());
        assertTrue(ctx.errors().anyMatch(em -> em.toString().contains("rule: AssertNoWarning(type = RoughConstantValue)")));
        assertTrue(ctx.errors().anyMatch(em -> em.toString().contains("rule: AssertNoWarning(type = Rough*)")));
        assertTrue(ctx.errors().anyMatch(em -> em.toString().contains("rule is not satisfied: AssertWarning(type = AAA; score = 0..100)")));
        assertTrue(ctx.errors().anyMatch(em -> em.toString().contains("rule is not satisfied: AssertWarning(type = BBB; score = 0..100)")));
    }
}
