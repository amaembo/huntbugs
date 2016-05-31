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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
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
        @AssertWarning(value="FinalizeNullifiesSuper", minScore = 40, maxScore = 60)
        protected void finalize() throws Throwable {
        }
    }

    static class Useless extends SuperClass {
        @Override
        @AssertWarning(value="FinalizeUselessSuper", minScore = 30, maxScore = 50)
        protected void finalize() throws Throwable {
            super.finalize();
        }
    }

    static class Useful extends SuperClass {
        @Override
        @AssertNoWarning("FinalizeUselessSuper")
        @AssertWarning("FinalizePublic")
        public void finalize() throws Throwable {
            super.finalize();
            System.out.println("More");
        }
    }

    static class Useful2 extends SuperClass {
        @Override
        @AssertNoWarning("FinalizeNoSuperCall")
        public void finalize() throws Throwable {
            super.finalize();
            System.out.println("More");
        }
    }
    
    @AssertWarning("FinalizeInvocation")
    public void test() {
        finalize();
    }

    @Override
    @AssertWarning(value="FinalizeEmpty", minScore = 20, maxScore = 40)
    @AssertNoWarning("FinalizeNullifiesSuper")
    protected void finalize() {
    }

    static class FinalFinalizer {
        @Override
        @AssertNoWarning("Finalize*")
        protected final void finalize() {
        }
    }

    static class NullFields {
        InputStream is = new ByteArrayInputStream(new byte[1]);

        @AssertWarning("FinalizeNullsFields")
        @Override
        protected void finalize() throws Throwable {
            System.out.println("Finalizer");
            is = null;
        }
    }

    static class NullFieldsOnly {
        InputStream is = new ByteArrayInputStream(new byte[1]);
        Object obj = new Object();

        @AssertWarning("FinalizeOnlyNullsFields")
        @Override
        protected void finalize() throws Throwable {
            is = null;
            obj = null;
        }
    }

    static class NoSuperCall extends SuperClass {
        @AssertWarning("FinalizeNoSuperCall")
        @Override
        protected void finalize() throws Throwable {
            if (Math.random() > 0.5) {
                System.out.println("Mwahaha");
            } else {
                System.out.println("Hohoho");
            }
        }
    }
}
