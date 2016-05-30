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

import static org.junit.Assert.*;

import java.util.stream.Collectors;

import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.repo.FilteredRepository;
import one.util.huntbugs.repo.Repository;

import org.junit.Test;

/**
 * @author Tagir Valeev
 *
 */
public class FilteredRepositoryTest {
    @Test
    public void filteredRepoTest() {
        Repository parent = Repository.createSelfRepository();
        Repository repo = new FilteredRepository(parent, cn -> cn.endsWith("/TestSyncGetClass"));
        Context ctx = new Context(repo, new AnalysisOptions());
        ctx.analyzePackage("one/util/huntbugs/testdata");
        assertEquals("", ctx.errors().map(Object::toString).collect(Collectors.joining()));
        assertTrue(ctx.warnings().count() > 0);
        ctx.warnings().allMatch(w -> w.getType().getName().equals("SyncOnGetClass"));
    }
}
