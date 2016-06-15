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
public class TestStringConcatInLoop {
    @AssertWarning(value="StringConcatInLoop", minScore=47)
    public String testStringConcatInLoopSimple(String[] data) {
        String result = "";
        for(String row : data)
            result+=row;
        return result;
    }

    @AssertWarning(value="StringConcatInLoop", maxScore=47)
    public String testStringConcatInLoopIf(String[] data) {
        String result = "";
        for(String row : data)
            if(!row.isEmpty())
                result+=row;
        return result;
    }

    @AssertWarning("StringConcatInLoop")
    public String testStringConcatInLoopNested(String[] data) {
        String result = "";
        for(String row : data) {
            for(int i=0; i<10; i++)
                result+=row;
        }
        return result+ParseException.r;
    }
    
    @AssertWarning("StringConcatInLoop")
    public int testStringConcatInLoopNested2(String[] data) {
        int i=0;
        for(String row : data) {
            String result = "";
            for(int j=0; j<10; j++)
                result+=row;
            i+=result.length();
        }
        return i;
    }
    
    @AssertNoWarning("*")
    public int testStringConcatOk(String[] data) {
        int n = 0;
        for(String row : data) {
            String result = row; 
            result+=row;
            n+=result.length();
        }
        return n;
    }
    
    @AssertNoWarning("*")
    static class ParseException extends Exception {
        public static final String r = initialise(new String[] {"1", "2"});
        
        private static String initialise(String[] tokens) {
            String retval = "";
            for (String t : tokens) {
                retval += t;
            }
            return retval;
        }
    }
}
