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
public class TestNumericPromotion {
    @AssertWarning(type = "IntegerMultiplicationPromotedToLong", minScore = 65)
    public long testMultiplication(int num) {
        return num * 365 * 86400 * 1000;
    }

    @AssertWarning(type = "IntegerMultiplicationPromotedToLong", minScore = 55, maxScore = 60)
    public long testMultiplication2(int num) {
        return num * 365 * 86400;
    }

    @AssertWarning(type = "IntegerMultiplicationPromotedToLong", minScore = 45, maxScore = 50)
    public long testMultiplication3(int num) {
        return num * 86400;
    }

    @AssertWarning(type = "IntegerMultiplicationPromotedToLong", minScore = 35, maxScore = 40)
    public long testMultiplication4(int num) {
        return num * 365;
    }

    @AssertWarning(type = "IntegerMultiplicationPromotedToLong", minScore = 25, maxScore = 30)
    public long testMultiplication5(int num) {
        return num * 60;
    }

    @AssertWarning(type = "IntegerMultiplicationPromotedToLong", minScore = 15, maxScore = 20)
    public long testMultiplication6(int num) {
        return num * 2;
    }

    @AssertNoWarning(type = "IntegerMultiplicationPromotedToLong")
    public long testMultiplicationTwoNum(int num, int num2) {
        return num * num2;
    }

    @AssertWarning(type = "IntegerDivisionPromotedToFloat")
    public double divide(int x, int y) {
        return x / y;
    }
    
    @AssertWarning(type = "IntegerDivisionPromotedToFloat")
    public float divideFloat(int x, int y) {
        return x / y;
    }
    
    @AssertNoWarning(type = "*")
    public float divideFloatOk(int x, int y) {
        return (float)x / y;
    }
}
