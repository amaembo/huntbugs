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
public class TestFinalizer {
    static class SuperClass {
        @Override
        protected void finalize() throws Throwable {
            System.out.println("Finalize!!!");
        }
    }
    
    static class Nullify extends SuperClass {
        @Override
        @AssertWarning(type="FinalizeNullifiesSuper", minScore=40, maxScore = 60)
        protected void finalize() throws Throwable {
        }
    }
    
    static class Useless extends SuperClass {
        @Override
        @AssertWarning(type="FinalizeUselessSuper", minScore=30, maxScore = 50)
        protected void finalize() throws Throwable {
            super.finalize();
        }
    }
    
    static class Useful extends SuperClass {
        @Override
        @AssertNoWarning(type="FinalizeUselessSuper")
        @AssertWarning(type="FinalizePublic")
        public void finalize() throws Throwable {
            super.finalize();
            System.out.println("More");
        }
    }
    
    @AssertWarning(type="FinalizeInvocation")
    public void test() {
        finalize();
    }

    @Override
    @AssertWarning(type="FinalizeEmpty", minScore=20, maxScore = 40)
    @AssertNoWarning(type="FinalizeNullifiesSuper")
    protected void finalize() {
    }
    
    static class FinalFinalizer {
        @Override
        @AssertNoWarning(type="Finalize*")
        protected final void finalize() {
        }
    }
}
