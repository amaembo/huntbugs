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

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestSameIfChain {
    int f = 3;
    
    @AssertWarning("SameConditionChain")
    public void testSimple(int x) {
        if(x > 0) {
            System.out.println(1);
        }
        if(0 < x) {
            System.out.println(2);
        }
    }

    @AssertWarning("SameConditionChain")
    public void testField(int x) {
        if(x > f) {
            System.out.println(1);
        }
        if(f < x) {
            System.out.println(2);
        }
    }
    
    @AssertNoWarning("*")
    public void testOk(int x) {
        if(x > 0) {
            x--;
        }
        if(x > 0) {
            System.out.println(2);
        }
    }
}
