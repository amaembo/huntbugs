/*
 * Copyright 2015, 2016 Tagir Valeev
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

import java.io.InputStream;
import java.net.URL;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestUnsafeGetResource {
    @AssertWarning(type="UnsafeGetResource", minScore=50)
    public URL getURL() {
        Class<?> myClass = getClass();
        return myClass.getResource("foo");
    }

    @AssertNoWarning(type="UnsafeGetResource")
    public URL getURLOk() {
        return TestUnsafeGetResource.class.getResource("foo");
    }
    
    @AssertWarning(type="UnsafeGetResource", minScore=50)
    public InputStream openResource() {
        return getClass().getResourceAsStream("foo");
    }

    @AssertWarning(type="UnsafeGetResource", maxScore=35)
    public InputStream openResourceFromRoot() {
        return getClass().getResourceAsStream("/foo");
    }
    
    public static class NoSubClasses {
        @AssertWarning(type="UnsafeGetResource", minScore=40, maxScore=40)
        public URL getURL() {
            return getClass().getResource("foo");
        }
    }
    
    public static final class SubClass extends TestUnsafeGetResource {
        @AssertNoWarning(type="UnsafeGetResource")
        public URL getURL2() {
            return getClass().getResource("foo");
        }
    }
}
