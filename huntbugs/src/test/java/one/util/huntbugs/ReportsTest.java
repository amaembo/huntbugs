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

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Test;

import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.analysis.HuntBugsResult;
import one.util.huntbugs.output.Reports;
import one.util.huntbugs.repo.Repository;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningStatus;
import one.util.huntbugs.warning.WarningType;

/**
 * @author lan
 *
 */
public class ReportsTest {
    @Test(expected=IllegalArgumentException.class)
    public void testMergeZero() {
        Reports.merge(Collections.emptyList());
    }
    
    @Test
    public void testMerge() {
        Context ctx = new Context(Repository.createNullRepository(), new AnalysisOptions());
        ctx.addWarning(new Warning(ctx.getWarningType("RoughConstantValue"), 0, Arrays.asList(Roles.TYPE.create("test/type"))));
        ctx.addError(new ErrorMessage("detector", "cls", "member", "desc", -1, "Error"));
        
        Context ctx2 = new Context(Repository.createNullRepository(), new AnalysisOptions());
        ctx2.addWarning(new Warning(ctx.getWarningType("BadNameOfField"), 0, Arrays.asList(Roles.TYPE.create("test/type2"))));
        ctx2.addError(new ErrorMessage("detector2", "cls", "member", "desc", -1, "Error"));
        
        HuntBugsResult result = Reports.merge(Arrays.asList(ctx, ctx2));
        assertEquals(2, result.errors().count());
        assertEquals(2, result.warnings().count());
        
        assertEquals(Arrays.asList("detector", "detector2"), result.errors().map(ErrorMessage::getDetector).collect(
            Collectors.toList()));
        assertEquals(Arrays.asList("RoughConstantValue", "BadNameOfField"), result.warnings().map(Warning::getType).map(
            WarningType::getName).collect(Collectors.toList()));
    }
    
    @Test
    public void testDiff() {
        Context ctx = new Context(Repository.createNullRepository(), new AnalysisOptions());
        ctx.addWarning(new Warning(ctx.getWarningType("RoughConstantValue"), 0, Arrays.asList(Roles.TYPE.create("test/type"))));
        ctx.addWarning(new Warning(ctx.getWarningType("RoughConstantValue"), 0, Arrays.asList(Roles.TYPE.create("test/type2"))));
        ctx.addError(new ErrorMessage("detector", "cls", "member", "desc", -1, "Error"));

        Context ctx2 = new Context(Repository.createNullRepository(), new AnalysisOptions());
        ctx2.addWarning(new Warning(ctx.getWarningType("RoughConstantValue"), 5, Arrays.asList(Roles.TYPE.create("test/type2"))));
        ctx2.addWarning(new Warning(ctx.getWarningType("BadNameOfField"), 0, Arrays.asList(Roles.TYPE.create("test/type3"))));
        ctx2.addError(new ErrorMessage("detector2", "cls", "member", "desc", -1, "Error"));
        
        HuntBugsResult result = Reports.diff(ctx, ctx2);
        assertEquals(1, result.errors().count());
        assertEquals(Optional.of("detector2"), result.errors().map(ErrorMessage::getDetector).findFirst());
        
        assertEquals(3, result.warnings().count());
        assertEquals(Optional.of(WarningStatus.ADDED), result.warnings().filter(w -> w.getType().getName().equals(
            "BadNameOfField")).map(Warning::getStatus).findFirst());
        assertEquals(Optional.of(WarningStatus.FIXED), result.warnings().filter(w -> w.getClassName().equals(
            "test.type")).map(Warning::getStatus).findFirst());
        assertEquals(Optional.of(WarningStatus.SCORE_LOWERED), result.warnings().filter(w -> w.getClassName().equals(
            "test.type2") && w.getType().getName().equals("RoughConstantValue")).map(Warning::getStatus).findFirst());
    }
}
