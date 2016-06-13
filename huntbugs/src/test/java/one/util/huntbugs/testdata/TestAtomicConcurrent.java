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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestAtomicConcurrent {
    final Map<String, Integer> chm = new ConcurrentHashMap<>();
    
    @AssertWarning("NonAtomicOperationOnConcurrentMap")
    public void testAtomic(String str) {
        if(!chm.containsKey(str)) {
            chm.put(str, 1);
        } else {
            chm.put(str, 0);
        }
    }

    @AssertWarning("NonAtomicOperationOnConcurrentMap")
    public void testAtomic2(String str) {
        Integer oldVal = chm.get(str);
        if(oldVal == null) {
            chm.put(str, 1);
        } else {
            chm.put(str, 0);
        }
    }
    
    @AssertWarning("NonAtomicOperationOnConcurrentMap")
    public void testAtomicUpdate(String str) {
        chm.put(str, chm.get(str) + 1);
    }

    @AssertWarning("NonAtomicOperationOnConcurrentMap")
    public void testAtomicUpdate2(String str) {
        Integer res = chm.get(str);
        chm.put(str, res + 1);
    }
}
