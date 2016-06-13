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

    @AssertWarning("UncalledPrivateMethod")
    private void simple() {
        System.out.println("Uncalled");
    }

    @AssertWarning("UncalledPrivateMethod")
    @Deprecated
    private void deprecated() {
        System.out.println("Uncalled");
    }

    @AssertNoWarning("UncalledPrivateMethod")
    @MyAnno
    private void annotated() {
        System.out.println("Uncalled");
    }

    @AssertNoWarning("UncalledPrivateMethod")
    void packagePrivate() {
        System.out.println("Uncalled");
    }

    @AssertNoWarning("UncalledPrivateMethod")
    private void called() {
        System.out.println("Called");
    }

    @AssertNoWarning("UncalledPrivateMethod")
    private void methodRef(String s) {
        System.out.println("Called " + s);
    }

    @AssertNoWarning("UncalledPrivateMethod")
    private void lambda(String s) {
        System.out.println("Called " + s);
    }
    
    public void caller() {
        called();
        Stream.of("a").forEach(this::methodRef);
        Stream.of("b").forEach(b -> this.lambda(b));
    }
    
    @AssertWarning("UncalledPrivateMethodChain")
    @AssertNoWarning("UncalledPrivateMethod")
    private void callA() {
        callB();
    }
    
    @AssertNoWarning("UncalledPrivateMethod")
    private void callB() {
        callA();
    }
    
    @AssertWarning("UncalledPrivateMethodChain")
    @AssertNoWarning("UncalledPrivateMethod")
    private void selfLambda() {
        Runnable r = () -> selfLambda();
        r.run();
        this.r.run();
    }
    
    Runnable r = new Runnable() {
        @AssertWarning("UncalledMethodOfAnonymousClass")
        public void test() {
            System.out.println("test");
        }

        @AssertNoWarning("*")
        public void test2() {
            System.out.println("test2");
        }
        
        @AssertNoWarning("*")
        public void test3() {
            System.out.println("test3");
        }
        
        @Override
        public void run() {
            System.out.println("run");
            test2();
            new Runnable() {
                @Override
                public void run() {
                    test3();
                }
            }.run();
        }
    };
}
