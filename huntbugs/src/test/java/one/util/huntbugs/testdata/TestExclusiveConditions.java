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

import java.util.concurrent.TimeUnit;

import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestExclusiveConditions {
    @AssertWarning(type="AndEqualsAlwaysFalse")
    public void testSimple(int x) {
        if(x == 1 && x == 2) {
            System.out.println("Never!");
        }
    }

    @AssertWarning(type="OrNotEqualsAlwaysTrue")
    public void testOr(int x) {
        if(x != 1 || x != 2) {
            System.out.println("Always!");
        }
    }

    @AssertWarning(type="AndEqualsAlwaysFalse")
    public void testBoxing(Integer x) {
        Integer a = 1;
        Integer b = 2;
        if(x == a && x == b) {
            System.out.println("Never!");
        }
    }
    
    @AssertWarning(type="AndEqualsAlwaysFalse")
    public void testStrings(String str) {
        if(str.equals("A\nB") && str.equals("B\nA")) {
            System.out.println("Never!");
        }
    }

    @AssertWarning(type="AndEqualsAlwaysFalse")
    public void testEnum(TimeUnit tu) {
        if(tu.equals(TimeUnit.DAYS) && tu == TimeUnit.HOURS) {
            System.out.println("Never!");
        }
    }
    
    @AssertWarning(type="AndEqualsAlwaysFalse")
    public void testComplex(int x, int y, int z) {
        if(x == 1 && y == 2 && z == 3 && 4 == x) {
            System.out.println("Never!");
        }
    }

    @AssertWarning(type="OrNotEqualsAlwaysTrue")
    public void testComplexOr(int x, int y, int z) {
        if(x != 1 || y != 2 || z != 3 || 4 != x) {
            System.out.println("Always!");
        }
    }
}
