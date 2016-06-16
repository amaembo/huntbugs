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
public class TestRoughConstants {
    private static final float PI_FLOAT = 3.14159f;
    private static final double PI_DOUBLE = 3.14159;

    @AssertNoWarning("*")
    public void testNan() {
        double a;
        a = Double.NaN;
        System.out.println(a);
        a = Double.POSITIVE_INFINITY;
        System.out.println(a);
        a = Double.NEGATIVE_INFINITY;
        System.out.println(a);
        float b;
        b = Float.NaN;
        System.out.println(b);
        b = Float.POSITIVE_INFINITY;
        System.out.println(b);
        b = Float.NEGATIVE_INFINITY;
        System.out.println(b);
    }

    @AssertNoWarning("*")
    public void testNoWarning() {
        double a;
        a = Math.PI; // Exact
        System.out.println(a);
        a = 3.141512345; // Explicitly specified something different from PI
        System.out.println(a);
        a = 3.1417;
        System.out.println(a);
        a = 3.1414;
        System.out.println(a);
        float b;
        b = (float)Math.PI; // Exact value converted to float
        System.out.println(b);
        b = 3.141512345f; // Explicitly specified something different from PI
        System.out.println(b);
        b = 3.1417f;
        System.out.println(b);
        b = 3.1414f;
        System.out.println(b);
    }

    @AssertWarning(value="RoughConstantValue", minScore=50)
    public void testPi1() {
        System.out.println(3.141592);
    }

    @AssertWarning(value="RoughConstantValue", minScore=30)
    public void testPi2() {
        System.out.println(3.141592612345); // Something different, but too close to PI, thus suspicious
    }

    @AssertWarning(value="RoughConstantValue", minScore=40)
    public void testPi3() {
        System.out.println(3.1415f);
    }

    @AssertWarning(value="RoughConstantValue", minScore=50)
    public void testPi4() {
        System.out.println(PI_DOUBLE);
    }

    @AssertWarning(value="RoughConstantValue", minScore=50)
    public void testPi5() {
        System.out.println(PI_FLOAT);
    }

    @AssertWarning(value="RoughConstantValue", minScore=50)
    public void testPi6() {
        System.out.println(PI_FLOAT);
    }

    @AssertWarning(value="RoughConstantValue", minScore=40)
    public void test2Pi1() {
        System.out.println(2*3.141592);
    }

    @AssertWarning(value="RoughConstantValue", minScore=30)
    public void test2Pi2() {
        System.out.println(2*3.1416);
    }

    @AssertWarning(value="RoughConstantValue", minScore=30)
    public void test2Pi3() {
        System.out.println(2*3.1415);
    }

    @AssertWarning(value="RoughConstantValue", minScore=30)
    public void test2Pi4() {
        System.out.println(6.2831);
    }

    @AssertWarning(value="RoughConstantValue", minScore=30)
    public void testE1() {
        System.out.println(2.7183);
    }

    @AssertWarning(value="RoughConstantValue", minScore=30)
    public void testE2() {
        System.out.println(2.71828f);
    }

    @AssertWarning(value="RoughConstantValue", maxScore=20)
    public void testE2Digits() {
        // Too far away from real value and E is not very popular number
        System.out.println(2.72);
    }

    @AssertWarning(value="RoughConstantValue", minScore=30)
    public void testPi2Digits() {
        // Pi is more popular, thus likely a bug
        System.out.println(3.14);
    }

    @AssertWarning(value="RoughConstantValue", maxScore=30)
    public void testDoubleArray() {
        double[] arr = new double[] {2.7183, 2.7184, 2.7185};
        System.out.println(arr[0]);
    }

    @AssertWarning(value="RoughConstantValue", maxScore=30)
    public void testDoubleArray2() {
        Double[] arr = new Double[] {2.7183, 2.7184, 2.7185};
        System.out.println(arr[0]);
    }

    @AssertWarning("RoughConstantValue")
    public void testDoubleArrayHighPrecision() {
        Double[] arr = new Double[] {2.718281828, 2.719, 2.72};
        System.out.println(arr[0]);
    }

}
