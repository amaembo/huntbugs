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

import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestConditionChain {
    @AssertWarning(type = "SameConditions")
    public void testSameConditions(int x) {
        if (x > 2) {
            if (x > 2) {
                System.out.println(x);
            }
        }
    }

    @AssertWarning(type = "SameConditions")
    public void testSameConditionsElse(int x) {
        if (x > 2) {
            if (x > 2) {
                System.out.println(x);
            } else {
                System.out.println("foo");
            }
        }
    }
    
    @AssertWarning(type = "SameConditions")
    public void testSameConditionsChain(int x) {
        if (x > 2) {
            if (x < 4) {
                if (x > 2) {
                    System.out.println(x);
                }
            }
        }
    }

    @AssertWarning(type = "SameConditions")
    public void testSameConditionsChainElse1(int x) {
        if (x > 2) {
            if (x < 4) {
                if (x > 2) {
                    System.out.println(x);
                } else {
                    System.out.println("false");
                }
            }
        }
    }
    
    @AssertWarning(type = "SameConditions")
    public void testSameConditionsChainElse2(int x) {
        if (x > 2) {
            if (x < 4) {
                if (x > 2) {
                    System.out.println(x);
                } else {
                    System.out.println("false");
                }
            } else {
                System.out.println("false");
            }
        }
    }
    
    @AssertWarning(type = "SameConditionsExcluding")
    public void testSameConditionsExcluding(int x) {
        if (x > 2) {
            System.out.println("foo3");
        } else if (x < 4) {
            if (x > 2) {
                System.out.println(x);
            } else {
                System.out.println("foo1");
            }
        } else {
            System.out.println("foo2");
        }
    }
}
