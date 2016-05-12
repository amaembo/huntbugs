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

import java.util.stream.Stream;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
public class TestSameBranches {
    @AssertWarning(type = "SameBranchesIf")
    public void testSame(int a) {
        if (a > 0) {
            System.out.println("Foo");
        } else {
            System.out.println("Foo");
        }
    }

    @AssertWarning(type = "SameBranchesTernary")
    public int testSame(int a, int[] b) {
        return a > 0 ? b[a++] : b[a++];
    }

    @AssertWarning(type = "SameBranchesIf")
    public void testSameComplex(int a, int b) {
        if (a > 0) {
            if (b > 0) {
                try {
                    while (b < 10) {
                        System.out.println("Foo");
                        b += Stream.of(1, 2, 3).reduce((m, n) -> n + m).get();
                    }
                } catch (NullPointerException | IllegalAccessError e) {
                    System.out.println("oops..." + e);
                }
            }
        } else {
            if (b > 0) {
                try {
                    while (b < 10) {
                        System.out.println("Foo");
                        b += Stream.of(1, 2, 3).reduce((x, y) -> x + y).get();
                    }
                } catch (NullPointerException | IllegalAccessError e) {
                    System.out.println("oops..." + e);
                }
            }
        }
    }

    @AssertNoWarning(type = "SameBranchesIf")
    public void testSameDiffLambda(int a, int b) {
        if (a > 0) {
            System.out.println(Stream.of(1, 2, 3).reduce((m, n) -> n - m));
        } else {
            System.out.println(Stream.of(1, 2, 3).reduce((m, n) -> m - n));
        }
    }

    @AssertWarning(type = "SameBranchesIf")
    public void testSameLambdaCapture(int a, int b) {
        if (a > 0) {
            System.out.println(Stream.of(1, 2, 3).map(x -> x + a).reduce((m, n) -> n + m));
        } else {
            System.out.println(Stream.of(1, 2, 3).map(x -> x + a).reduce((m, n) -> m + n));
        }
    }
    
    @AssertWarning(type = "SameBranchesIf")
    public void testSameSwitch(int a, int b) {
        if (a > 0) {
            switch(b) {
            case 1:
                System.out.println("One!");
                break;
            case 2:
                System.out.println("Two!");
            }
        } else {
            switch(b) {
            case 1:
                System.out.println("One!");
                break;
            case 2:
                System.out.println("Two!");
            }
        }
    }
    
    @AssertNoWarning(type = "SameBranchesIf")
    public void testDiffComplex(int a, int b) {
        if (a > 0) {
            if (b > 0) {
                try {
                    while (b < 10) {
                        System.out.println("Foo");
                        b++;
                    }
                } catch (NullPointerException | IllegalAccessError e) {
                    System.out.println("oops..." + e);
                }
            }
        } else {
            if (b > 0) {
                try {
                    while (b < 10) {
                        System.out.println("Foo");
                        b++;
                    }
                } catch (NullPointerException | IllegalAccessError e) {
                    System.out.println("oopss..." + e);
                }
            }
        }
    }

    @AssertNoWarning(type = "SameBranchesIf")
    public void testDiff(int a) {
        if (a > 0) {
            System.out.println("Foo");
        } else {
            System.out.println("Bar");
        }
    }
    
    @AssertWarning(type="SameBranchesSwitch")
    public void testSwitch(int a) {
        switch(a) {
        case 1:
            System.out.println("Foo");
            break;
        case 2:
            System.out.println("Bar");
            break;
        case 3:
            System.out.println("Bar");
            break;
        case 4:
            System.out.println("Bar");
            break;
        case 5:
            System.out.println("Foo");
            break;
        default:
            System.out.println("Baz");
        }
    }
    
    @AssertNoWarning(type="SameBranchesSwitch")
    public void testSwitchFallthrough(int a) {
        switch(a) {
        case 1:
            System.out.println("Foo");
        case 4:
            System.out.println("Foo");
        case 5:
            System.out.println("Foo");
        default:
            System.out.println("Baz");
        }
    }
    
    @AssertNoWarning(type="SameBranchesDefault")
    public void testSwitchDefault(int a) {
        switch(a) {
        case 1:
            System.out.println("Foo");
            break;
        case 4:
            System.out.println("Bar");
            break;
        default:
            System.out.println("Foo");
            break;
        }
    }
}
