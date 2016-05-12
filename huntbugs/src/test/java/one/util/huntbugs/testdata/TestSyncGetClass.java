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
public class TestSyncGetClass {
    private static int val;
    
    @AssertWarning(type="SyncOnGetClass", minScore=50)
    public void update(int x) {
        synchronized (getClass()) {
            val = x;
            System.out.println(val);
        }
    }

    @AssertNoWarning(type="*")
    public static void updateStatic(TestSyncGetClass obj, int x) {
        synchronized (obj.getClass()) {
            val = x;
            System.out.println(val);
        }
    }

    @AssertNoWarning(type="SyncOnGetClass")
    @AssertWarning(type="StaticFieldFromInstanceMethod")
    public void update(Object obj, int x) {
        synchronized (obj.getClass()) {
            val = x;
            System.out.println(val);
        }
    }

    @AssertWarning(type="SyncOnGetClass", minScore=40, maxScore=49)
    public void print(int x) {
        synchronized (getClass()) {
            System.out.println(x);
        }
    }
}
