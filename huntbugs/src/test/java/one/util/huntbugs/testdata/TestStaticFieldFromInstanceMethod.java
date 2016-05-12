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
public class TestStaticFieldFromInstanceMethod {
    private static int val;
    private static boolean VERBOSE;
    static Object data;
    
    @AssertWarning(type="StaticFieldFromInstanceMethod", minScore=55)
    public void testWrite(int x) {
        val = x;
        System.out.println(val);
    }

    @AssertWarning(type="StaticFieldFromInstanceMethod", minScore=35, maxScore=35)
    private void testWritePrivate(int x) {
        val = x;
        System.out.println(val);
    }
    
    @AssertWarning(type="StaticFieldFromInstanceMethod", minScore=45, maxScore=45)
    protected void testWriteProtected(int x) {
        val = x;
        System.out.println(val);
    }
    
    @AssertWarning(type="StaticFieldFromInstanceMethod", minScore=50, maxScore=50)
    public void cleanUp() {
        data = null;
    }
    
    @AssertWarning(type="StaticFieldFromInstanceMethod", minScore=35, maxScore=35)
    public void cleanUpSynchronized() {
        synchronized(TestStaticFieldFromInstanceMethod.class) {
            data = null;
        }
    }

    @AssertWarning(type="StaticFieldFromInstanceMethod", minScore=35, maxScore=35)
    public synchronized void cleanUpSynchronizedMethod() {
        data = null;
    }
    
    @AssertWarning(type="StaticFieldFromInstanceMethod", minScore=45, maxScore=45)
    public void setVerbose(boolean v) {
        VERBOSE = v;
    }
    
    @AssertNoWarning(type="StaticFieldFromInstanceMethod")
    public static void testWriteFromStatic(int x) {
        val = x;
    }
}
