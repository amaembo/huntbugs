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

import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestFloatNaN {
    @AssertWarning("UnusedLocalVariable")
    void testLambda() {
        Runnable r = () -> System.out.println();
        Runnable r2 = System.out::println;
    }
    
    @AssertWarning("FloatCompareToNaN")
    void testFloat(float a) {
        if(a == Float.NaN)
            System.out.println("NaN!");
    }

    @AssertWarning("FloatCompareToNaN")
    void testNotFloat(float a) {
        if(Float.NaN != a)
            System.out.println("NaN!");
    }
    
    @AssertWarning("FloatCompareToNaN")
    void testDouble(double a) {
        if(a == Double.NaN)
            System.out.println("NaN!");
    }
    
    @AssertWarning("FloatCompareToNaN")
    void testNotDouble(double a) {
        if(Double.NaN != a)
            System.out.println("NaN!");
    }
}
