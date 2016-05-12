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
public class TestSpinLoop {
    boolean flag;

    volatile boolean vflag;

    @AssertWarning(type="SpinLoopOnField")
    void waitForTrue() {
        while (flag)
            ;
    }

    @AssertNoWarning(type="SpinLoopOnField")
    void waitForVolatileTrue() {
        while (vflag)
            ;
    }

    TestSpinLoop foo;

    TestSpinLoop bar;

    @AssertWarning(type="SpinLoopOnField")
    void waitForNonNull() {
        while (foo == null)
            ;
    }

    @AssertWarning(type="SpinLoopOnField")
    static void waitForNonNullIndirect(int x, TestSpinLoop baz) {
        while (baz.foo == null)
            ;
    }

    @AssertWarning(type="SpinLoopOnField")
    static void waitForNonNullIndirect2(int x, TestSpinLoop baz) {
        while (baz.foo.bar == null)
            ;
    }

    static boolean sflag;

    @AssertWarning(type="SpinLoopOnField")
    static void waitForStatic() {
        while (!sflag)
            ;
    }
}
