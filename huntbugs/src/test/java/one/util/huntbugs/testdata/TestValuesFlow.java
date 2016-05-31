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
package one.util.huntbugs.testdata;

import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;

import one.util.huntbugs.registry.anno.AssertNoWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestValuesFlow {
    @AssertNoWarning("*")
    void testForSwitch(Map<Character, Integer> mod) {
        Map<Character, Integer> mapTemp = new Hashtable<>();
        for (Entry<Character, Integer> e : mod.entrySet()) {
            Character key = e.getKey();
            switch (key) {
            case 'B':
                // we will phase these at the time of rotation, in setModRot
                break;
            case 'C':
                // not implemented
                continue;
            }
            mapTemp.put(key, e.getValue());
        }
    }
}
