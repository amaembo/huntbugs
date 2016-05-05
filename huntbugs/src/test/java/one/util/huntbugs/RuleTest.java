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

import java.util.Arrays;

import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.repo.Repository;
import one.util.huntbugs.warning.rule.CategoryRule;
import one.util.huntbugs.warning.rule.CompositeRule;
import one.util.huntbugs.warning.rule.RegexRule;

import org.junit.Test;

/**
 * @author lan
 *
 */
public class RuleTest {
    @Test
    public void testRules() {
        AnalysisOptions options = new AnalysisOptions();
        Context ctx = new Context(Repository.createNullRepository(), options);
        assertEquals(60, ctx.getWarningType("RoughConstantValue").getMaxScore());
        options.setRule(new CategoryRule("BadPractice", -10));
        ctx = new Context(Repository.createNullRepository(), options);
        assertEquals(50, ctx.getWarningType("RoughConstantValue").getMaxScore());
        options.setRule(new CompositeRule(Arrays.asList(options.getRule(), new RegexRule("Rough.+", -20))));
        ctx = new Context(Repository.createNullRepository(), options);
        assertEquals(30, ctx.getWarningType("RoughConstantValue").getMaxScore());
    }
}
