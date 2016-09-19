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
package one.util.huntbugs.sample.testdata;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Mihails Volkovs
 */
public class TestSampleCustomDetector {

    private static final String DEMO_CUSTOM_DETECTOR = "SampleCustomDetector";

    @AssertWarning(DEMO_CUSTOM_DETECTOR)
    public Collection getCollection() {
        return new ArrayList();
    }

    @AssertNoWarning(DEMO_CUSTOM_DETECTOR)
    public Map getMap() {
        return new HashMap();
    }

}
