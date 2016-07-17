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
 * @author lan
 *
 */
public class TestUnreachableCatch {
    @AssertNoWarning("*")
    public String testOk(Object x) {
        try {
            return (String)x;
        }
        catch(ClassCastException cce) {
            cce.printStackTrace();
            return null;
        }
    }
    
    @AssertWarning("UnreachableCatch")
    public Object testUnreachable(Object x) {
        try {
            return x;
        }
        catch(ClassCastException cce) {
            cce.printStackTrace();
            return null;
        }
    }

    @AssertWarning("UnreachableCatch")
    public Object testUnreachableMulti(Object x) {
        try {
            return x;
        }
        catch(ClassCastException | NullPointerException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
