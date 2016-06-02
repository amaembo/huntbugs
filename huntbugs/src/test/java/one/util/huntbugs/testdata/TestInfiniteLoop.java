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
public class TestInfiniteLoop {
    @AssertWarning("InfiniteLoop")
    public void testLoop(int x) {
        while (x > 2) {
            System.out.println(x);
        }
    }

    @AssertWarning("InfiniteLoop")
    public void testFlag() {
        boolean b = true;
        while (b) {
            System.out.println(b);
        }
    }

    @AssertNoWarning("InfiniteLoop")
    @AssertWarning("InvariantLoopCondition")
    public void testLoopInvariant(int x) {
        while (x > 2) {
            System.out.println(1);
            if (Math.random() > 0.5)
                break;
        }
    }

    @AssertNoWarning("InfiniteLoop")
    public void testLoopOk(int x) {
        while (x > 2) {
            System.out.println(x--);
        }
    }

    @AssertNoWarning("InfiniteLoop")
    @AssertWarning("InvariantLoopConditionPart")
    public void testLoopPart(int x, int z) {
        while (x > 2 || z == 10) {
            System.out.println(x--);
        }
    }

    TestInfiniteLoop next;

    @AssertNoWarning("InfiniteLoop")
    public void dump(TestInfiniteLoop obj) {
        int x = 10;
        while (obj != null) {
            --x;
            obj = obj.next;
        }
        System.out.println(x);
    }
}
