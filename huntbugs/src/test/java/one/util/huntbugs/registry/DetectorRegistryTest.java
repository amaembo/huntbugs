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
package one.util.huntbugs.registry;

import one.util.huntbugs.analysis.AnalysisOptions;
import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.repo.Repository;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Mihails Volkovs
 */
public class DetectorRegistryTest {

    private Context context;

    private DetectorRegistry detectorRegistry;

    @Before
    public void setUp() {
        context = new Context(Repository.createNullRepository(), new AnalysisOptions());
        detectorRegistry = new DetectorRegistry(context);
    }

    @Test
    public void addDetector() {
        final long WARNINGS = getWarnings();
        assertTrue(detectorRegistry.addDetector(TestDetector.class));
        assertEquals(WARNINGS + 2, getWarnings());
    }

    @Test
    public void addFakeDetector() {
        final long WARNINGS = getWarnings();
        assertFalse(detectorRegistry.addDetector(DetectorRegistryTest.class));
        assertEquals(WARNINGS, getWarnings());
    }

    private long getWarnings() {
        return context.getStat("WarningTypes.Total");
    }

    @WarningDefinition(category="DetectorRegistryTest", name="DetectorRegistryTest", maxScore=80)
    @WarningDefinition(category="DetectorRegistryTest", name="DetectorRegistryTest", maxScore=80)
    private static class TestDetector {

    }

}