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

import java.util.List;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestInfiniteRecursion {
    @AssertWarning("InfiniteRecursion")
    public TestInfiniteRecursion(int x) {
        System.out.print(new TestInfiniteRecursion(1));
    }
    
    @AssertWarning("InfiniteRecursion")
    public TestInfiniteRecursion(int x, int y) {
        if(x > 2)
            return;
        new TestInfiniteRecursion(x, y);
    }
    
    @AssertWarning("InfiniteRecursion")
    public static void testSimple() {
        testSimple();
    }

    @AssertWarning("InfiniteRecursion")
    public static void testSimple(int x) {
        testSimple(x);
    }
    
    @AssertWarning("InfiniteRecursion")
    public static void testSimpleMod(int x) {
        x--;
        testSimpleMod(x);
    }

    @AssertWarning("InfiniteRecursion")
    public static void testSimpleCheck(int x) {
        if(x > 2)
            return;
        testSimpleCheck(x);
    }
    
    @AssertWarning("InfiniteRecursion")
    public static void testSimpleCheck(int x, long y, double z, boolean b) {
        if(!b)
            return;
        testSimpleCheck(x, y, z, b);
    }
    
    @AssertNoWarning("InfiniteRecursion")
    public static void testSimpleModOk(int x) {
        if(x-- > 0)
            return;
        testSimpleModOk(x);
    }
    
    @AssertWarning("InfiniteRecursion")
    public int test() {
        return test();
    }
    
    int f;
    
    private boolean updateF() {
        return --f > 0;
    }
    
    @AssertNoWarning("InfiniteRecursion")
    public boolean has() {
        if(updateF())
            return false;
        return has();
    }
    
    @AssertNoWarning("*")
    public void printRecursively(List<?> list) {
        for(Object obj : list) {
            if(obj instanceof List) {
                printRecursively((List<?>)obj);
            } else
                System.out.println(obj);
        }
    }
    
    public interface Iface {
        @AssertWarning("InfiniteRecursion")
        default public int test(int x) {
            return test(x+1);
        }
    }
    
    public static abstract class InfiniteLoop {
        int x;

        @AssertWarning("InfiniteRecursion")
        void report() {
            report();
        }

        @AssertWarning("InfiniteRecursion")
        void report2(Object a, Object b) {
            if (a.equals(b)) // we miss this one because we assume equals can do
                             // a store
                report2(a, b);
        }

        @AssertWarning("InfiniteRecursion")
        static void report3(InfiniteLoop obj) {
            InfiniteLoop.report3(obj);
        }

        @AssertNoWarning("InfiniteRecursion")
        void doNotReport(Object a, Object b) {
            if (a.equals(b)) {
                doNotReport(b, a);
            }
        }

        @AssertNoWarning("InfiniteRecursion")
        void doNotReport2(Object a, Object b) {
            if (x == 0) {
                x = 1;
                // A field has been checked and modified
                doNotReport2(a, b);
            }
        }

        @AssertNoWarning("InfiniteRecursion")
        void doNotReport3(Object a, Object b) {
            if (opaque()) {
                // Assume method invocation reads and writes all fields
                doNotReport3(a, b);
            }
        }

        protected abstract boolean opaque();
    }
}
