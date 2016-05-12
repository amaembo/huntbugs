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
public class TestUncalledPrivateMethod {
    @interface MyAnno {
    }

    @AssertWarning(type = "UncalledPrivateMethod")
    private void simple() {
        System.out.println("Uncalled");
    }

    @AssertWarning(type = "UncalledPrivateMethod")
    @Deprecated
    private void deprecated() {
        System.out.println("Uncalled");
    }

    @AssertNoWarning(type = "UncalledPrivateMethod")
    @MyAnno
    private void annotated() {
        System.out.println("Uncalled");
    }

    @AssertNoWarning(type = "UncalledPrivateMethod")
    void packagePrivate() {
        System.out.println("Uncalled");
    }

    @AssertNoWarning(type = "UncalledPrivateMethod")
    private void called() {
        System.out.println("Called");
    }

    @AssertNoWarning(type = "UncalledPrivateMethod")
    private void methodRef(String s) {
        System.out.println("Called " + s);
    }

    public void caller() {
        called();
        Stream.of("a").forEach(this::methodRef);
    }
    
    @AssertWarning(type = "UncalledPrivateMethodChain")
    @AssertNoWarning(type = "UncalledPrivateMethod")
    private void callA() {
        callB();
    }
    
    @AssertNoWarning(type = "UncalledPrivateMethod")
    private void callB() {
        callA();
    }
}
