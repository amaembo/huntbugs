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
public class TestKnownComparison {
    private static Integer val = 0; 
    private Integer f = 0;
    private final int finalField;
    private char ch;
    
    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public void testFinalField() {
        if(finalField > 15) {
            System.out.println("Never!");
        }
    }
    
    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public TestKnownComparison() {
        finalField = 10;
        testInstanceFieldOk2(true);
        if(finalField != 10) {
            System.out.println("Never!");
        }
    }
    
    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public TestKnownComparison(boolean b) {
        if(ch + 1 == 5) {
            System.out.println("Never! "+b);
        }
        finalField = 10;
    }
    
    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public void testAnd(int x) {
        int a = 2;
        int b = 3;
        if (a > b && x > 2) {
            System.out.println("Never ever!");
        }
    }

    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public void testArrayLength(boolean x) {
        int[] a = {1, 2, 3};
        int[] b = {4, 5, 6};
        int[] c = x ? a : b;
        if (c.length > 5) {
            System.out.println("Never ever!");
        }
    }
    
    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public void testAnd2(int x) {
        int a = 2;
        int b = 3;
        if (x > 2 && a > b) {
            System.out.println("Never ever!");
        }
    }

    @AssertWarning("ResultOfComparisonIsStaticallyKnown")
    public void test() {
        int a = 2;
        int b = 3;
        if (a < b) {
            System.out.println("Why not?");
        }
    }

    @AssertWarning("ResultOfComparisonIsStaticallyKnown")
    public void testOr(int x) {
        int a = 2;
        int b = 3;
        if (x > 3 || a < b) {
            System.out.println("Why not?");
        }
    }

    @AssertWarning("ResultOfComparisonIsStaticallyKnown")
    public void testInc() {
        int a = 2;
        int b = 3;
        b++;
        if (a < b) {
            System.out.println("Why not?");
        }
    }

    @AssertNoWarning("ResultOfComparisonIsStaticallyKnown")
    public void testIncrement(boolean f) {
        int a = 2;
        int b = 3;
        if (f)
            a += 2;
        if (a < b) {
            System.out.println("Why not?");
        }
    }

    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public int testTernary() {
        int a = 2;
        return a == 2 ? 1 : -1;
    }

    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public int testLoopBreak() {
        int a = 2;
        int b = 1;
        while (true) {
            b = 2;
            if (++a > 4)
                break;
            System.out.println("Iteration!");
        }
        return b == 2 ? 1 : -1;
    }

    @AssertNoWarning("*")
    public void testFor() {
        for (int i = 0; i < 10; i++) {
            if (i == 0)
                System.out.println("First!");
            System.out.println("Iteration!");
        }
    }

    @AssertNoWarning("*")
    public void testSubFor(List<String> l2) {
        for (int i = 0, n = l2.size(); i < n; i++) {
            if (i - 1 == 0)
                System.out.println("First!");
            System.out.println("Iteration!");
        }
    }

    @AssertNoWarning("*")
    public void testForContinue(boolean b) {
        for (int i = 0; i < 10; i++) {
            if (b && i == 0 || !b && i == 2) {
                System.out.println("First!");
            } else
                continue;
            System.out.println("Iteration!");
        }
    }

    @AssertNoWarning("*")
    public void testNestedForForSwitch(boolean b) {
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1)
                System.out.println("Second pass");
            double start = 1;
            for (double x = start; x < 1024; x *= 2) {
                double q = x * 2;
                switch (pass) {
                case 0:
                    System.out.println(q);
                    if (x < 10) {
                        System.out.println("Loop");
                        for (int z = 0; z < 10; z++)
                            System.out.println(z);
                    }
                    System.out.println("Continue");
                    continue;
                case 1:
                    if (x > 128)
                        continue;
                    System.out.println(x + "1");
                    break;
                }
            }
        }
    }

    @AssertNoWarning("*")
    public void testNestedForFor() {
        for (int a = 0; a < 10; a++) {
            if (a % 2 == 0) {
                for (int iter = 0; iter < 10; iter++) {
                    System.out.println("test");
                    if (iter > 3)
                        continue;
                    for (int b = a; b < 10; b++) {
                        if (b % 2 == 0) {
                            for (int c = (a == b) ? iter + 3 : 1; c < 20; c++) {
                                if (a == b) {
                                    System.out.println("Equal");
                                }
                                System.out.println("InnerInner");
                            }
                            System.out.println("Inner");
                        }
                    }
                }
                System.out.println("Outer");
            }
        }
    }

    @AssertNoWarning("*")
    public void testIncInLoop() {
        int x = 10;
        for (int i = 0; i < 10; i++) {
            x -= 4;
            if (x > 0)
                System.out.println("X!");
            System.out.println("Iteration!");
        }
    }

    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public void testInLambda(int x) {
        Runnable r = () -> {
            int a = 2;
            int b = 3;
            if (a > b && x > 2) {
                System.out.println("Never ever!");
            }
        };
        r.run();
    }

    @AssertNoWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public void testInLambdaFP(int x) {
        Runnable r = () -> {
            int a = 2;
            if (x > a) {
                System.out.println("Can be");
            }
        };
        r.run();
    }

    @AssertNoWarning("*")
    public void testComplexLoop(int x) {
        int s = -1;
        for (int i = 0; i < x; i++) {
            if (i < 3) {
                System.out.println(1);
                if (s < 0)
                    continue;
            }
            if (i < 5) {
                System.out.println(2);
                if (s < 0)
                    s = i;
                continue;
            } else if (s < 0)
                continue;
            System.out.println(3);
        }
    }

    @AssertNoWarning("*")
    public void testComplexLoop(String ssType, String type) {
        if (ssType == null)
            return;
        int istart = -1;
        for (int i = 0; i < 100; i++) {
            if (i == 1) {
                System.out.println(0);
            } else {
                System.out.println(1);
            }
            if (type.equals("test")) {
                System.out.println(2);
                --i;
                if (istart < 0)
                    continue;
            } else if (type.equals("test2")) {
                System.out.println(3);
                if (istart < 0)
                    istart = i;
                continue;
            } else if (istart < 0) {
                continue;
            }
            if (type.equals("test3")) {
                System.out.println(4);
                if (i >= 0 && i <= 10)
                    continue;
                System.out.println(5);
            }
            System.out.println(6);
            istart = -1;
        }
    }
    
    @AssertNoWarning("*")
    public void testComplexLoop2(String type) {
        int x = -1;
        for (int i = 0; i < 100; i++) {
            if (type.equals("test")) {
                System.out.println(2);
                if (x < 0)
                    continue;
            } else if (type.equals("test2")) {
                System.out.println(3);
                if (x < 0)
                    x = i;
                continue;
            }
            x = -1;
        }
    }

    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public void testComplexLoop3(String type) {
        int x = -1;
        for (int i = 0; i < 100; i++) {
            if (type.equals("test")) {
                System.out.println(2);
                if (x < 0)
                    continue;
            } else if (type.equals("test2")) {
                x = 0;
                System.out.println(3);
                if (x < 0)
                    x = i;
                continue;
            }
            x = -1;
        }
    }

    @AssertNoWarning("*")
    public void testAssert(String type) {
        int x = 1;
        assert x > 0 && !type.isEmpty();
        System.out.println(x+":"+type);
    }

    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public static void testStaticField() {
        val = 3;
        if(val > 3) {
            System.out.println("Never");
        }
    }

    @AssertNoWarning("*")
    public static void testStaticFieldOk() {
        val = 2;
        testStaticField();
        if(val > 2) {
            System.out.println("Never");
        }
    }
    
    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public void testInstanceField() {
        f = 3;
        if(f > 3) {
            System.out.println("Never");
        }
    }
    
    @AssertNoWarning("*")
    public void testInstanceFieldOk() {
        f = 2;
        testInstanceField();
        if(f > 2) {
            System.out.println("Never");
        }
    }
    
    @AssertNoWarning("*")
    public void testInstanceFieldOk2(boolean b) {
        f = 2;
        int x = 0;
        if(b) x = f++;
        if(f > 2) {
            System.out.println("Never");
        }
        System.out.println(x);
    }
    
    @AssertNoWarning("*")
    public void testInstanceFieldOk3(int x) {
        if(x > 0)
            f = 2;
        if(f > 2) {
            System.out.println("Never");
        }
        System.out.println(x);
    }
    
    @AssertNoWarning("*")
    public void testFieldLoop() {
        f = 0;
        while(f < 10) {
            if(Math.random() > 0.5) {
                f++;
                System.out.println(f);
            } else {
                f = 1;
                System.out.println(f+"!");
            }
        }
    }
    
    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    @AssertNoWarning("ResultOfComparisonIsStaticallyKnown")
    static class TestInitial {
        static long x = 2;
        
        static {
            if(x < 1) {
                System.out.println("Cannot be");
            }
        }
    }
    
    @AssertWarning("ResultOfComparisonIsStaticallyKnownDeadCode")
    public void testAioobe(int[] x, int y) {
        int a = 0;
        try {
            a = x[y];
            if(a > 0) {
                a = 0;
            }
        }
        catch(IndexOutOfBoundsException ex) {
            if(a > 0) {
                a = 0;
            }
        }
        System.out.println(a);
    }

    @AssertNoWarning("*")
    public void testFieldDoWhile() {
        do {
            if(f == 5) {
                System.out.println(f);
            }
            doSomeProcessing();
            f = 5;
        } while(ch < 10);
    }
    
    private void doSomeProcessing() {
        f++;
        ch--;
    }

    @AssertNoWarning("*")
    static class ArrayTest {
        int[] arr;
        
        public ArrayTest(boolean b) {
            arr = b ? new int[] {1,2} : new int[] {1};
        }
        
        public void testArrayLength() {
            if(arr.length > 1) {
                System.out.println("Big");
            }
        }
    }

    // procyon bug
//    @AssertNoWarning("*")
//    public int testRetries(Scanner sc) {
//        int cnt = 0;
//        while(true) {
//            try {
//                return sc.nextInt();
//            }
//            catch(InputMismatchException ime) {
//                if(cnt < 20) {
//                    cnt++;
//                } else {
//                    throw ime;
//                }
//            }
//        }
//    }
}
