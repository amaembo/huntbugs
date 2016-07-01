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

import java.util.Set;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestUnsupportedCall {
    public TestUnsupportedCall() {
        throw Math.random() > 0.5 ? new UnsupportedOperationException() : new UnsupportedOperationException("With message");
    }

    public TestUnsupportedCall(int x) {
        System.out.println(x);
    }
    
    @AssertWarning("UnsupportedCall")
    public void hello(int x) {
        doHello(x+1);
    }

    @AssertNoWarning("*")
    private void doHello(int i) {
        throw new UnsupportedOperationException();
    }
    
    @AssertNoWarning("*")
    public void addAll(Set<String> a, Set<String> b) {
        a.addAll(b);
    }
    
    @AssertWarning("UnsupportedCall")
    public static void test() {
        new TestUnsupportedCall();
    }
    
    @AssertNoWarning("UnsupportedCall")
    public static void testCatch() {
        try {
            new TestUnsupportedCall();
        } catch (UnsupportedOperationException e) {
            e.printStackTrace();
        }
    }
}
