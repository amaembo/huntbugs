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

import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestIgnoredException {
    @AssertWarning(type="IgnoredException")
    public void test1() {
        try {
            System.out.println("Test");
        }
        catch(Exception ex) {
            
        }
    }

    @AssertWarning(type="IgnoredException")
    public void test2() {
        try {
            System.out.println("Test");
        }
        catch(Throwable t) {
            return;
        }
        System.out.println("Passed");
    }

    @AssertWarning(type="IgnoredException")
    public void test3() {
        for(int i=0; i<10; i++) {
            try {
                System.out.println("Test");
            }
            catch(RuntimeException ex) {
                continue;
            }
        }
    }
}
