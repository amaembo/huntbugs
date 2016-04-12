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

import java.util.List;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestKnownComparison {
    @AssertWarning(type = "ResultOfComparisonIsStaticallyKnownDeadCode")
    public void test() {
        int a = 2;
        int b = 3;
        if (a > b) {
            System.out.println("Never ever!");
        }
    }

    @AssertWarning(type = "ResultOfComparisonIsStaticallyKnown")
    public void test2() {
        int a = 2;
        int b = 3;
        if (a < b) {
            System.out.println("Why not?");
        }
    }

    @AssertWarning(type = "ResultOfComparisonIsStaticallyKnown")
    public void testInc() {
        int a = 2;
        int b = 3;
        b++;
        if (a < b) {
            System.out.println("Why not?");
        }
    }
    
    @AssertNoWarning(type = "ResultOfComparisonIsStaticallyKnown")
    public void testIncrement(boolean f) {
        int a = 2;
        int b = 3;
        if (f)
            a += 2;
        if (a < b) {
            System.out.println("Why not?");
        }
    }

    @AssertWarning(type = "ResultOfComparisonIsStaticallyKnownDeadCode")
    public int testTernary() {
        int a = 2;
        return a == 2 ? 1 : -1;
    }
    
    @AssertWarning(type = "ResultOfComparisonIsStaticallyKnownDeadCode")
    public int testLoopBreak() {
        int a = 2;
        int b = 1;
        while(true) {
            b = 2;
            if(++a > 4)
                break;
            System.out.println("Iteration!");
        }
        return b == 2 ? 1 : -1;
    }
    
    @AssertNoWarning(type = "*")
    public void testFor() {
        for (int i = 0; i < 10; i++) {
            if (i == 0)
                System.out.println("First!");
            System.out.println("Iteration!");
        }
    }
    
    @AssertNoWarning(type = "*")
    public void testSubFor(List<String> l2) {
        for (int i = 0, n = l2.size(); i < n; i++) {
            if (i - 1 == 0)
                System.out.println("First!");
            System.out.println("Iteration!");
        }
    }
}
