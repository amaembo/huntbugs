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
public class TestSameBranches {
    @AssertWarning(type="SameBranchesIf")
    public void testSame(int a) {
        if(a > 0) {
            System.out.println("Foo");
        } else {
            System.out.println("Foo");
        }
    }
    
    @AssertWarning(type="SameBranchesTernary")
    public int testSame(int a, int[] b) {
        return a > 0 ? b[a++] : b[a++];
    }
    
    @AssertWarning(type="SameBranchesIf")
    public void testSameComplex(int a, int b) {
        if(a > 0) {
            if(b > 0) {
                try {
                    while(b < 10) {
                        System.out.println("Foo");
                        b++;
                    }
                } catch (NullPointerException | IllegalAccessError e) {
                    System.out.println("oops..."+e);
                }
            }
        } else {
            if(b > 0) {
                try {
                    while(b < 10) {
                        System.out.println("Foo");
                        b++;
                    }
                } catch (NullPointerException | IllegalAccessError e) {
                    System.out.println("oops..."+e);
                }
            }
        }
    }
    
    @AssertNoWarning(type="SameBranchesIf")
    public void testDiffComplex(int a, int b) {
        if(a > 0) {
            if(b > 0) {
                try {
                    while(b < 10) {
                        System.out.println("Foo");
                        b++;
                    }
                } catch (NullPointerException | IllegalAccessError e) {
                    System.out.println("oops..."+e);
                }
            }
        } else {
            if(b > 0) {
                try {
                    while(b < 10) {
                        System.out.println("Foo");
                        b++;
                    }
                } catch (NullPointerException | IllegalAccessError e) {
                    System.out.println("oopss..."+e);
                }
            }
        }
    }
    
    @AssertNoWarning(type="SameBranchesIf")
    public void testDiff(int a) {
        if(a > 0) {
            System.out.println("Foo");
        } else {
            System.out.println("Bar");
        }
    }
}
