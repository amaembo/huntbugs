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

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestNaming {
    @AssertNoWarning(type="BadNameOfMethod")
    public void AB() {
        System.out.println();
    }

    @AssertWarning(type="BadNameOfMethod", minScore=38)
    public void Test() {
        System.out.println();
    }
    
    @AssertWarning(type="BadNameOfMethod", minScore=35, maxScore=38)
    protected void Test2() {
        System.out.println();
    }
    
    @AssertWarning(type="BadNameOfMethod", minScore=32, maxScore=35)
    void Test3() {
        System.out.println();
    }
    
    public static class Class1 {
        @AssertWarning(type="BadNameOfMethod", minScore=25, maxScore=30)
        private void Test4() {
            System.out.println();
        }
    }
    
    static class Class2 {
        @AssertWarning(type="BadNameOfMethod", minScore=15, maxScore=20)
        private void Test5() {
            System.out.println();
        }
    }
}
