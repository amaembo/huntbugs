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
public class TestRoughConstants {
    private static final float PI_FLOAT = 3.14159f;
    private static final double PI_DOUBLE = 3.14159;

    @AssertNoWarning(type="RoughConstantValue")
    public void testNan() {
        double a;
        a = Double.NaN;
        a = Double.POSITIVE_INFINITY;
        a = Double.NEGATIVE_INFINITY;
        float b;
        b = Float.NaN;
        b = Float.POSITIVE_INFINITY;
        b = Float.NEGATIVE_INFINITY;
    }

    @AssertNoWarning(type="RoughConstantValue")
    public void testNoWarning() {
        double a;
        a = Math.PI; // Exact
        a = 3.141512345; // Explicitly specified something different from PI
        a = 3.1417;
        a = 3.1414;
        float b;
        b = (float)Math.PI; // Exact value converted to float
        b = 3.141512345f; // Explicitly specified something different from PI
        b = 3.1417f;
        b = 3.1414f;
    }

    @AssertWarning(type="RoughConstantValue", minRank=50)
    public void testPi1() {
        System.out.println(3.141592);
    }

    @AssertWarning(type="RoughConstantValue", minRank=30)
    public void testPi2() {
        System.out.println(3.141592612345); // Something different, but too close to PI, thus suspicious
    }

    @AssertWarning(type="RoughConstantValue", minRank=40)
    public void testPi3() {
        System.out.println(3.1415f);
    }

    @AssertWarning(type="RoughConstantValue", minRank=50)
    public void testPi4() {
        System.out.println(PI_DOUBLE);
    }

    @AssertWarning(type="RoughConstantValue", minRank=50)
    public void testPi5() {
        System.out.println(PI_FLOAT);
    }

    @AssertWarning(type="RoughConstantValue", minRank=50)
    public void testPi6() {
        System.out.println(PI_FLOAT);
    }

    @AssertWarning(type="RoughConstantValue", minRank=40)
    public void test2Pi1() {
        System.out.println(2*3.141592);
    }

    @AssertWarning(type="RoughConstantValue", minRank=30)
    public void test2Pi2() {
        System.out.println(2*3.1416);
    }

    @AssertWarning(type="RoughConstantValue", minRank=30)
    public void test2Pi3() {
        System.out.println(2*3.1415);
    }

    @AssertWarning(type="RoughConstantValue", minRank=30)
    public void test2Pi4() {
        System.out.println(6.2831);
    }

    @AssertWarning(type="RoughConstantValue", minRank=30)
    public void testE1() {
        System.out.println(2.7183);
    }

    @AssertWarning(type="RoughConstantValue", minRank=30)
    public void testE2() {
        System.out.println(2.71828f);
    }

    @AssertWarning(type="RoughConstantValue", maxRank=20)
    public void testE2Digits() {
        // Too far away from real value and E is not very popular number
        System.out.println(2.72);
    }

    @AssertWarning(type="RoughConstantValue", minRank=30)
    public void testPi2Digits() {
        // Pi is more popular, thus likely a bug
        System.out.println(3.14);
    }

    @AssertWarning(type="RoughConstantValue", maxRank=30)
    public void testDoubleArray() {
        double[] arr = new double[] {2.7183, 2.7184, 2.7185};
        System.out.println(arr[0]);
    }

    @AssertWarning(type="RoughConstantValue", maxRank=30)
    public void testDoubleArray2() {
        Double[] arr = new Double[] {2.7183, 2.7184, 2.7185};
        System.out.println(arr[0]);
    }

    @AssertWarning(type="RoughConstantValue")
    public void testDoubleArrayHighPrecision() {
        Double[] arr = new Double[] {2.718281828, 2.719, 2.72};
        System.out.println(arr[0]);
    }

}
