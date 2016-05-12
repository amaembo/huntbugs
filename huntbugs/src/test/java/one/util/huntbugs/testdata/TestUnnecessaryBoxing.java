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
public class TestUnnecessaryBoxing {
    @AssertWarning(type = "BoxedForToString")
    public String getString(int a, int b) {
        Integer integer = a + b;
        return integer.toString();
    }

    @AssertWarning(type = "BoxedForToString")
    public String getString(double a, double b) {
        return new Double(a + b).toString();
    }
    
    @AssertNoWarning(type = "BoxedForToString")
    public String getStringNoToString(int a, int b) {
        Integer sum = a + b;
        return Integer.toString(sum);
    }

    @AssertWarning(type = "BoxedForUnboxing")
    public int boxUnbox(int a, int b) {
        Integer result = a + b;
        return result;
    }
    
    @AssertWarning(type = "UnboxedForBoxing", minScore=40)
    public Integer unboxBox(Integer x) {
        int a = x;
        return a;
    }

    @AssertNoWarning(type = "UnboxedForBoxing")
    public Integer unboxBox(Character x) {
        int a = x;
        return a;
    }
    
    @AssertWarning(type = "UnboxedForBoxing", maxScore=30)
    public Boolean unboxBox(Boolean x) {
        boolean a = x;
        return a;
    }
    
    @AssertWarning(type = "UnboxedForBoxing")
    public Integer unboxBoxTwice(Integer x) {
        int a = x;
        if (x > 2)
            return unboxBox(a);
        return a;
    }

    @AssertNoWarning(type = "UnboxedForBoxing")
    public Integer unboxBoxOk(Integer x) {
        int a = x;
        if (x > 2)
            return Math.abs(a);
        return a;
    }
}
