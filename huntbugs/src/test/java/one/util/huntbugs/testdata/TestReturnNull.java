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

import java.util.Optional;
import java.util.function.Function;

import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
public class TestReturnNull {
    @AssertWarning(type="OptionalReturnNull")
    Optional<String> testOptional(int a) {
        Optional<String> x = null;
        if(a > 5)
            return Optional.of(String.valueOf(a));
        return x;
    }

    @AssertWarning(type="BooleanReturnNull")
    Boolean testBoolean(int a) {
        if(a > 5)
            return true;
        return null;
    }
    
    @AssertWarning(type="ArrayReturnNull")
    int[] testArray(int a) {
        if(a > 5)
            return new int[] {a};
        return null;
    }
    
    @AssertWarning(type="OptionalReturnNull")
    String testInLambda(String x) {
        Function<String, Optional<String>> s = y -> null;
        return s.apply(x).get();
    }
}
