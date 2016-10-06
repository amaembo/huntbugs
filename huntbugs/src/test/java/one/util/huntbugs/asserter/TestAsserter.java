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
package one.util.huntbugs.asserter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author Tagir Valeev
 *
 */
@AssertWarning("BBB")
@AssertNoWarning("RoughConstantValue")
public class TestAsserter {
    @AssertWarning("CCC")
    @AssertNoWarning("BadNameOfField")
    int TheField = 123;

    @AssertWarning("BadName*")
    @SuppressMyWarning("BadNameOfField")
    int TheFieldSuppressed = 123;
    
    @AssertWarning("AAA")
    @AssertNoWarning("Rough*")
    public double test() {
        return 3.1415*TheField;
    }
    
    @AssertWarning("ParameterOverwritte*")
    @SuppressFBWarnings("Param*")
    public double testSuppress(int x) {
        x = 10;
        return x*2;
    }
    
    @AssertWarning("ParameterOverwritt*")
    public double testSuppressParam(@SuppressMyWarning("all") int x) {
        x = 10;
        return x*2;
    }
    
    @AssertNoWarning("UncalledPrivateMethod")
    private void uncalled() {
        System.out.println("Uncalled");
    }
    
    public void testLocalClass() {
        class X {
            @AssertNoWarning("ParameterOverwritten")
            public void print(int x) {
                x = 10;
                System.out.println(x);
            }
        }
        new X().print(5);
    }
    
    @interface SuppressMyWarning {
        String value();
    }
}
