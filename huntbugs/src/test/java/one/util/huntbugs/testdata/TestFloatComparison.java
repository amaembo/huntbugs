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
package one.util.huntbugs.testdata;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestFloatComparison {
    @AssertWarning(type="FloatComparison", minRank = 25, maxRank = 45)
    void testFloat(float a, float b) {
        if(a == b)
            System.out.println("Equals");
    }

    @AssertNoWarning(type="FloatComparison")
    void testFloat(float a) {
        if(a == 0)
            System.out.println("Equals");
    }

    @AssertWarning(type="FloatComparison", minRank = 25, maxRank = 45)
    void testDouble(double a, double b) {
        if(a == b)
            System.out.println("Equals");
    }
    
    @AssertNoWarning(type="FloatComparison")
    void testDouble(double a) {
        if(a == 0)
            System.out.println("Equals");
    }
    
    @AssertWarning(type="FloatComparison", minRank = 5, maxRank = 15)
    void testDouble2(double a) {
        if(a == 1.0)
            System.out.println("Equals");
    }
    
    @AssertWarning(type="FloatComparison", minRank = 15, maxRank = 25)
    void testDouble3(double a) {
        if(a == 3.0)
            System.out.println("Equals");
        if(a == 1000)
            System.out.println("Equals");
    }
    
    @AssertWarning(type="FloatComparison", minRank = 15, maxRank = 25)
    void testDouble4(double a) {
        if(a == 10.5)
            System.out.println("Equals");
    }
}
