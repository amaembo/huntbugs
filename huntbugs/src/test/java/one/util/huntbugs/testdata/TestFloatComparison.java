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
 * @author Tagir Valeev
 *
 */
public class TestFloatComparison {
    @AssertWarning(value="FloatComparison", minScore = 25, maxScore = 45)
    void testFloat(float a, float b) {
        if(a == b)
            System.out.println("Equals");
    }

    @AssertWarning(value="FloatComparison", minScore = 5, maxScore = 20)
    void testFloatInt(float a, int b) {
        if(a == b)
            System.out.println("Equals");
    }
    
    @AssertNoWarning("FloatComparison")
    void testFloat(float a) {
        if(a == 0)
            System.out.println("Equals");
    }

    @AssertWarning(value="FloatComparison", minScore = 25, maxScore = 45)
    void testDouble(double a, double b) {
        if(a == b)
            System.out.println("Equals");
    }
    
    @AssertNoWarning("*")
    void testRound(double a) {
        if(a == (int)a)
            System.out.println("Round");
    }
    
    @AssertNoWarning("FloatComparison")
    void testDouble(double a) {
        if(a == 0)
            System.out.println("Equals");
    }
    
    @AssertWarning(value="FloatComparison", minScore = 5, maxScore = 15)
    void testDouble2(double a) {
        if(a == 3.0)
            System.out.println("Equals");
    }
    
    @AssertWarning(value="FloatComparison", minScore = 15, maxScore = 25)
    void testDouble3(double a) {
        if(a == 5.0)
            System.out.println("Equals");
        if(a == 1000)
            System.out.println("Equals");
    }
    
    @AssertWarning(value="FloatComparison", minScore = 15, maxScore = 25)
    void testDouble4(double a) {
        if(a == 10.5)
            System.out.println("Equals");
    }

    @AssertWarning(value="FloatComparison", minScore = 10, maxScore = 20)
    void testDoublePhi(boolean b, double a) {
        double x = a;
        if(b) x = a+10;
        if(a == x)
            System.out.println("Equals");
    }
    
    @AssertWarning(value="FloatComparison", minScore = 10, maxScore = 20)
    void testDoubleTernary(boolean b, double a) {
        double x = b ? a : a+10;
        if(a == x)
            System.out.println("Equals");
    }
}
