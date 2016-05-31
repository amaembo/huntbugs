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
public class TestConditionChain {
    @AssertWarning("SameConditions")
    public void testSameConditions(int x) {
        if (x > 2) {
            if (2 < x) {
                System.out.println(x);
            }
        }
    }

    @AssertNoWarning("*")
    public void testExcludingConditions(float x) {
        if (x <= 0) {
            System.out.println("ok");
        } else if (x > 0) {
            System.out.println(x);
        }
    }

    @AssertWarning("SameConditions")
    public void testSameConditionsElse(int x) {
        if (x > 2) {
            if (x > 2) {
                System.out.println(x);
            } else {
                System.out.println("foo");
            }
        }
    }

    @AssertWarning("SameConditions")
    public void testSameConditionsChain(int x) {
        if (x > 2) {
            if (x < 4) {
                if (x > 2) {
                    System.out.println(x);
                }
            }
        }
    }

    @AssertWarning("SameConditions")
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

    @AssertWarning("SameConditions")
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

    @AssertWarning("SameConditionsExcluding")
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

    @AssertNoWarning("SameConditionsExcluding")
    @AssertWarning(value="SameConditions", maxScore = 50)
    void testCondition(int vc, short value) {
        for (int i = vc; --i >= 0;) {
            if (value <= 0)
                System.out.println("1");
            else if (value > 0)
                System.out.println("2");
        }
    }

}
