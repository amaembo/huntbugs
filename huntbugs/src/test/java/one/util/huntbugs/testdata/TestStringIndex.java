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
public class TestStringIndex {
    private static final String TEST_STRING = " test string ";

    @AssertWarning(type="StringIndexIsGreaterThanAllowed")
    public String trimSubstring() {
        return TEST_STRING.trim().substring(TEST_STRING.length());
    }
    
    @AssertWarning(type="UselessStringSubstring")
    public String uselessSubstring() {
        return TEST_STRING.trim().substring(0);
    }
    
    @AssertWarning(type="StringIndexIsLessThanZero")
    public String lessThanZero() {
        return TEST_STRING.substring(-1);
    }

    @AssertWarning(type="StringIndexIsLessThanZero")
    public String lessThanZero2() {
        return TEST_STRING.substring(1, -1);
    }
    
    @AssertNoWarning(type="*")
    public String substringCorrect() {
        return TEST_STRING.substring(TEST_STRING.length())
                + TEST_STRING.substring(TEST_STRING.length()-1)
                +  TEST_STRING.substring(1, TEST_STRING.length())
                +  TEST_STRING.substring(1, 2);
    }
    
    @AssertWarning(type="StringIndexIsGreaterThanAllowed")
    public String substringIncorrect() {
        return TEST_STRING.substring(TEST_STRING.length()+1);
    }
    @AssertWarning(type="StringIndexIsGreaterThanAllowed")
    public String substringIncorrect2() {
        return TEST_STRING.substring(1, TEST_STRING.length()+1);
    }
    
    @AssertWarning(type="StringIndexIsGreaterThanAllowed")
    public String substringIncorrect3() {
        return TEST_STRING.substring(2, 1);
    }
    @AssertNoWarning(type="*")
    public char charAtCorrect() {
        return TEST_STRING.charAt(TEST_STRING.length()-1);
    }
    
    @AssertWarning(type="StringIndexIsGreaterThanAllowed")
    public char charAtIncorrect() {
        return TEST_STRING.charAt(TEST_STRING.length());
    }
}
